import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { useExplorerPromptStore } from './explorerPromptStore'
import { useEditorStore } from './editorStore'
import { useGitStore } from './gitStore'
import { useSessionChangesStore } from './sessionChangesStore'
import { useToastStore } from './toastStore'
import { fileNameFromPath } from '@renderer/utils/editorLanguage'
import { formatWorkspaceError } from '@renderer/utils/workspaceErrors'
import {
  clearLastWorkspacePath,
  getLastWorkspacePath,
  isEphemeralWorkspacePath,
  listRecentWorkspaces,
  rememberWorkspace,
} from '@renderer/utils/workspacePersistence'

export interface DirectoryEntry {
  name: string
  path: string
  kind: 'file' | 'directory'
}

export interface TreeNode extends DirectoryEntry {
  children?: TreeNode[]
  expanded?: boolean
  loading?: boolean
}

export type ExplorerContextMenu =
  | { x: number; y: number; kind: 'file'; path: string; name: string }
  | { x: number; y: number; kind: 'directory'; path: string; name: string; targetDir: string }
  | { x: number; y: number; kind: 'root'; targetDir: string }

export type WindowBootMode = 'restore' | 'blank' | 'workspace'

export interface WindowBootConfig {
  mode: WindowBootMode
  workspacePath?: string
}

export const useWorkspaceStore = defineStore('workspace', () => {
  const rootPath = ref<string | null>(null)
  const tree = ref<TreeNode[]>([])
  const selectedPath = ref<string | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  /** Bumped after revealInTree so the target node scrolls into view. */
  const revealTick = ref(0)
  const revealTargetPath = ref<string | null>(null)
  const rootExpanded = ref(true)
  const contextMenu = ref<ExplorerContextMenu | null>(null)

  const selectedEntry = computed(() => {
    if (!selectedPath.value) return null
    return findNode(tree.value, selectedPath.value)
  })

  function normalizePath(p: string): string {
    return p.replace(/\\/g, '/')
  }

  function pathsEqual(a: string, b: string): boolean {
    return normalizePath(a).toLowerCase() === normalizePath(b).toLowerCase()
  }

  function findNode(nodes: TreeNode[], path: string): TreeNode | null {
    for (const node of nodes) {
      if (pathsEqual(node.path, path)) return node
      if (node.children) {
        const found = findNode(node.children, path)
        if (found) return found
      }
    }
    return null
  }

  async function loadChildren(node: TreeNode): Promise<void> {
    if (node.children) {
      node.expanded = true
      return
    }
    node.loading = true
    try {
      node.children = (await window.workspaceApi.readDirectory(node.path)).map(entry => ({
        ...entry,
        expanded: false,
      }))
      node.expanded = true
      useGitStore().queueIgnoreScan(node.children.map(child => child.path))
    } catch (err) {
      error.value = err instanceof Error ? err.message : String(err)
    } finally {
      node.loading = false
    }
  }

  /** Expand ancestors and select — Cursor-style reveal in explorer. */
  async function revealInTree(targetPath: string): Promise<void> {
    if (!rootPath.value) return
    const root = normalizePath(rootPath.value)
    const target = normalizePath(targetPath)
    if (!target.toLowerCase().startsWith(root.toLowerCase())) return

    const relative = target.slice(root.length).replace(/^\/+/, '')
    if (!relative) return

    const segments = relative.split('/')
    let level = tree.value
    let found: TreeNode | null = null

    for (let i = 0; i < segments.length; i++) {
      const seg = segments[i]!
      const node = level.find(n => n.name.toLowerCase() === seg.toLowerCase())
      if (!node) break
      found = node
      if (i < segments.length - 1) {
        if (node.kind !== 'directory') break
        if (!node.children) {
          await loadChildren(node)
        } else {
          node.expanded = true
        }
        level = node.children ?? []
      }
    }

    if (found) {
      rootExpanded.value = true
      selectedPath.value = targetPath
      revealTargetPath.value = targetPath
      revealTick.value++
    }
  }

  function toggleRootExpanded() {
    rootExpanded.value = !rootExpanded.value
  }

  function parentDirectory(filePath: string): string {
    const sep = filePath.includes('\\') ? '\\' : '/'
    const idx = filePath.lastIndexOf(sep)
    return idx > 0 ? filePath.slice(0, idx) : filePath
  }

  function joinPath(dir: string, name: string): string {
    const sep = dir.includes('\\') ? '\\' : '/'
    const trimmed = dir.replace(/[\\/]+$/, '')
    return `${trimmed}${sep}${name}`
  }

  function getCreationDirectory(): string | null {
    if (!rootPath.value) return null
    if (!selectedPath.value) return rootPath.value

    const node = findNode(tree.value, selectedPath.value)
    if (node?.kind === 'directory') return node.path
    return parentDirectory(selectedPath.value)
  }

  function resolveTargetDirectory(targetDir?: string): string | null {
    if (targetDir) return targetDir
    return getCreationDirectory()
  }

  function remapPath(oldPath: string, newPath: string, targetPath: string): string {
    const oldNorm = normalizePath(oldPath)
    const newNorm = normalizePath(newPath)
    const targetNorm = normalizePath(targetPath)
    const oldLower = oldNorm.toLowerCase()
    const targetLower = targetNorm.toLowerCase()

    if (targetLower === oldLower) return newPath

    const prefix = `${oldNorm}/`
    if (targetLower.startsWith(prefix.toLowerCase())) {
      return newNorm + targetNorm.slice(oldNorm.length)
    }

    return targetPath
  }

  function openContextMenu(event: MouseEvent, path: string, kind: 'file' | 'directory'): void {
    event.preventDefault()
    if (!rootPath.value) return

    selectedPath.value = path
    if (kind === 'file') {
      contextMenu.value = {
        x: event.clientX,
        y: event.clientY,
        kind: 'file',
        path,
        name: fileNameFromPath(path),
      }
      return
    }

    contextMenu.value = {
      x: event.clientX,
      y: event.clientY,
      kind: 'directory',
      path,
      name: fileNameFromPath(path),
      targetDir: path,
    }
  }

  function openRootContextMenu(event: MouseEvent): void {
    event.preventDefault()
    if (!rootPath.value) return

    contextMenu.value = {
      x: event.clientX,
      y: event.clientY,
      kind: 'root',
      targetDir: rootPath.value,
    }
  }

  function closeContextMenu(): void {
    contextMenu.value = null
  }

  function collectExpandedPaths(nodes: TreeNode[], out: string[] = []): string[] {
    for (const node of nodes) {
      if (node.expanded) out.push(node.path)
      if (node.children) collectExpandedPaths(node.children, out)
    }
    return out
  }

  async function loadLevel(dirPath: string): Promise<TreeNode[]> {
    return (await window.workspaceApi.readDirectory(dirPath)).map(entry => ({
      ...entry,
      expanded: false,
    }))
  }

  async function refreshTree(): Promise<void> {
    if (!rootPath.value) return

    loading.value = true
    error.value = null
    const expandedPaths = collectExpandedPaths(tree.value)
    const selected = selectedPath.value

    try {
      tree.value = await loadLevel(rootPath.value)
      rootExpanded.value = true

      for (const path of expandedPaths) {
        const node = findNode(tree.value, path)
        if (node?.kind === 'directory') {
          await loadChildren(node)
        }
      }

      if (selected) {
        selectedPath.value = selected
      }

      void useGitStore().scheduleRefresh('changes')
    } catch (err) {
      error.value = err instanceof Error ? err.message : String(err)
    } finally {
      loading.value = false
    }
  }

  let agentExplorerRefreshTimer: ReturnType<typeof setTimeout> | null = null

  /** CC parity: tool already wrote disk — rescan explorer, then reveal path. */
  async function syncAfterAgentDiskWrite(filePath: string): Promise<void> {
    await refreshTree()
    await revealInTree(filePath)
  }

  /** End-of-turn rescan (e.g. terminal-created files). */
  function scheduleExplorerRescan(): void {
    if (agentExplorerRefreshTimer) clearTimeout(agentExplorerRefreshTimer)
    agentExplorerRefreshTimer = setTimeout(() => {
      agentExplorerRefreshTimer = null
      void refreshTree()
    }, 200)
  }

  async function createFile(targetDir?: string): Promise<string | null> {
    const dir = resolveTargetDirectory(targetDir)
    if (!dir) return null

    const promptStore = useExplorerPromptStore()
    const name = await promptStore.ask({
      title: '新建文件',
      defaultValue: 'untitled.txt',
      placeholder: '文件名',
    })
    if (!name) return null

    loading.value = true
    error.value = null
    try {
      const filePath = await window.workspaceApi.createFile(joinPath(dir, name.trim()))
      await refreshTree()
      await revealInTree(filePath)
      return filePath
    } catch (err) {
      useToastStore().show(formatWorkspaceError(err))
      return null
    } finally {
      loading.value = false
    }
  }

  async function createFolder(targetDir?: string): Promise<string | null> {
    const dir = resolveTargetDirectory(targetDir)
    if (!dir) return null

    const promptStore = useExplorerPromptStore()
    const name = await promptStore.ask({
      title: '新建文件夹',
      defaultValue: 'new-folder',
      placeholder: '文件夹名',
    })
    if (!name) return null

    loading.value = true
    error.value = null
    try {
      const folderPath = await window.workspaceApi.createDirectory(joinPath(dir, name.trim()))
      await refreshTree()
      await revealInTree(folderPath)
      return folderPath
    } catch (err) {
      useToastStore().show(formatWorkspaceError(err))
      return null
    } finally {
      loading.value = false
    }
  }

  async function renameEntry(path: string, kind: 'file' | 'directory'): Promise<string | null> {
    const currentName = fileNameFromPath(path)
    const promptStore = useExplorerPromptStore()
    const newName = await promptStore.ask({
      title: kind === 'file' ? '重命名文件' : '重命名文件夹',
      defaultValue: currentName,
      placeholder: '新名称',
    })
    if (!newName || newName.trim() === currentName) return null

    const trimmed = newName.trim()
    if (/[\\/]/.test(trimmed)) {
      useToastStore().show('名称不能包含路径分隔符')
      return null
    }

    const parent = parentDirectory(path)
    const newPath = joinPath(parent, trimmed)

    loading.value = true
    error.value = null
    try {
      await window.workspaceApi.rename(path, newPath)
      useEditorStore().applyPathRename(path, newPath)
      useSessionChangesStore().applyPathRename(path, newPath)

      if (selectedPath.value) {
        selectedPath.value = remapPath(path, newPath, selectedPath.value)
      }

      await refreshTree()
      await revealInTree(newPath)
      return newPath
    } catch (err) {
      useToastStore().show(formatWorkspaceError(err))
      return null
    } finally {
      loading.value = false
    }
  }

  function pathIsUnder(parent: string, child: string): boolean {
    const parentNorm = normalizePath(parent).replace(/\/+$/, '').toLowerCase()
    const childNorm = normalizePath(child).toLowerCase()
    return childNorm === parentNorm || childNorm.startsWith(`${parentNorm}/`)
  }

  async function deleteEntry(path: string, kind: 'file' | 'directory'): Promise<boolean> {
    if (rootPath.value && pathsEqual(path, rootPath.value)) {
      useToastStore().show('不能删除工作区根目录')
      return false
    }

    const name = fileNameFromPath(path)
    const promptStore = useExplorerPromptStore()
    const confirmed = await promptStore.confirm({
      title: kind === 'file' ? '删除文件' : '删除文件夹',
      message:
        kind === 'file'
          ? `确定删除「${name}」？此操作不可撤销。`
          : `确定删除文件夹「${name}」及其全部内容？此操作不可撤销。`,
      confirmLabel: '删除',
    })
    if (!confirmed) return false

    loading.value = true
    error.value = null
    try {
      const editor = useEditorStore()
      if (kind === 'file') {
        await window.workspaceApi.deleteFile(path)
        await editor.closeTab(path, true)
      } else {
        for (const tab of [...editor.tabs]) {
          if (pathIsUnder(path, tab.path)) {
            await editor.closeTab(tab.path, true)
          }
        }
        await window.workspaceApi.deleteDirectory(path)
      }

      if (selectedPath.value && (pathsEqual(selectedPath.value, path) || pathIsUnder(path, selectedPath.value))) {
        selectedPath.value = null
      }

      await refreshTree()
      void useGitStore().scheduleRefresh('changes')
      return true
    } catch (err) {
      useToastStore().show(formatWorkspaceError(err))
      return false
    } finally {
      loading.value = false
    }
  }

  async function openWorkspaceAt(
    path: string,
    options?: { persist?: boolean },
  ): Promise<boolean> {
    const nextPath = path.trim()
    if (!nextPath) return false

    const currentKey = rootPath.value ? normalizePath(rootPath.value).toLowerCase() : null
    const nextKey = normalizePath(nextPath).toLowerCase()
    if (currentKey === nextKey) return true

    if (!(await useEditorStore().closeAllTabs())) {
      return false
    }

    loading.value = true
    error.value = null
    try {
      await window.desktopApi.ensureWorkspaceReady(nextPath)
      tree.value = await loadLevel(nextPath)
      rootPath.value = nextPath
      selectedPath.value = null
      rootExpanded.value = true
      closeContextMenu()
      await window.desktopApi.syncWindowWorkspace(nextPath)
      if (options?.persist !== false) {
        await rememberWorkspace(nextPath)
      }
      void useGitStore().scheduleRefresh('changes')
      if (import.meta.env.DEV) {
        const devContext = await window.desktopApi.getDevContext(nextPath)
        if (devContext.isSelfWorkspace) {
          useToastStore().show(
            '当前工作区包含 Agent Desktop 源码。Review/保存会触发热更新，终端与编辑器已做防护，但仍建议避免在 dev 进程内对 desktop/src 批量写入。',
          )
        }
      }
      return true
    } catch (err) {
      error.value = err instanceof Error ? err.message : String(err)
      return false
    } finally {
      loading.value = false
    }
  }

  async function restoreLastWorkspace(): Promise<boolean> {
    const candidates = [
      await getLastWorkspacePath(),
      ...(await listRecentWorkspaces()).map(item => item.path),
    ].filter((path, index, all): path is string => {
      if (!path?.trim()) return false
      if (isEphemeralWorkspacePath(path)) return false
      const key = path.replace(/\\/g, '/').replace(/\/+$/, '').toLowerCase()
      return all.findIndex(p => p?.replace(/\\/g, '/').replace(/\/+$/, '').toLowerCase() === key) === index
    })

    for (const path of candidates) {
      const ok = await openWorkspaceAt(path)
      if (ok) return true
    }

    await clearLastWorkspacePath()
    return false
  }

  /** Single entry point for per-window startup — do not restore workspace elsewhere. */
  async function initializeWindowBoot(boot: WindowBootConfig): Promise<void> {
    if (boot.mode === 'workspace' && boot.workspacePath?.trim()) {
      await openWorkspaceAt(boot.workspacePath)
      return
    }

    if (boot.mode === 'restore') {
      await restoreLastWorkspace()
      return
    }

    rootPath.value = null
    tree.value = []
    selectedPath.value = null
    error.value = null
    closeContextMenu()
    await window.desktopApi.syncWindowWorkspace(null)
  }

  async function openFolder() {
    loading.value = true
    error.value = null
    try {
      const path = await window.workspaceApi.openDirectory()
      if (!path) return
      await openWorkspaceAt(path)
    } catch (err) {
      error.value = err instanceof Error ? err.message : String(err)
    } finally {
      loading.value = false
    }
  }

  async function toggleExpand(node: TreeNode) {
    if (node.kind !== 'directory') return

    if (node.expanded) {
      node.expanded = false
      return
    }

    await loadChildren(node)
  }

  function selectPath(path: string, kind: 'file' | 'directory') {
    selectedPath.value = path
    if (kind === 'directory') {
      void toggleExpand(findNode(tree.value, path)!)
    }
  }

  return {
    rootPath,
    tree,
    selectedPath,
    selectedEntry,
    loading,
    error,
    revealTick,
    revealTargetPath,
    rootExpanded,
    contextMenu,
    openFolder,
    openWorkspaceAt,
    initializeWindowBoot,
    refreshTree,
    syncAfterAgentDiskWrite,
    scheduleExplorerRescan,
    createFile,
    createFolder,
    renameEntry,
    deleteEntry,
    openContextMenu,
    openRootContextMenu,
    closeContextMenu,
    toggleExpand,
    selectPath,
    revealInTree,
    toggleRootExpanded,
  }
})
