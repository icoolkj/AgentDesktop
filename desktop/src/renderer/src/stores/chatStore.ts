import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import type {
  AgentEvent,
  ChatAttachment,
  ChatSendParams,
  ContextBreakdown,
  JsonRpcRequest,
  PermissionReplyParams,
  PermissionRequestEvent,
} from '@agent-desktop/shared-types'
import { createSessionId, transcriptToUiMessages } from '@agent-desktop/shared-types'
import { useAgentStore } from './agentStore'
import { useWorkspaceStore } from './workspaceStore'
import { useEditorStore } from './editorStore'
import { useSessionChangesStore } from './sessionChangesStore'
import type { ChatSessionSummary } from '@agent-desktop/shared-types'
import {
  loadSessionIdForWorkspace,
  saveSessionIdForWorkspace,
  sessionBelongsToWorkspace,
} from '@renderer/utils/workspaceSession'
import { useAgentSettingsStore } from './agentSettingsStore'
import { useModelSettingsStore } from './modelSettingsStore'
import { buildChatAttachments } from '@renderer/utils/chatAttachments'
import { attachmentSummary } from '@renderer/utils/imageAttachment'
import { getEditorSelection } from '@renderer/utils/editorSearchBridge'
import { loadProjectRules } from '@renderer/utils/projectRules'
import { loadGitStatusSummary } from '@renderer/utils/gitStatusSummary'
import { formatToolLabel } from '@renderer/utils/toolDisplay'
import {
  autoAcknowledgeWritesOnTurnComplete,
  blocksSendOnPendingReview,
  sendBlockedMessage,
} from '@renderer/utils/permissionModePolicy'
import {
  applyStoredAttachmentPaths,
  applyStoredPathsToDisplayAttachments,
  buildPersistAttachmentInputs,
  mergeDisplayAttachments,
  uiAttachmentsToChat,
} from '@renderer/utils/persistUserAttachments'

export interface ChatToolActivity {
  toolCallId: string
  name: string
  status: 'running' | 'done' | 'error'
  summary?: string
  output?: string
  argsJson?: string
  startedAt?: number
  endedAt?: number
}

export interface ChatFileChange {
  path: string
  relativePath: string
  changeKind: 'modified' | 'created' | 'deleted'
  added: number
  removed: number
  diffPreview: string
}

export interface ChatUserAttachment {
  id: string
  kind: 'image' | 'pdf' | 'text'
  name: string
  previewUrl?: string
  filePath?: string
  source?: 'store' | 'workspace'
  missing?: boolean
}

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  messageUuid?: string
  attachments?: ChatUserAttachment[]
  tools?: ChatToolActivity[]
  fileChanges?: ChatFileChange[]
  createdAt?: string
}

export interface AgentRunTimelineEntry {
  id: string
  kind: 'step' | 'tool' | 'llm' | 'stream' | 'client'
  phase?: string
  label: string
  detail?: string
  turn?: number
  status: 'active' | 'done' | 'error'
  startedAt: number
  endedAt?: number
  toolCallId?: string
}

export const useChatStore = defineStore('chat', () => {
  const messages = ref<ChatMessage[]>([])
  const sessions = ref<ChatSessionSummary[]>([])
  const sessionId = ref(createSessionId())
  const streamingText = ref('')
  const toolActivity = ref<ChatToolActivity[]>([])
  const runStatus = ref('')
  const isSending = ref(false)
  const error = ref<string | null>(null)
  const permissionRequest = ref<PermissionRequestEvent | null>(null)
  const contextStats = ref<{ detail: string; percent: number } | null>(null)
  const contextBreakdown = ref<ContextBreakdown | null>(null)
  const runTimeline = ref<AgentRunTimelineEntry[]>([])
  const runMode = ref<'stream' | 'agent' | null>(null)
  const runStartedAt = ref(0)
  const rewindDialogOpen = ref(false)
  const rewindBusy = ref(false)

  let requestId = 0
  let streamingBuffer = ''
  let timelineSeq = 0
  let streamTimelineStarted = false
  let unsubscribeEvents: (() => void) | null = null
  let initialized = false
  let workspaceWatchStarted = false
  let disconnectGuardStarted = false

  watch(sessionId, id => {
    void saveSessionIdForWorkspace(workspaceRoot() ?? null, id)
  })

  function workspaceRoot(): string | undefined {
    return useWorkspaceStore().rootPath ?? undefined
  }

  /** Claude switchSession + listSessions({ dir: cwd }) — bind session to current project. */
  async function onWorkspaceChanged() {
    const root = workspaceRoot() ?? null
    await refreshSessions()

    const savedId = await loadSessionIdForWorkspace(root)
    const savedInList = savedId && sessions.value.some(s => s.id === savedId)

    if (savedInList && savedId) {
      sessionId.value = savedId
    } else if (sessions.value.length > 0) {
      sessionId.value = sessions.value[0]!.id
    } else {
      sessionId.value = createSessionId()
    }

    await saveSessionIdForWorkspace(root, sessionId.value)
    window.transcriptApi.switchSession(sessionId.value, root, null)
    messages.value = []
    streamingBuffer = ''
    streamingText.value = ''
    error.value = null
    resetRunState()
    isSending.value = false
    await syncCurrentSession(true)
  }

  function ensureWorkspaceWatch() {
    if (workspaceWatchStarted) return
    workspaceWatchStarted = true
    const workspaceStore = useWorkspaceStore()
    watch(
      () => workspaceStore.rootPath,
      (next, prev) => {
        if (next !== prev) {
          void onWorkspaceChanged()
        }
      },
    )
  }

  function resetRunState() {
    toolActivity.value = []
    runStatus.value = ''
  }

  function resetTimeline() {
    runTimeline.value = []
    runMode.value = null
    runStartedAt.value = 0
    timelineSeq = 0
    streamTimelineStarted = false
  }

  function completeTimelineActive(match: (e: AgentRunTimelineEntry) => boolean, status: 'done' | 'error' = 'done') {
    const t = Date.now()
    for (const entry of runTimeline.value) {
      if (entry.status === 'active' && match(entry)) {
        entry.status = status
        entry.endedAt = t
      }
    }
  }

  function pushTimeline(
    entry: Omit<AgentRunTimelineEntry, 'id' | 'startedAt'> & { startedAt?: number },
  ) {
    runTimeline.value.push({
      id: `tl-${++timelineSeq}`,
      startedAt: entry.startedAt ?? Date.now(),
      ...entry,
    })
  }

  function recordClientStep(label: string, detail?: string) {
    pushTimeline({ kind: 'client', label, detail, status: 'active' })
  }

  function markClientStepDone(labelPrefix: string) {
    const t = Date.now()
    for (const entry of runTimeline.value) {
      if (entry.kind === 'client' && entry.status === 'active' && entry.label.startsWith(labelPrefix)) {
        entry.status = 'done'
        entry.endedAt = t
      }
    }
  }

  function normalizeRunMode(mode?: string): 'stream' | 'agent' | undefined {
    if (mode === 'agent' || mode === 'stream') return mode
    return undefined
  }

  function recordAgentStep(
    phase: string,
    label: string,
    turn?: number,
    detail?: string,
    mode?: string,
  ) {
    const wire = normalizeRunMode(mode)
    if (wire) runMode.value = wire

    if (phase === 'received' || phase === 'history_loaded') {
      if (phase === 'received') {
        completeTimelineActive(e => e.kind === 'client' && e.phase === 'ack')
      }
      pushTimeline({ kind: 'step', phase, label, detail, turn, status: 'done' })
      return
    }

    if (phase === 'llm') {
      completeTimelineActive(e => e.kind === 'llm' || e.phase === 'llm')
      pushTimeline({ kind: 'llm', phase, label, detail, turn, status: 'active' })
    } else if (phase === 'llm_done') {
      completeTimelineActive(e => e.kind === 'llm' || e.phase === 'llm')
      pushTimeline({ kind: 'llm', phase, label, detail, turn, status: 'done' })
    } else if (phase === 'tools') {
      completeTimelineActive(e => e.phase === 'tools')
      pushTimeline({ kind: 'step', phase, label, detail, turn, status: 'active' })
    } else if (phase === 'tools_done') {
      completeTimelineActive(e => e.phase === 'tools')
      pushTimeline({ kind: 'step', phase, label, detail, turn, status: 'done' })
    } else if (phase === 'cancelled' || phase === 'error' || phase === 'max_turns') {
      completeTimelineActive(() => true, 'error')
      pushTimeline({ kind: 'step', phase, label, detail, turn, status: 'error' })
    } else {
      completeTimelineActive(e => e.kind === 'client' && e.status === 'active')
      pushTimeline({ kind: 'step', phase, label, detail, turn, status: phase === 'mode' ? 'done' : 'active' })
    }
  }

  /** Reset in-flight turn without polluting chat history (connection blips). */
  function softCancelInFlight(reason?: string) {
    if (!isSending.value) return
    forceEndTurn()
    streamingBuffer = ''
    streamingText.value = ''
    resetRunState()
    isSending.value = false
    clearSendWatchdog()
    if (reason) {
      error.value = reason
    }
  }

  function ensureDisconnectGuard() {
    if (disconnectGuardStarted) return
    disconnectGuardStarted = true
    const agentStore = useAgentStore()
    watch(
      () => agentStore.status,
      (next, prev) => {
        if (next === 'connected') {
          error.value = null
          return
        }
        if (
          prev === 'connected' &&
          next === 'disconnected' &&
          isSending.value &&
          !agentStore.reconnecting
        ) {
          softCancelInFlight('连接中断，正在自动重连…')
        }
      },
    )
  }

  let sendWatchdogTimer: ReturnType<typeof setTimeout> | null = null
  let progressTimer: ReturnType<typeof setInterval> | null = null
  let sendStartedAt = 0

  /**
   * Claude Code QueryGuard parity — stale `done`/finally from a cancelled turn
   * must not reset state for a newer in-flight turn.
   * @see claude_code_src-master/src/utils/QueryGuard.ts
   */
  let turnGeneration = 0
  let activeTurnGeneration = 0

  function beginTurn(): number {
    turnGeneration += 1
    activeTurnGeneration = turnGeneration
    return activeTurnGeneration
  }

  /** Like QueryGuard.forceEnd — cancel bumps generation so stale handlers no-op. */
  function forceEndTurn(): void {
    turnGeneration += 1
    activeTurnGeneration = 0
  }

  function isCurrentTurn(gen: number): boolean {
    return gen !== 0 && gen === activeTurnGeneration
  }

  function endTurnIfCurrent(gen: number): boolean {
    if (!isCurrentTurn(gen)) return false
    activeTurnGeneration = 0
    return true
  }

  function clearProgressTimer() {
    if (progressTimer) {
      clearInterval(progressTimer)
      progressTimer = null
    }
  }

  function startProgressTimer() {
    clearProgressTimer()
    sendStartedAt = Date.now()
    progressTimer = setInterval(() => {
      if (!isSending.value) {
        clearProgressTimer()
        return
      }
      const secs = Math.floor((Date.now() - sendStartedAt) / 1000)
      const waitingServer =
        runStatus.value.startsWith('发送到 Agent') ||
        runStatus.value.startsWith('等待 Agent') ||
        runStatus.value.startsWith('已发送，等待 Agent')
      if (secs >= 15 && waitingServer) {
        runStatus.value = `Agent 未响应（${secs}s）— 请 Restart Agent`
        return
      }
      if (secs >= 8 && !streamingBuffer && toolActivity.value.length === 0) {
        const base = runStatus.value.replace(/\s*\(\d+s\)$/, '')
        runStatus.value = `${base || '处理中'} (${secs}s)`
      }
    }, 4000)
  }

  function clearSendWatchdog() {
    if (sendWatchdogTimer) {
      clearTimeout(sendWatchdogTimer)
      sendWatchdogTimer = null
    }
    clearProgressTimer()
  }

  function startSendWatchdog() {
    clearSendWatchdog()
    sendWatchdogTimer = setTimeout(() => {
      if (isSending.value) {
        softCancelInFlight('请求超时，请重试或缩小问题范围')
      }
    }, 5 * 60_000)
  }

  function toolOutputSummary(name: string, output: string): string | undefined {
    if (!output) return undefined
    const fileMatch = output.match(/^File:\s*(.+?)(?:\s|\(|$)/m)
    if (fileMatch) return fileMatch[1].trim()
    if (name === 'list_dir') {
      const lines = output.split('\n').filter(l => l.trim()).length
      return lines > 0 ? `${lines} entries` : undefined
    }
    if (name === 'glob' || name === 'grep') {
      const m = output.match(/Found (\d+) match|\b(\d+) file\(s\)/i)
      if (m) return m[1] ?? m[2]
    }
    if (name === 'search_replace') {
      const m = output.match(/in (.+)$/)
      if (m) return m[1]?.trim()
    }
    return undefined
  }

  function updateRunStatus(phase: string, label: string) {
    if (phase === 'tool' || phase === 'tool_done') return
    if (label?.trim()) runStatus.value = label
  }

  function refreshToolRunStatus() {
    const done = toolActivity.value.filter(t => t.status === 'done').length
    const running = toolActivity.value.find(t => t.status === 'running')
    if (running) {
      runStatus.value = formatToolLabel(running.name, running.argsJson, running.output, running.summary)
    } else if (done > 0) {
      runStatus.value = `已探索 ${done} 步`
    }
  }

  function upsertToolStart(toolCallId: string, name: string, argsJson?: string) {
    const now = Date.now()
    const label = formatToolLabel(name, argsJson)
    const existing = toolActivity.value.find(item => item.toolCallId === toolCallId)
    if (existing) {
      existing.name = name
      existing.status = 'running'
      existing.argsJson = argsJson
      existing.startedAt = existing.startedAt ?? now
      existing.endedAt = undefined
    } else {
      toolActivity.value.push({ toolCallId, name, status: 'running', argsJson, startedAt: now })
    }
    const argHint = formatToolArgsHint(argsJson)
    pushTimeline({
      kind: 'tool',
      phase: 'tool',
      label,
      detail: argHint,
      status: 'active',
      toolCallId,
    })
    runStatus.value = label
  }

  function formatToolArgsHint(argsJson?: string): string | undefined {
    if (!argsJson?.trim()) return undefined
    try {
      const parsed = JSON.parse(argsJson) as Record<string, unknown>
      const path = parsed.path ?? parsed.pattern ?? parsed.glob
      if (path != null) return String(path)
      const keys = Object.keys(parsed).slice(0, 2)
      return keys.map(k => `${k}=${String(parsed[k]).slice(0, 40)}`).join(', ')
    } catch {
      return argsJson.length > 60 ? argsJson.slice(0, 57) + '...' : argsJson
    }
  }

  function upsertToolResult(toolCallId: string, name: string, ok: boolean, output: string) {
    const summary = toolOutputSummary(name, output)
    const now = Date.now()
    const existing = toolActivity.value.find(item => item.toolCallId === toolCallId)
    if (existing) {
      existing.name = name
      existing.status = ok ? 'done' : 'error'
      existing.summary = summary
      existing.output = output
      existing.endedAt = now
    } else {
      toolActivity.value.push({
        toolCallId,
        name,
        status: ok ? 'done' : 'error',
        summary,
        output,
        startedAt: now,
        endedAt: now,
      })
    }
    const tl = runTimeline.value.find(e => e.toolCallId === toolCallId && e.status === 'active')
    if (tl) {
      tl.status = ok ? 'done' : 'error'
      tl.endedAt = now
      tl.label = formatToolLabel(name, existing?.argsJson, output, summary)
      tl.detail = summary ?? tl.detail
    }
    refreshToolRunStatus()
  }

  async function persistAssistantTurn(content: string) {
    const text = content.trim()
    if (text) {
      await window.transcriptApi.appendAssistantText(sessionId.value, workspaceRoot() ?? null, text)
    }
    await window.transcriptApi.flush(sessionId.value, workspaceRoot() ?? null)
  }

  function finalizeTurn(sessionIdFromEvent?: string, turnGen?: number) {
    const gen = turnGen ?? activeTurnGeneration
    if (gen !== 0 && !isCurrentTurn(gen)) return
    if (!isSending.value && gen === 0) return

    endTurnIfCurrent(gen)
    if (sessionIdFromEvent) {
      sessionId.value = sessionIdFromEvent
    }

    const toolsSnapshot = [...toolActivity.value]
    const fileChangesSnapshot = useSessionChangesStore().getTurnSnapshot()
    const hadTools = toolsSnapshot.length > 0
    const failMsg = error.value
    const successText = streamingBuffer

    let content = successText
    if (!content && failMsg) {
      content = `**未能完成：** ${failMsg}`
    } else if (!content && hadTools) {
      content =
        '**未能完成：** 模型未返回文字结果（可能超时或上下文过长）。请展开下方工具详情，或缩小问题后重试。'
    }

    if (content || hadTools || fileChangesSnapshot.length > 0) {
      messages.value.push({
        role: 'assistant',
        content: content || '（无文字输出）',
        tools: hadTools ? toolsSnapshot : undefined,
        fileChanges: fileChangesSnapshot.length
          ? fileChangesSnapshot.map(c => ({
              path: c.path,
              relativePath: c.relativePath,
              changeKind: c.changeKind,
              added: c.added,
              removed: c.removed,
              diffPreview: c.diffPreview,
            }))
          : undefined,
        createdAt: new Date().toISOString(),
      })
    }

    void persistAssistantTurn(content || '')

    if (autoAcknowledgeWritesOnTurnComplete(useAgentSettingsStore().permissionMode)) {
      const changesStore = useSessionChangesStore()
      if (changesStore.hasPendingChanges) {
        void changesStore.acceptAll()
      }
    }

    streamingBuffer = ''
    streamingText.value = ''
    resetRunState()
    isSending.value = false
    clearSendWatchdog()

    if (successText) {
      error.value = null
    }

    void refreshSessions()
  }

  function handleAgentEvent(event: AgentEvent) {
    const ws = workspaceRoot() ?? null
    const sid = sessionId.value

    switch (event.type) {
      case 'agent_step':
        if (event.phase === 'cancelled') {
          runStatus.value = event.label
        } else {
          updateRunStatus(event.phase, event.label)
        }
        recordAgentStep(event.phase, event.label, event.turn, event.detail, event.mode)
        if ((event.phase === 'context' || event.phase === 'compact') && event.detail) {
          const matchPct = event.detail.match(/\((\d+)%\)/)
          const matchRatio = event.detail.match(/(\d[\d,]*)\s*\/\s*(\d[\d,]*)/)
          let percent = 0
          if (matchPct) {
            percent = Number.parseInt(matchPct[1]!, 10)
          } else if (matchRatio) {
            const used = Number.parseInt(matchRatio[1]!.replace(/,/g, ''), 10)
            const max = Number.parseInt(matchRatio[2]!.replace(/,/g, ''), 10)
            percent = max > 0 ? Math.round((used * 100) / max) : 0
          }
          contextStats.value = { detail: event.detail, percent }
        }
        break
      case 'permission_request':
        permissionRequest.value = event
        break
      case 'context_breakdown':
        contextBreakdown.value = event.breakdown
        contextStats.value = {
          detail: `${event.breakdown.totalChars.toLocaleString()} / ${event.breakdown.maxChars.toLocaleString()} 字符 (${event.breakdown.percent}%)`,
          percent: event.breakdown.percent,
        }
        break
      case 'compact_boundary':
        contextStats.value = {
          detail: `${event.usedChars} / ${event.maxChars} 字符`,
          percent: event.maxChars > 0 ? Math.round((event.usedChars * 100) / event.maxChars) : 0,
        }
        void window.transcriptApi.appendCompactBoundary(sid, ws, {
          trigger: event.trigger,
          usedChars: event.usedChars,
          maxChars: event.maxChars,
          summary: event.summary,
        })
        break
      case 'text_delta':
        error.value = null
        if (!streamTimelineStarted) {
          streamTimelineStarted = true
          completeTimelineActive(e => e.kind === 'llm' || e.phase === 'llm')
          pushTimeline({ kind: 'stream', phase: 'stream', label: '流式生成回答', status: 'active' })
        }
        runStatus.value = '生成回答…'
        streamingBuffer += event.delta
        streamingText.value = streamingBuffer
        break
      case 'tool_start':
        upsertToolStart(event.toolCallId, event.name, event.argsJson)
        void window.transcriptApi.appendToolUse(sid, ws, {
          toolCallId: event.toolCallId,
          name: event.name,
          argsJson: event.argsJson,
        })
        break
      case 'tool_result':
        upsertToolResult(event.toolCallId, event.name, event.ok, event.output)
        void window.transcriptApi.appendToolResult(sid, ws, {
          toolCallId: event.toolCallId,
          name: event.name,
          ok: event.ok,
          output: event.output,
        })
        break
      case 'file_changed': {
        const changesStore = useSessionChangesStore()
        const workspaceStore = useWorkspaceStore()
        const isDeleted = event.changeKind === 'deleted'
        changesStore.recordChange({
          path: event.path,
          workspaceRoot: workspaceRoot() ?? undefined,
          changeKind: event.changeKind,
          content: event.content,
          previousContent: event.previousContent,
        })
        if (!isDeleted && event.content != null) {
          void window.desktopApi.recordFileChange({
            absolutePath: event.path,
            workspaceRoot: workspaceRoot() ?? null,
            content: event.content,
            previousContent: event.previousContent ?? null,
            // 删除事件不会进入本分支；窄化类型以匹配 DTO（仅 modified/created）
            changeKind: event.changeKind === 'deleted' ? 'modified' : event.changeKind,
            conversationId: sid,
            source: 'composer',
          })
        }
        if (isDeleted) {
          void workspaceStore.refreshTree()
          void useEditorStore().applyAgentFileChange(event.path, { deleted: true })
        } else {
          void workspaceStore.syncAfterAgentDiskWrite(event.path).then(() => {
            void useEditorStore().applyAgentFileChange(event.path)
          })
        }
        break
      }
      case 'rewind_restored': {
        const changesStore = useSessionChangesStore()
        if (event.paths?.length) {
          changesStore.clearPendingForPaths(event.paths)
        } else {
          changesStore.clearAllPending()
        }
        void (async () => {
          for (const path of event.paths ?? []) {
            if (!path) continue
            try {
              await window.workspaceApi.readFile(path)
              await useEditorStore().applyAgentFileChange(path)
            } catch {
              await useEditorStore().closeTab(path, true)
            }
          }
          await useWorkspaceStore().refreshTree()
        })()
        break
      }
      case 'subagent_start':
        pushTimeline({
          kind: 'step',
          phase: 'subagent',
          label: `子 Agent · ${event.subagentType}`,
          detail: event.description,
          status: 'active',
        })
        break
      case 'subagent_done':
        completeTimelineActive(e => e.phase === 'subagent' && e.status === 'active')
        pushTimeline({
          kind: 'step',
          phase: 'subagent',
          label: `子 Agent 完成 · ${event.subagentType}`,
          detail: event.summary,
          status: event.ok ? 'done' : 'error',
        })
        break
      case 'done':
        completeTimelineActive(() => true)
        useWorkspaceStore().scheduleExplorerRescan()
        finalizeTurn(event.sessionId)
        break
      case 'error':
        error.value = event.message
        runStatus.value = event.message
        recordAgentStep('error', event.message)
        if (isSending.value) {
          finalizeTurn()
        }
        break
      case 'ready':
        break
      case 'inline_edit_delta':
      case 'inline_edit_done':
      case 'inline_edit_error':
        break
    }
  }

  async function init() {
    ensureWorkspaceWatch()
    ensureDisconnectGuard()
    const agentStore = useAgentStore()
    agentStore.initLifecycle()

    if (!unsubscribeEvents) {
      unsubscribeEvents = agentStore.subscribeEvents(handleAgentEvent)
    }

    if (!initialized) {
      await agentStore.connect()
      initialized = true
      await onWorkspaceChanged()
      return
    }

    if (agentStore.status !== 'connected') {
      await agentStore.reconnect()
    }
    await onWorkspaceChanged()
  }

  async function refreshSessions() {
    try {
      const root = workspaceRoot()
      const list = await window.transcriptApi.listSessions(root ?? null, 30)
      sessions.value = list
        .filter(item => sessionBelongsToWorkspace(item.cwd, root))
        .map(item => ({
          id: item.sessionId,
          title: item.title,
          workspace: item.cwd,
          updatedAt: item.updatedAt,
        }))
    } catch (e) {
      console.warn('[transcript] list sessions failed:', e)
    }
  }

  function enrichAssistantMetadata(loaded: ChatMessage[], previous: ChatMessage[]): ChatMessage[] {
    if (previous.length === 0) return loaded
    const prevAssistants = previous.filter(m => m.role === 'assistant')
    let ai = 0
    return loaded.map(msg => {
      if (msg.role !== 'assistant') return msg
      const prev = prevAssistants[ai++]
      if (!prev) return msg
      return {
        ...msg,
        tools: msg.tools?.length ? msg.tools : prev.tools,
        fileChanges: msg.fileChanges?.length ? msg.fileChanges : prev.fileChanges,
      }
    })
  }

  async function syncCurrentSession(force = false) {
    if (isSending.value && !force) return

    const root = workspaceRoot()
    const summary = sessions.value.find(s => s.id === sessionId.value)
    if (summary && !sessionBelongsToWorkspace(summary.workspace, root)) {
      await onWorkspaceChanged()
      return
    }

    try {
      const loaded = await window.transcriptApi.loadSession(
        sessionId.value,
        root ?? summary?.workspace ?? null,
      )
      const mapped = transcriptToUiMessages(loaded.entries).map(msg => ({
        role: msg.role,
        content: msg.content,
        messageUuid: msg.messageUuid,
        attachments: uiAttachmentsToChat(msg.attachments),
        tools: msg.tools,
        createdAt: msg.createdAt,
      }))
      messages.value = enrichAssistantMetadata(mapped, messages.value)
    } catch (e) {
      console.warn('[transcript] load session failed:', e)
      if (!isSending.value) messages.value = []
    }

    if (!isSending.value) {
      streamingBuffer = ''
      streamingText.value = ''
      resetRunState()
    }
  }

  async function loadSession(id: string) {
    sessionId.value = id
    await saveSessionIdForWorkspace(workspaceRoot() ?? null, id)
    window.transcriptApi.switchSession(id, workspaceRoot() ?? null, null)
    messages.value = []
    streamingBuffer = ''
    streamingText.value = ''
    error.value = null
    resetRunState()
    isSending.value = false

    await refreshSessions()
    await syncCurrentSession(true)
  }

  async function newSession() {
    sessionId.value = createSessionId()
    await saveSessionIdForWorkspace(workspaceRoot() ?? null, sessionId.value)
    window.transcriptApi.switchSession(sessionId.value, workspaceRoot() ?? null, null)
    messages.value = []
    streamingBuffer = ''
    streamingText.value = ''
    error.value = null
    resetRunState()
    isSending.value = false
  }

  async function sendMessage(
    text: string,
    uploads: ChatAttachment[] = [],
    displayAttachments: ChatUserAttachment[] = [],
  ) {
    const agentStore = useAgentStore()
    const socket = agentStore.ws

    if (!socket || socket.readyState !== WebSocket.OPEN) {
      error.value = 'Not connected to agent'
      return
    }

    if (isSending.value) {
      error.value = '上一条消息仍在处理中，请先点 Stop 或等待完成'
      return
    }

    const trimmed = text.trim()
    if (!trimmed && !uploads.length) return

    if (trimmed === '/rewind' || trimmed.startsWith('/rewind ')) {
      rewindDialogOpen.value = true
      return
    }

    const changesStore = useSessionChangesStore()
    const mode = useAgentSettingsStore().permissionMode
    if (changesStore.hasPendingChanges && blocksSendOnPendingReview(mode)) {
      error.value = sendBlockedMessage(mode)
      return
    }

    const workspaceStore = useWorkspaceStore()
    const editorStore = useEditorStore()
    const root = workspaceStore.rootPath ?? undefined
    const ws = root ?? null

    try {
      beginTurn()
      contextStats.value = null
      contextBreakdown.value = null
      changesStore.resetTurn()
      isSending.value = true
      resetTimeline()
      runStartedAt.value = Date.now()
      error.value = null
      agentStore.setError(null)

      if (uploads.length || displayAttachments.length) {
        runStatus.value = '准备附件…'
        recordClientStep('准备附件', `${uploads.length || displayAttachments.length} 个附件`)
      } else {
        runStatus.value = '写入会话…'
        recordClientStep('写入会话')
      }

      const agentSettings = useAgentSettingsStore()
      const modelSettings = useModelSettingsStore()
      const sendModel = modelSettings.resolveSendModel()
      const selection = editorStore.activePath
        ? getEditorSelection(editorStore.activePath)
        : null
      const attachmentPayload = await buildChatAttachments(trimmed, root, selection)
      const allAttachments = [...uploads, ...attachmentPayload.attachments]
      const mergedDisplay = mergeDisplayAttachments(displayAttachments, allAttachments)
      if (uploads.length || displayAttachments.length) {
        markClientStepDone('准备附件')
        runStatus.value = '写入会话…'
        recordClientStep('写入会话')
      }

      const displayText =
        trimmed ||
        (mergedDisplay.length
          ? mergedDisplay.map(a => a.name).join(', ')
          : attachmentSummary(uploads))
      const title = displayText.length > 80 ? `${displayText.slice(0, 77)}...` : displayText
      const persistAttachments = buildPersistAttachmentInputs(mergedDisplay, allAttachments)
      await window.transcriptApi.setTitle(sessionId.value, ws, title)
      const appendResult = await window.transcriptApi.appendUser(
        sessionId.value,
        ws,
        displayText,
        persistAttachments.length ? persistAttachments : undefined,
      )
      const userMessageId = appendResult.messageUuid
      const sendAttachments = applyStoredAttachmentPaths(
        allAttachments,
        persistAttachments,
        appendResult.storedAttachments,
      )
      const displayWithStorePaths = applyStoredPathsToDisplayAttachments(
        mergedDisplay,
        persistAttachments,
        appendResult.storedAttachments,
      )
      await window.transcriptApi.flush(sessionId.value, ws)
      markClientStepDone('写入会话')

      messages.value.push({
        role: 'user',
        content: trimmed || displayText,
        messageUuid: userMessageId,
        attachments: displayWithStorePaths.length ? displayWithStorePaths : undefined,
      })
      streamingBuffer = ''
      streamingText.value = ''
      resetRunState()

      const hasWorkspace = Boolean(root)
      runStatus.value = '发送到 Agent…'
      recordClientStep(
        '发送 chat.send',
        hasWorkspace ? 'Agent 工具循环 · 服务端读 JSONL' : '纯对话 · 无工作区',
      )
      startSendWatchdog()
      startProgressTimer()

      const projectRules = await loadProjectRules(root)
      const gitStatusSummary = await loadGitStatusSummary(root)

      const params: ChatSendParams = {
        message: trimmed || displayText,
        sessionId: sessionId.value,
        workspaceRoot: root,
        openFilePath: editorStore.activePath ?? undefined,
        editorDocuments: editorStore.getOpenDocumentSnapshots(),
        attachments: sendAttachments.length ? sendAttachments : undefined,
        projectRules,
        gitStatusSummary,
        permissionMode: agentSettings.permissionMode,
        userMessageId,
        model: sendModel,
      }

      const request: JsonRpcRequest<ChatSendParams> = {
        jsonrpc: '2.0',
        method: 'chat.send',
        params,
        id: ++requestId,
      }

      socket.send(JSON.stringify(request))
      markClientStepDone('发送 chat.send')
      pushTimeline({
        kind: 'client',
        phase: 'ack',
        label: '等待服务端 ACK',
        status: 'active',
      })
      runStatus.value = '等待 Agent 响应…'
    } catch (e) {
      forceEndTurn()
      isSending.value = false
      clearSendWatchdog()
      error.value = e instanceof Error ? e.message : String(e)
    }
  }

  function cancelGeneration() {
    const agentStore = useAgentStore()
    const socket = agentStore.ws
    // Claude REPL onCancel: forceEnd() + resetLoadingState before server abort
    forceEndTurn()
    streamingBuffer = ''
    streamingText.value = ''
    resetRunState()
    isSending.value = false
    clearSendWatchdog()
    runStatus.value = '已停止生成'

    if (!socket || socket.readyState !== WebSocket.OPEN) {
      return
    }

    const request: JsonRpcRequest = {
      jsonrpc: '2.0',
      method: 'chat.cancel',
      id: ++requestId,
    }
    socket.send(JSON.stringify(request))
  }

  /** Manual recovery when input feels stuck (session dropdown ↻ or devtools). */
  function forceResetChat() {
    forceEndTurn()
    clearSendWatchdog()
    streamingBuffer = ''
    streamingText.value = ''
    resetRunState()
    isSending.value = false
  }

  function clearError() {
    error.value = null
  }

  function replyPermission(allowed: boolean, remember?: boolean) {
    const request = permissionRequest.value
    if (!request) return

    const agentStore = useAgentStore()
    const socket = agentStore.ws
    if (!socket || socket.readyState !== WebSocket.OPEN) {
      permissionRequest.value = null
      return
    }

    if (remember) {
      useAgentSettingsStore().setPermissionMode('auto')
    }

    const params: PermissionReplyParams = {
      requestId: request.requestId,
      allowed,
      remember,
    }
    const rpc: JsonRpcRequest<PermissionReplyParams> = {
      jsonrpc: '2.0',
      method: 'permission.reply',
      params,
      id: ++requestId,
    }
    socket.send(JSON.stringify(rpc))
    permissionRequest.value = null
  }

  function dismissPermissionRequest() {
    if (permissionRequest.value) {
      replyPermission(false)
    }
  }

  async function executeRewind(messageUuid: string, mode: 'both' | 'conversation' | 'code') {
    if (rewindBusy.value) return
    rewindBusy.value = true
    const agentStore = useAgentStore()
    const editorStore = useEditorStore()
    const ws = workspaceRoot() ?? null
    const sid = sessionId.value

    try {
      if (mode === 'code' || mode === 'both') {
        const result = await agentStore.invokeRpc<{
          ok: boolean
          paths?: string[]
        }>('checkpoint.restore', { sessionId: sid, turnId: messageUuid })
        const changesStore = useSessionChangesStore()
        if (result.paths?.length) {
          changesStore.clearPendingForPaths(result.paths)
        } else {
          changesStore.clearAllPending()
        }
        for (const path of result.paths ?? []) {
          if (!path) continue
          try {
            await window.workspaceApi.readFile(path)
            await editorStore.applyAgentFileChange(path)
          } catch {
            await editorStore.closeTab(path, true)
          }
        }
        await useWorkspaceStore().refreshTree()
      }

      if (mode === 'conversation' || mode === 'both') {
        await window.transcriptApi.truncate(sid, ws, messageUuid)
        const idx = messages.value.findIndex(m => m.messageUuid === messageUuid)
        if (idx >= 0) {
          messages.value = messages.value.slice(0, idx + 1)
        }
      }

      rewindDialogOpen.value = false
      error.value = null
    } catch (e) {
      error.value = e instanceof Error ? e.message : String(e)
    } finally {
      rewindBusy.value = false
    }
  }

  function closeRewindDialog() {
    rewindDialogOpen.value = false
  }

  async function reconnect() {
    error.value = null
    await useAgentStore().reconnect()
  }

  async function restart() {
    await useAgentStore().restart()
  }

  function cleanup() {
    unsubscribeEvents?.()
    unsubscribeEvents = null
    useAgentStore().cleanup()
  }

  return {
    messages,
    sessions,
    sessionId,
    streamingText,
    toolActivity,
    runStatus,
    runTimeline,
    runMode,
    runStartedAt,
    isSending,
    error,
    permissionRequest,
    contextStats,
    contextBreakdown,
    rewindDialogOpen,
    rewindBusy,
    init,
    refreshSessions,
    syncCurrentSession,
    onWorkspaceChanged,
    loadSession,
    newSession,
    sendMessage,
    executeRewind,
    closeRewindDialog,
    cancelGeneration,
    forceResetChat,
    clearError,
    replyPermission,
    dismissPermissionRequest,
    reconnect,
    restart,
    cleanup,
  }
})
