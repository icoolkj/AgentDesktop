import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@renderer/utils/workspaceSession', () => ({
  loadSessionIdForWorkspace: vi.fn().mockResolvedValue(null),
  saveSessionIdForWorkspace: vi.fn().mockResolvedValue(undefined),
  sessionBelongsToWorkspace: () => true,
}))

vi.mock('@renderer/utils/chatAttachments', () => ({
  buildChatAttachments: vi.fn().mockResolvedValue({ attachments: [], displayText: '' }),
}))

vi.mock('@renderer/utils/projectRules', () => ({
  loadProjectRules: vi.fn().mockResolvedValue(''),
}))

vi.mock('@renderer/utils/gitStatusSummary', () => ({
  loadGitStatusSummary: vi.fn().mockResolvedValue(''),
}))

const agentMocks = vi.hoisted(() => {
  let eventHandler = null as null | ((event: unknown) => void)
  return {
    ws: { readyState: 1, send: vi.fn() },
    eventHandler: {
      get: () => eventHandler,
      set: (fn: null | ((event: unknown) => void)) => {
        eventHandler = fn
      },
    },
    subscribeEvents: vi.fn((fn: (event: unknown) => void) => {
      eventHandler = fn
      return () => {
        eventHandler = null
      }
    }),
    connect: vi.fn().mockResolvedValue(undefined),
    reconnect: vi.fn().mockResolvedValue(undefined),
    initLifecycle: vi.fn(),
    cleanup: vi.fn(),
    setError: vi.fn(),
    status: 'connected' as const,
    invokeRpc: vi.fn().mockResolvedValue({ ok: true, paths: [] }),
  }
})

const sessionChangesMocks = vi.hoisted(() => ({
  hasPendingChanges: false,
  resetTurn: vi.fn(),
  recordChange: vi.fn(),
  clearPendingForPaths: vi.fn(),
  clearAllPending: vi.fn(),
}))

vi.mock('@renderer/stores/agentStore', () => ({
  useAgentStore: () => agentMocks,
}))

vi.mock('@renderer/stores/sessionChangesStore', () => ({
  useSessionChangesStore: () => sessionChangesMocks,
}))

vi.mock('@renderer/stores/workspaceStore', () => ({
  useWorkspaceStore: () => ({
    rootPath: 'C:/workspace',
    refreshTree: vi.fn().mockResolvedValue(undefined),
    scheduleExplorerRescan: vi.fn(),
    syncAfterAgentDiskWrite: vi.fn().mockResolvedValue(undefined),
  }),
}))

vi.mock('@renderer/stores/editorStore', () => ({
  useEditorStore: () => ({
    activePath: null,
    openDocuments: [],
    applyAgentFileChange: vi.fn().mockResolvedValue(undefined),
    closeTab: vi.fn().mockResolvedValue(undefined),
  }),
}))

const agentSettingsMocks = { permissionMode: 'auto' as 'ask' | 'auto' | 'plan' }

vi.mock('@renderer/stores/agentSettingsStore', () => ({
  useAgentSettingsStore: () => agentSettingsMocks,
}))

vi.mock('@renderer/stores/modelSettingsStore', () => ({
  useModelSettingsStore: () => ({
    resolveSendModel: () => 'auto',
  }),
}))

import { useChatStore } from '@renderer/stores/chatStore'

describe('chatStore P4-A behaviors', () => {
  beforeEach(() => {
    vi.stubGlobal('WebSocket', { OPEN: 1, CLOSED: 3 })
    setActivePinia(createPinia())
    agentMocks.ws.send.mockClear()
    agentMocks.ws.readyState = 1
    sessionChangesMocks.hasPendingChanges = false
    sessionChangesMocks.resetTurn.mockClear()
    sessionChangesMocks.recordChange.mockClear()
    sessionChangesMocks.clearAllPending.mockClear()
    sessionChangesMocks.clearPendingForPaths.mockClear()
    agentMocks.eventHandler.set(null)
    agentSettingsMocks.permissionMode = 'auto'

    vi.stubGlobal('window', {
      transcriptApi: {
        setTitle: vi.fn().mockResolvedValue(undefined),
        appendUser: vi.fn().mockResolvedValue({ messageUuid: 'user-uuid-1', storedAttachments: [] }),
        flush: vi.fn().mockResolvedValue(undefined),
        loadSession: vi.fn().mockResolvedValue({ entries: [] }),
        listSessions: vi.fn().mockResolvedValue([]),
        switchSession: vi.fn().mockResolvedValue(undefined),
        truncate: vi.fn().mockResolvedValue(undefined),
      },
      desktopApi: {
        recordFileChange: vi.fn().mockResolvedValue(undefined),
      },
      workspaceApi: {
        readFile: vi.fn().mockResolvedValue('ok'),
        readFileDataUrl: vi.fn().mockResolvedValue({ dataUrl: 'data:text/plain;base64,eA==' }),
      },
    })
  })

  it('blocks sendMessage when pending review in Ask mode (CC default + batch review)', async () => {
    agentSettingsMocks.permissionMode = 'ask'
    sessionChangesMocks.hasPendingChanges = true
    const store = useChatStore()
    await store.sendMessage('hello')
    expect(store.error).toContain('输入框上方')
    expect(agentMocks.ws.send).not.toHaveBeenCalled()
  })

  it('does not block sendMessage in Agent mode when pending (CC acceptEdits parity)', async () => {
    agentSettingsMocks.permissionMode = 'auto'
    sessionChangesMocks.hasPendingChanges = true
    const store = useChatStore()
    await store.sendMessage('hello')
    expect(store.error).not.toContain('Ask 模式')
    expect(store.error).not.toContain('全部接受')
  })

  it('blocks sendMessage when websocket is not open', async () => {
    agentMocks.ws.readyState = 3
    const store = useChatStore()
    await store.sendMessage('hello')
    expect(store.error).toContain('Not connected')
  })

  it('syncCurrentSession maps transcript attachments to messages', async () => {
    window.transcriptApi.loadSession = vi.fn().mockResolvedValue({
      entries: [
        {
          type: 'user',
          uuid: 'u1',
          message: { role: 'user', content: 'with image' },
          adAttachments: [
            {
              id: 'a1',
              kind: 'image',
              name: 'note.png',
              path: 'C:/store/note.png',
              source: 'store',
            },
          ],
        },
      ],
    })

    const store = useChatStore()
    await store.syncCurrentSession(true)

    expect(store.messages).toHaveLength(1)
    expect(store.messages[0]?.attachments?.[0]?.name).toBe('note.png')
    expect(store.messages[0]?.attachments?.[0]?.filePath).toBe('C:/store/note.png')
  })

  it('handleAgentEvent file_changed records pending change', async () => {
    const store = useChatStore()
    await store.init()
    const onEvent = agentMocks.eventHandler.get()
    expect(onEvent).toBeTypeOf('function')

    onEvent?.({
      type: 'file_changed',
      path: 'C:/workspace/a.txt',
      changeKind: 'modified',
      content: 'after',
      previousContent: 'before',
    })

    expect(sessionChangesMocks.recordChange).toHaveBeenCalledWith(
      expect.objectContaining({
        path: 'C:/workspace/a.txt',
        changeKind: 'modified',
        content: 'after',
        previousContent: 'before',
      }),
    )
  })

  it('handleAgentEvent rewind_restored clears pending review items', async () => {
    const store = useChatStore()
    await store.init()
    const onEvent = agentMocks.eventHandler.get()!

    onEvent({
      type: 'rewind_restored',
      sessionId: 'sess-1',
      paths: ['C:/workspace/a.txt'],
    })

    expect(sessionChangesMocks.clearPendingForPaths).toHaveBeenCalledWith(['C:/workspace/a.txt'])
  })
})
