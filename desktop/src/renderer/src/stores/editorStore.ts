import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { fileNameFromPath, languageFromPath } from '@renderer/utils/editorLanguage'
import { editorPathsEqual, normalizeEditorPath } from '@renderer/utils/editorPath'
import { viewKindFromPath, type TabViewKind } from '@renderer/utils/fileKind'
import type { TextLoadPolicy } from '@renderer/utils/fileSizeLimits'
import { useWorkspaceStore } from './workspaceStore'
import { useExplorerPromptStore } from './explorerPromptStore'
import { useGitStore } from './gitStore'
import { useToastStore } from './toastStore'

export interface EditorTab {
  path: string
  name: string
  viewKind: TabViewKind
  language: string
  content: string
  savedContent: string
  dirty: boolean
  loading: boolean
  largeFile?: boolean
  /** Agent wrote to disk while tab had unsaved edits — disk content differs from buffer. */
  diskAhead?: boolean
  mediaDataUrl?: string
  fileSize?: number
  mimeType?: string
  textLoadPolicy?: TextLoadPolicy
  /** HEAD / index content for Git side-by-side diff view. */
  gitDiffOriginal?: string
}

export const useEditorStore = defineStore('editor', () => {
  const tabs = ref<EditorTab[]>([])
  const activePath = ref<string | null>(null)
  const error = ref<string | null>(null)
  const loadingTasks = new Map<string, Promise<void>>()

  const activeTab = computed(
    () => tabs.value.find(t => activePath.value != null && editorPathsEqual(t.path, activePath.value)) ?? null,
  )

  function findTab(path: string): EditorTab | undefined {
    return tabs.value.find(t => editorPathsEqual(t.path, path))
  }

  function resolveTabPath(path: string): string {
    return findTab(path)?.path ?? normalizeEditorPath(path)
  }

  function patchTab(path: string, patch: Partial<EditorTab>): void {
    const tab = findTab(path)
    if (!tab) return
    tabs.value = tabs.value.map(item => (item.path === tab.path ? { ...item, ...patch } : item))
  }

  function createTabSkeleton(filePath: string): EditorTab {
    return {
      path: filePath,
      name: fileNameFromPath(filePath),
      viewKind: viewKindFromPath(filePath),
      language: languageFromPath(filePath),
      content: '',
      savedContent: '',
      dirty: false,
      loading: true,
    }
  }

  function ensureTab(filePath: string): void {
    if (findTab(filePath)) return
    tabs.value = [...tabs.value, createTabSkeleton(normalizeEditorPath(filePath))]
  }

  function showLargeFilePrompt(
    filePath: string,
    fileSize: number,
    textLoadPolicy: TextLoadPolicy,
  ): void {
    patchTab(filePath, {
      viewKind: 'large-prompt',
      fileSize,
      textLoadPolicy,
      loading: false,
      content: '',
      savedContent: '',
      dirty: false,
    })
  }

  async function loadTextFile(filePath: string, force = false): Promise<void> {
    const info = await window.workspaceApi.getFileInfo(filePath)
    const policy = info.textLoadPolicy ?? 'normal'

    patchTab(filePath, { fileSize: info.size, textLoadPolicy: policy })

    if (policy === 'blocked') {
      showLargeFilePrompt(filePath, info.size, policy)
      return
    }

    if (policy === 'confirm-required' && !force) {
      showLargeFilePrompt(filePath, info.size, policy)
      return
    }

    const content = await window.workspaceApi.readFile(filePath)
    if (content == null) {
      throw new Error('File not found')
    }
    patchTab(filePath, {
      viewKind: 'text',
      content,
      savedContent: content,
      loading: false,
      largeFile: policy === 'large' || policy === 'confirm-required',
    })
  }

  async function loadImageFile(filePath: string): Promise<void> {
    try {
      const { dataUrl, size, mimeType } = await window.workspaceApi.readFileDataUrl(filePath)
      patchTab(filePath, {
        viewKind: 'image',
        mediaDataUrl: dataUrl,
        fileSize: size,
        mimeType,
        content: '',
        savedContent: '',
        dirty: false,
        loading: false,
      })
    } catch (e) {
      const message = e instanceof Error ? e.message : String(e)
      if (message.includes('preview limit') || message.includes('IMAGE_TOO_LARGE')) {
        const info = await window.workspaceApi.getFileInfo(filePath)
        showLargeFilePrompt(filePath, info.size, 'blocked')
        return
      }
      throw e
    }
  }

  async function loadBinaryFile(filePath: string): Promise<void> {
    const info = await window.workspaceApi.getFileInfo(filePath)
    patchTab(filePath, {
      viewKind: 'binary',
      fileSize: info.size,
      mimeType: info.mimeType,
      content: '',
      savedContent: '',
      dirty: false,
      loading: false,
    })
  }

  async function loadFileContent(filePath: string, force = false): Promise<void> {
    ensureTab(filePath)
    patchTab(filePath, { loading: true, viewKind: viewKindFromPath(filePath) })

    try {
      const kind = viewKindFromPath(filePath)
      if (kind === 'image') {
        await loadImageFile(filePath)
      } else if (kind === 'binary') {
        await loadBinaryFile(filePath)
      } else {
        await loadTextFile(filePath, force)
      }
    } catch (e) {
      error.value = e instanceof Error ? e.message : String(e)
      const info = await window.workspaceApi.getFileInfo(filePath).catch(() => null)
      if (info?.viewKind === 'text' && info.textLoadPolicy) {
        showLargeFilePrompt(filePath, info.size, info.textLoadPolicy)
      } else {
        tabs.value = tabs.value.filter(t => t.path !== filePath)
        if (activePath.value === filePath) {
          activePath.value = tabs.value.at(-1)?.path ?? null
        }
      }
    }
  }

  async function openFile(
    filePath: string,
    options?: { force?: boolean; reveal?: boolean },
  ): Promise<void> {
    const path = resolveTabPath(filePath)
    const shouldReveal = options?.reveal !== false
    error.value = null

    const kind = await window.workspaceApi.getPathKind(path).catch(() => 'missing' as const)
    if (kind === 'directory') {
      if (shouldReveal) void useWorkspaceStore().revealInTree(path)
      return
    }
    if (kind === 'missing') {
      error.value = '路径不存在或无法访问'
      return
    }

    activePath.value = path

    const pending = loadingTasks.get(path)
    if (pending) {
      await pending
      if (shouldReveal) void useWorkspaceStore().revealInTree(path)
      return
    }

    const existing = findTab(path)
    if (existing && !existing.loading) {
      if (existing.viewKind === 'large-prompt' && options?.force) {
        // User confirmed — fall through to reload.
      } else if (existing.viewKind === 'git-diff') {
        // Explorer open or force reload → switch from Git diff back to normal editor.
      } else if (existing.viewKind !== 'large-prompt' && !options?.force) {
        if (shouldReveal) void useWorkspaceStore().revealInTree(path)
        return
      }
    }

    const task = loadFileContent(path, options?.force ?? false)
    loadingTasks.set(path, task)

    try {
      await task
    } finally {
      loadingTasks.delete(path)
    }

    if (shouldReveal) void useWorkspaceStore().revealInTree(path)
  }

  async function openGitDiff(filePath: string): Promise<void> {
    error.value = null
    const workspaceRoot = useWorkspaceStore().rootPath
    if (!workspaceRoot) return

    const pathKind = await window.workspaceApi.getPathKind(filePath).catch(() => 'missing' as const)
    if (pathKind !== 'file') {
      if (pathKind === 'directory') void useWorkspaceStore().revealInTree(filePath)
      return
    }

    activePath.value = filePath
    ensureTab(filePath)
    patchTab(filePath, {
      loading: true,
      viewKind: 'git-diff',
      gitDiffOriginal: '',
    })

    try {
      const info = await window.workspaceApi.getFileInfo(filePath)
      if (info.viewKind !== 'text') {
        error.value = '该文件类型暂不支持 Git 并排对比'
        await loadFileContent(filePath)
        return
      }

      const [baseline, modified] = await Promise.all([
        window.workspaceApi.gitFileBaseline(workspaceRoot, filePath),
        window.workspaceApi.readFile(filePath).then(c => c ?? ''),
      ])

      patchTab(filePath, {
        viewKind: 'git-diff',
        language: languageFromPath(filePath),
        content: modified,
        savedContent: modified,
        gitDiffOriginal: baseline.baseline ?? '',
        loading: false,
        dirty: false,
        name: fileNameFromPath(filePath),
      })
    } catch (e) {
      error.value = e instanceof Error ? e.message : String(e)
      patchTab(filePath, { loading: false })
    }
  }

  async function refreshAfterGitChange(filePaths: string[]): Promise<void> {
    const gitStore = useGitStore()
    for (const filePath of filePaths) {
      const tab = findTab(filePath)
      if (!tab) continue

      const stillChanged = gitStore.statusForPath(filePath) != null
      if (stillChanged) {
        await openGitDiff(filePath)
      } else {
        await loadFileContent(filePath, true)
      }
    }
  }

  function revealActiveInTree(): void {
    if (activePath.value) {
      void useWorkspaceStore().revealInTree(activePath.value)
    }
  }

  async function confirmOpenLargeFile(filePath: string): Promise<void> {
    await openFile(filePath, { force: true })
  }

  function setContent(path: string, content: string): void {
    const tab = findTab(path)
    if (!tab || (tab.viewKind !== 'text' && tab.viewKind !== 'git-diff')) return
    patchTab(path, { content, dirty: content !== tab.savedContent })
  }

  async function trackEditorSave(tab: EditorTab, previousContent: string): Promise<void> {
    const workspaceRoot = useWorkspaceStore().rootPath
    if (!workspaceRoot) return

    void window.desktopApi.recordFileChange({
      absolutePath: tab.path,
      workspaceRoot,
      content: tab.content,
      previousContent,
      changeKind: previousContent.length === 0 ? 'created' : 'modified',
      source: 'tab',
      recordHashes: false,
    })
  }

  async function saveActive(): Promise<boolean> {
    const tab = activeTab.value
    if (!tab) return true
    if (tab.viewKind !== 'text' && tab.viewKind !== 'git-diff') return false
    if (!tab.dirty) return true

    error.value = null
    const previousContent = tab.savedContent
    try {
      await window.workspaceApi.writeFile(tab.path, tab.content)
      patchTab(tab.path, { savedContent: tab.content, dirty: false, diskAhead: false })
      await trackEditorSave(tab, previousContent)
      void useGitStore().scheduleRefresh('changes')
      return true
    } catch (e) {
      error.value = e instanceof Error ? e.message : String(e)
      return false
    }
  }

  async function openActiveInSystem(): Promise<void> {
    const tab = activeTab.value
    if (!tab) return

    error.value = null
    try {
      await window.workspaceApi.openInSystem(tab.path)
    } catch (e) {
      error.value = e instanceof Error ? e.message : String(e)
    }
  }

  async function saveFile(path: string): Promise<boolean> {
    const prev = activePath.value
    activePath.value = path
    const ok = await saveActive()
    if (prev !== path) activePath.value = prev
    return ok
  }

  async function closeTab(path: string, force = false): Promise<boolean> {
    const tab = findTab(path)
    if (!tab) return false

    if (tab.dirty && !force) {
      const discard = await useExplorerPromptStore().confirm({
        title: '未保存的更改',
        message: `「${tab.name}」有未保存的更改，是否放弃并关闭？`,
        confirmLabel: '放弃并关闭',
      })
      if (!discard) return false
    }

    tabs.value = tabs.value.filter(t => t.path !== path)
    if (activePath.value === path) {
      activePath.value = tabs.value.at(-1)?.path ?? null
    }
    return true
  }

  async function closeTabs(paths: string[]): Promise<boolean> {
    for (const path of paths) {
      if (!(await closeTab(path))) return false
    }
    return true
  }

  async function closeOtherTabs(path: string): Promise<boolean> {
    const targets = tabs.value.filter(tab => tab.path !== path).map(tab => tab.path)
    return closeTabs(targets)
  }

  async function closeTabsToLeft(path: string): Promise<boolean> {
    const index = tabs.value.findIndex(tab => tab.path === path)
    if (index <= 0) return true
    return closeTabs(tabs.value.slice(0, index).map(tab => tab.path))
  }

  async function closeTabsToRight(path: string): Promise<boolean> {
    const index = tabs.value.findIndex(tab => tab.path === path)
    if (index < 0 || index >= tabs.value.length - 1) return true
    return closeTabs(tabs.value.slice(index + 1).map(tab => tab.path))
  }

  async function closeAllTabs(): Promise<boolean> {
    return closeTabs(tabs.value.map(tab => tab.path))
  }

  function activateTab(path: string): void {
    const tab = findTab(path)
    if (tab) activePath.value = tab.path
  }

  /** Only unsaved dirty tabs — avoids huge payloads (e.g. open pom.xml) blocking chat.send. */
  function getOpenDocumentSnapshots(): { path: string; content: string; dirty: boolean }[] {
    const maxChars = 512 * 1024
    return tabs.value
      .filter(tab => tab.viewKind === 'text' && !tab.loading && tab.dirty)
      .map(tab => ({
        path: tab.path,
        content: tab.content.length > maxChars ? tab.content.slice(0, maxChars) : tab.content,
        dirty: true,
      }))
  }

  /**
   * Agent wrote to disk (file_changed) — reload editor buffer from filesystem.
   * Claude Code: disk is source of truth; no in-memory injection.
   */
  async function applyAgentFileChange(
    filePath: string,
    options?: { deleted?: boolean },
  ): Promise<void> {
    error.value = null
    if (options?.deleted) {
      await closeTab(filePath, true)
      return
    }

    const tab = findTab(filePath)
    const toast = useToastStore()

    if (tab && tab.viewKind === 'text' && !tab.loading) {
      if (tab.dirty) {
        try {
          const disk = await window.workspaceApi.readFile(filePath)
          if (disk != null && disk !== tab.content) {
            patchTab(filePath, { savedContent: disk, diskAhead: true })
            toast.show(
              `Agent 已在磁盘更新 ${fileNameFromPath(filePath)}，与未保存编辑冲突`,
              6000,
            )
          }
        } catch (e) {
          error.value = e instanceof Error ? e.message : String(e)
        }
      } else {
        await loadFileContent(filePath, true)
      }
      activePath.value = filePath
      return
    }

    await openFile(filePath, { reveal: false })
  }

  /** diskAhead conflict: replace buffer with Agent/disk version. */
  function acceptDiskVersion(path: string): void {
    const tab = findTab(path)
    if (!tab?.diskAhead) return
    patchTab(path, {
      content: tab.savedContent,
      dirty: false,
      diskAhead: false,
    })
  }

  /** diskAhead conflict: keep unsaved buffer; next save overwrites disk. */
  function keepBufferOverDisk(path: string): void {
    const tab = findTab(path)
    if (!tab?.diskAhead) return
    patchTab(path, { diskAhead: false })
  }

  function applyPathRename(oldPath: string, newPath: string): void {
    const oldNorm = oldPath.replace(/\\/g, '/')
    const newNorm = newPath.replace(/\\/g, '/')
    const oldLower = oldNorm.toLowerCase()
    const prefix = `${oldNorm}/`

    tabs.value = tabs.value.map(tab => {
      const tabNorm = tab.path.replace(/\\/g, '/')
      const tabLower = tabNorm.toLowerCase()

      if (tabLower === oldLower || tabLower.startsWith(prefix.toLowerCase())) {
        const updatedPath =
          tabLower === oldLower ? newPath : newNorm + tabNorm.slice(oldNorm.length)
        return {
          ...tab,
          path: updatedPath,
          name: fileNameFromPath(updatedPath),
          language: languageFromPath(updatedPath),
          viewKind: viewKindFromPath(updatedPath),
        }
      }

      return tab
    })

    if (activePath.value) {
      const activeNorm = activePath.value.replace(/\\/g, '/')
      const activeLower = activeNorm.toLowerCase()
      if (activeLower === oldLower || activeLower.startsWith(prefix.toLowerCase())) {
        activePath.value =
          activeLower === oldLower ? newPath : newNorm + activeNorm.slice(oldNorm.length)
      }
    }
  }

  return {
    tabs,
    activePath,
    activeTab,
    error,
    openFile,
    openGitDiff,
    refreshAfterGitChange,
    confirmOpenLargeFile,
    setContent,
    saveActive,
    saveFile,
    closeTab,
    closeOtherTabs,
    closeTabsToLeft,
    closeTabsToRight,
    closeAllTabs,
    activateTab,
    openActiveInSystem,
    revealActiveInTree,
    getOpenDocumentSnapshots,
    applyAgentFileChange,
    acceptDiskVersion,
    keepBufferOverDisk,
    applyPathRename,
  }
})
