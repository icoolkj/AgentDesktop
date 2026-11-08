import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const editorMocks = vi.hoisted(() => ({
  applyAgentFileChange: vi.fn().mockResolvedValue(undefined),
  closeTab: vi.fn().mockResolvedValue(undefined),
}))

vi.mock('@renderer/stores/editorStore', () => ({
  useEditorStore: () => editorMocks,
}))

vi.mock('@renderer/stores/workspaceStore', () => ({
  useWorkspaceStore: () => ({
    rootPath: 'C:/workspace',
    refreshTree: vi.fn().mockResolvedValue(undefined),
  }),
}))

vi.mock('@renderer/stores/chatStore', () => ({
  useChatStore: () => ({
    sessionId: 'test-session',
  }),
}))

import { useSessionChangesStore } from '@renderer/stores/sessionChangesStore'

describe('sessionChangesStore write path', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    editorMocks.applyAgentFileChange.mockClear()
    editorMocks.closeTab.mockClear()

    vi.stubGlobal('window', {
      workspaceApi: {
        writeFile: vi.fn().mockResolvedValue(undefined),
        deleteFile: vi.fn().mockResolvedValue(undefined),
        writeFilesBatch: vi.fn().mockResolvedValue(undefined),
        deleteFilesBatch: vi.fn().mockResolvedValue(undefined),
      },
      desktopApi: {
        recordFileChange: vi.fn().mockResolvedValue(undefined),
      },
    })
  })

  it('clears pending changes on rewind restore paths', () => {
    const store = useSessionChangesStore()
    store.recordChange({
      path: 'C:/proj/a.ts',
      changeKind: 'modified',
      content: 'new',
      previousContent: 'old',
    })
    store.recordChange({
      path: 'C:/proj/b.ts',
      changeKind: 'modified',
      content: 'x',
      previousContent: 'y',
    })
    expect(store.pendingChanges).toHaveLength(2)
    store.clearPendingForPaths(['C:/proj/a.ts'])
    expect(store.pendingChanges).toHaveLength(1)
    expect(store.pendingChanges[0]?.path).toBe('C:/proj/b.ts')
    store.clearAllPending()
    expect(store.pendingChanges).toHaveLength(0)
  })

  it('records pending change from file_changed payload', () => {
    const store = useSessionChangesStore()
    store.recordChange({
      path: 'C:/workspace/a.txt',
      workspaceRoot: 'C:/workspace',
      changeKind: 'modified',
      content: 'after',
      previousContent: 'before',
    })

    expect(store.pendingChanges).toHaveLength(1)
    expect(store.pendingChanges[0]?.content).toBe('after')
  })

  it('accept does not rewrite disk (CC main path already committed)', async () => {
    const store = useSessionChangesStore()
    store.recordChange({
      path: 'C:/workspace/a.txt',
      changeKind: 'modified',
      content: 'after',
      previousContent: 'before',
    })

    await store.acceptChange('C:/workspace/a.txt')

    expect(window.workspaceApi.writeFile).not.toHaveBeenCalled()
    expect(store.turnChanges.find(c => c.path === 'C:/workspace/a.txt')?.reviewStatus).toBe('accepted')
  })

  it('reject restores previous content on disk', async () => {
    const store = useSessionChangesStore()
    store.recordChange({
      path: 'C:/workspace/a.txt',
      changeKind: 'modified',
      content: 'after',
      previousContent: 'before',
    })

    await store.rejectChange('C:/workspace/a.txt')

    expect(window.workspaceApi.writeFile).toHaveBeenCalledWith('C:/workspace/a.txt', 'before')
    expect(store.pendingChanges).toHaveLength(0)
  })

  it('reject deleted file restores previous content', async () => {
    const store = useSessionChangesStore()
    store.recordChange({
      path: 'C:/workspace/a.txt',
      changeKind: 'deleted',
      previousContent: 'restore-me',
    })

    await store.rejectChange('C:/workspace/a.txt')

    expect(window.workspaceApi.writeFile).toHaveBeenCalledWith('C:/workspace/a.txt', 'restore-me')
  })
})
