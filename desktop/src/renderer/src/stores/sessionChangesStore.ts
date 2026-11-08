import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  buildChangeTree,
  buildUnifiedDiffPreview,
  countLineChanges,
  relativePathFromRoot,
  type ChangeTreeNode,
} from '@renderer/utils/lineDiff'
import { useChatStore } from './chatStore'
import { useEditorStore } from './editorStore'
import { useWorkspaceStore } from './workspaceStore'

export type ChangeReviewStatus = 'pending' | 'accepted' | 'rejected'

export interface SessionFileChange {
  path: string
  relativePath: string
  changeKind: 'modified' | 'created' | 'deleted'
  previousContent?: string
  content: string
  added: number
  removed: number
  diffPreview: string
  changedAt: number
  reviewStatus: ChangeReviewStatus
}

export const useSessionChangesStore = defineStore('sessionChanges', () => {
  const turnChanges = ref<SessionFileChange[]>([])

  const pendingChanges = computed(() => turnChanges.value.filter(c => c.reviewStatus === 'pending'))

  const hasPendingChanges = computed(() => pendingChanges.value.length > 0)

  const changedPaths = computed(
    () => new Set(pendingChanges.value.map(c => c.path.toLowerCase())),
  )

  const changeTree = computed(() =>
    buildChangeTree(
      pendingChanges.value.map(c => ({
        path: c.path,
        relativePath: c.relativePath,
        changeKind: c.changeKind,
        previousContent: c.previousContent,
        content: c.content,
      })),
    ),
  )

  function resetTurn() {
    turnChanges.value = turnChanges.value.filter(c => c.reviewStatus === 'pending')
  }

  function recordChange(input: {
    path: string
    workspaceRoot?: string
    changeKind: 'modified' | 'created' | 'deleted'
    content?: string
    previousContent?: string
  }) {
    const isDeleted = input.changeKind === 'deleted'
    if (!isDeleted && !input.content) return

    const relativePath = relativePathFromRoot(input.workspaceRoot, input.path)
    const previous = input.previousContent ?? ''
    const content = isDeleted ? '' : (input.content ?? '')
    const stats = isDeleted
      ? countLineChanges(previous, '')
      : input.previousContent != null
        ? countLineChanges(previous, content)
        : { added: content.split('\n').length, removed: 0 }
    const diffPreview = isDeleted
      ? buildUnifiedDiffPreview(previous, '')
      : input.previousContent != null
        ? buildUnifiedDiffPreview(previous, content)
        : content.split('\n').slice(0, 24).map(l => `+ ${l}`).join('\n')

    const existingIdx = turnChanges.value.findIndex(
      c => c.path.toLowerCase() === input.path.toLowerCase(),
    )
    const entry: SessionFileChange = {
      path: input.path,
      relativePath,
      changeKind: input.changeKind,
      previousContent: input.previousContent,
      content,
      added: stats.added,
      removed: stats.removed,
      diffPreview,
      changedAt: Date.now(),
      reviewStatus: 'pending',
    }
    if (existingIdx >= 0) {
      turnChanges.value[existingIdx] = entry
    } else {
      turnChanges.value.push(entry)
    }
  }

  async function acceptChange(path: string): Promise<void> {
    const change = getChange(path)
    if (!change || change.reviewStatus !== 'pending') return

    // Agent already wrote to disk (CC main path). Accept only updates review state.
    markAccepted(change)
  }

  function markAccepted(change: SessionFileChange): void {
    const chatStore = useChatStore()
    if (change.changeKind !== 'deleted' && change.content) {
      void window.desktopApi.recordFileChange({
        absolutePath: change.path,
        workspaceRoot: useWorkspaceStore().rootPath ?? null,
        content: change.content,
        previousContent: change.previousContent ?? null,
        changeKind: change.changeKind,
        conversationId: chatStore.sessionId,
        source: 'composer',
      })
    }
    change.reviewStatus = 'accepted'
  }

  async function revertEditorForChange(
    change: SessionFileChange,
    options?: { skipDisk?: boolean },
  ): Promise<void> {
    const editor = useEditorStore()
    const skipDisk = options?.skipDisk === true

    if (change.changeKind === 'deleted') {
      if (!skipDisk && change.previousContent != null) {
        await window.workspaceApi.writeFile(change.path, change.previousContent)
      }
      await editor.applyAgentFileChange(change.path)
      return
    }

    if (change.changeKind === 'created' || change.previousContent == null) {
      if (!skipDisk) {
        await window.workspaceApi.deleteFile(change.path)
      }
      await editor.closeTab(change.path, true)
      return
    }

    if (!skipDisk) {
      await window.workspaceApi.writeFile(change.path, change.previousContent)
    }
    await editor.applyAgentFileChange(change.path)
  }

  async function rejectChange(path: string): Promise<void> {
    const change = getChange(path)
    if (!change || change.reviewStatus !== 'pending') return

    try {
      await revertEditorForChange(change)
    } catch (e) {
      console.error('Failed to revert agent change', e)
      throw e
    }
    change.reviewStatus = 'rejected'
    turnChanges.value = turnChanges.value.filter(
      c => c.path.toLowerCase() !== path.toLowerCase(),
    )
  }

  async function acceptAll(): Promise<void> {
    const pending = [...pendingChanges.value]
    if (pending.length === 0) return

    for (const change of pending) {
      markAccepted(change)
      if (change.changeKind === 'deleted') {
        await useEditorStore().closeTab(change.path, true)
      } else {
        await useEditorStore().applyAgentFileChange(change.path)
      }
    }
    await useWorkspaceStore().refreshTree()
  }

  async function rejectAll(): Promise<void> {
    const pending = [...pendingChanges.value]
    if (pending.length === 0) return

    const diskDeletes: string[] = []
    const diskWrites: { path: string; content: string }[] = []
    for (const change of pending) {
      if (change.changeKind === 'deleted') {
        if (change.previousContent != null) {
          diskWrites.push({ path: change.path, content: change.previousContent })
        }
      } else if (change.changeKind === 'created' || change.previousContent == null) {
        diskDeletes.push(change.path)
      } else if (change.previousContent != null) {
        diskWrites.push({ path: change.path, content: change.previousContent })
      }
    }

    if (diskDeletes.length > 0) {
      await window.workspaceApi.deleteFilesBatch(diskDeletes)
    }
    if (diskWrites.length > 0) {
      await window.workspaceApi.writeFilesBatch(diskWrites)
    }

    for (const change of pending) {
      try {
        await revertEditorForChange(change, { skipDisk: true })
      } catch (e) {
        console.error('Failed to revert agent change', e)
        throw e
      }
      change.reviewStatus = 'rejected'
    }

    turnChanges.value = turnChanges.value.filter(c => c.reviewStatus === 'pending')
    await useWorkspaceStore().refreshTree()
  }

  function isChanged(path: string): boolean {
    return changedPaths.value.has(path.toLowerCase())
  }

  function hasChangedUnder(dirPath: string): boolean {
    const prefix = dirPath.replace(/\\/g, '/').replace(/\/+$/, '/').toLowerCase()
    for (const change of pendingChanges.value) {
      const file = change.path.replace(/\\/g, '/').toLowerCase()
      if (file.startsWith(prefix)) return true
    }
    return false
  }

  function getTurnSnapshot(): SessionFileChange[] {
    return turnChanges.value
      .filter(c => c.reviewStatus !== 'rejected')
      .map(c => ({ ...c }))
  }

  function getChange(path: string): SessionFileChange | undefined {
    return turnChanges.value.find(
      c => c.path.toLowerCase() === path.toLowerCase() && c.reviewStatus === 'pending',
    )
  }

  function applyPathRename(oldPath: string, newPath: string): void {
    const oldNorm = oldPath.replace(/\\/g, '/')
    const newNorm = newPath.replace(/\\/g, '/')
    const oldLower = oldNorm.toLowerCase()
    const prefix = `${oldNorm}/`

    turnChanges.value = turnChanges.value.map(change => {
      const changeNorm = change.path.replace(/\\/g, '/')
      const changeLower = changeNorm.toLowerCase()

      if (changeLower !== oldLower && !changeLower.startsWith(prefix.toLowerCase())) {
        return change
      }

      const updatedPath =
        changeLower === oldLower ? newPath : newNorm + changeNorm.slice(oldNorm.length)
      return {
        ...change,
        path: updatedPath,
        relativePath: relativePathFromRoot(undefined, updatedPath),
      }
    })
  }

  function clearPendingForPaths(paths: string[]) {
    if (paths.length === 0) return
    const lowered = new Set(paths.map(p => p.toLowerCase()))
    turnChanges.value = turnChanges.value.filter(
      c => c.reviewStatus !== 'pending' || !lowered.has(c.path.toLowerCase()),
    )
  }

  function clearAllPending() {
    turnChanges.value = turnChanges.value.filter(c => c.reviewStatus !== 'pending')
  }

  async function openPendingReview(path?: string): Promise<void> {
    const editor = useEditorStore()
    const target = path ?? pendingChanges.value[0]?.path
    if (!target) return
    await editor.openFile(target)
  }

  return {
    turnChanges,
    pendingChanges,
    hasPendingChanges,
    changedPaths,
    changeTree,
    resetTurn,
    recordChange,
    acceptChange,
    rejectChange,
    acceptAll,
    rejectAll,
    isChanged,
    hasChangedUnder,
    getChange,
    applyPathRename,
    getTurnSnapshot,
    clearPendingForPaths,
    clearAllPending,
    openPendingReview,
  }
})

export type { ChangeTreeNode }

export type ChangeListItem = Pick<
  SessionFileChange,
  'path' | 'relativePath' | 'changeKind' | 'added' | 'removed' | 'diffPreview'
> &
  Partial<Pick<SessionFileChange, 'previousContent' | 'content' | 'changedAt' | 'reviewStatus'>>
