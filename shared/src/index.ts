export * from './transcript'
export * from './transcriptBuild'
export * from './transcriptChain'
export * from './transcriptNormalize'
export * from './mediaLimits'
import type { LlmHistoryMessage } from './transcript'

/** JSON-RPC 2.0 request from UI → Agent */
export interface JsonRpcRequest<T = unknown> {
  jsonrpc: '2.0'
  method: string
  params?: T
  id: number | string
}

/** JSON-RPC 2.0 response from Agent → UI */
export interface JsonRpcResponse<T = unknown> {
  jsonrpc: '2.0'
  result?: T
  error?: { code: number; message: string }
  id: number | string | null
}

/** Open editor tab snapshot — lets Agent read unsaved Monaco buffer instead of stale disk. */
export interface EditorDocumentSnapshot {
  path: string
  content: string
  dirty?: boolean
}

export type PermissionMode = 'ask' | 'auto' | 'plan'
/** ask → CC default · auto → CC acceptEdits · plan → CC plan */

export interface ChatAttachment {
  kind: 'file' | 'folder' | 'selection' | 'image' | 'pdf' | 'pdf_reference'
  path: string
  content?: string
  label?: string
  /** image attachments — sent as multimodal LLM content blocks */
  mimeType?: string
  base64?: string
  /** pdf_reference — CC large-PDF @mention */
  pageCount?: number
  fileSize?: number
}

export interface ChatSendParams {
  message: string
  sessionId?: string
  workspaceRoot?: string
  openFilePath?: string
  /** Open text tabs (especially dirty) so read_file uses in-memory content. */
  editorDocuments?: EditorDocumentSnapshot[]
  /** @file / @folder / editor selection injected by composer. */
  attachments?: ChatAttachment[]
  /** Contents of AGENTS.md / .cursorrules read by the client. */
  projectRules?: string
  /** ask = confirm writes; auto = allow writes; plan = read-only tools. */
  permissionMode?: PermissionMode
  /** Override server default model for this message (omit or empty = Auto). */
  model?: string
  /** Transcript user message UUID for fileHistory checkpoints (Claude Code parity). */
  userMessageId?: string
  /** Git porcelain summary injected into system prompt. */
  gitStatusSummary?: string
  /**
   * @deprecated Server reads ~/.agent-desktop/projects/.../session.jsonl (Claude Code parity).
   * Do not send history over WebSocket.
   */
  history?: LlmHistoryMessage[]
}

/** Stop the in-flight chat.send for this WebSocket connection. */
export interface ChatCancelParams {
  /** Reserved for future per-session cancel; currently cancels the active run on this socket. */
  sessionId?: string
}

/** Cursor Ctrl+K inline edit — editor selection + instruction (no chat transcript). */
export interface InlineEditParams {
  requestId: string
  instruction: string
  filePath: string
  relativePath?: string
  language?: string
  selectionText: string
  startLine: number
  startColumn: number
  endLine: number
  endColumn: number
  workspaceRoot?: string
  editorDocuments?: EditorDocumentSnapshot[]
  model?: string
}

export interface PermissionReplyParams {
  requestId: string
  allowed: boolean
  /** When true, client switches to auto mode for this session. */
  remember?: boolean
}

export interface ContextBreakdownCategory {
  id: string
  label: string
  chars: number
  color: string
}

export interface ContextBreakdown {
  totalChars: number
  maxChars: number
  percent: number
  model?: string
  categories: ContextBreakdownCategory[]
}

/** NDJSON stream events: Agent → UI */
export type AgentEvent =
  | { type: 'ready'; port: number }
  | { type: 'text_delta'; delta: string }
  | { type: 'tool_start'; toolCallId: string; name: string; argsJson?: string }
  | { type: 'tool_result'; toolCallId: string; name: string; ok: boolean; output: string }
  | {
      type: 'file_changed'
      path: string
      changeKind: 'modified' | 'created' | 'deleted'
      content?: string
      previousContent?: string
    }
  | {
      type: 'rewind_restored'
      sessionId: string
      paths: string[]
    }
  | {
      type: 'permission_request'
      requestId: string
      toolName: string
      summary: string
      path?: string
    }
  | {
      type: 'agent_step'
      phase: string
      label: string
      /** 1-based agent loop turn (tool round). */
      turn?: number
      /** Extra context, e.g. model name or tool list. */
      detail?: string
      /** stream = no workspace / no tools; agent = full queryLoop with tools. */
      mode?: 'stream' | 'agent'
    }
  | {
      type: 'context_breakdown'
      breakdown: ContextBreakdown
    }
  | {
      type: 'compact_boundary'
      trigger: 'auto' | 'manual'
      summary: string
      usedChars: number
      maxChars: number
    }
  | {
      type: 'subagent_start'
      subagentId: string
      subagentType: string
      description: string
      depth: number
    }
  | {
      type: 'subagent_done'
      subagentId: string
      subagentType: string
      ok: boolean
      summary: string
    }
  | { type: 'inline_edit_delta'; requestId: string; delta: string }
  | { type: 'inline_edit_done'; requestId: string; replacement: string }
  | { type: 'inline_edit_error'; requestId: string; message: string }
  | { type: 'done'; sessionId?: string }
  | { type: 'error'; message: string }

/** Permission dialog payload (subset of AgentEvent). */
export type PermissionRequestEvent = Extract<AgentEvent, { type: 'permission_request' }>

/** IPC: main → renderer */
export interface AgentConnectionInfo {
  port: number
  wsUrl: string
}

/** Child process → main process */
export interface AgentReadyMessage {
  type: 'agent-ready'
  port: number
  /** 完整 WebSocket URL（Spring Boot 含 path，如 /ws） */
  wsUrl?: string
}

export interface ChatSessionSummary {
  id: string
  title: string
  workspace?: string
  updatedAt?: string
}

export interface ChatMessageRecord {
  id?: number
  role: string
  content: string
  tools?: ChatToolRecord[]
  createdAt?: string
}

export interface ChatToolRecord {
  toolCallId: string
  name: string
  status: 'running' | 'done' | 'error'
  summary?: string
  output?: string
  argsJson?: string
}

export type { LlmHistoryMessage }

export function parseNdjsonLine(line: string): AgentEvent | null {
  const trimmed = line.trim()
  if (!trimmed) return null
  try {
    const parsed = JSON.parse(trimmed) as Record<string, unknown>
    if (typeof parsed.type !== 'string') return null
    return parsed as AgentEvent
  } catch {
    return null
  }
}

export function formatNdjson(event: AgentEvent): string {
  return `${JSON.stringify(event)}\n`
}

export function createSessionId(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  return `session-${Date.now()}`
}
