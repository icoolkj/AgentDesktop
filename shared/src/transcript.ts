/** Claude Code JSONL transcript types (SerializedMessage / TranscriptMessage subset). */

export const TRANSCRIPT_VERSION = '0.1.0'

export type ClaudeContentBlock =
  | { type: 'text'; text: string }
  | { type: 'tool_use'; id: string; name: string; input?: Record<string, unknown> }
  | { type: 'tool_result'; tool_use_id: string; content: string; is_error?: boolean }

export type ClaudeTranscriptEntry = {
  type: string
  uuid?: string
  parentUuid?: string | null
  sessionId?: string
  cwd?: string
  timestamp?: string
  version?: string
  customTitle?: string
  message?: {
    id?: string
    role: 'user' | 'assistant'
    content: string | ClaudeContentBlock[]
  }
  /** Agent Desktop — attachment chips shown in chat UI. */
  adAttachments?: AdTranscriptAttachment[]
  subtype?: string
  content?: string
  compactMetadata?: Record<string, unknown>
}

export interface TranscriptSessionSummary {
  sessionId: string
  title: string
  firstPrompt?: string
  cwd?: string
  updatedAt: string
  fileSize: number
  messageCount: number
}

export interface TranscriptLoadResult {
  sessionId: string
  entries: ClaudeTranscriptEntry[]
  title: string
  cwd?: string
}

export interface LlmHistoryMessage {
  role: 'system' | 'user' | 'assistant' | 'tool'
  content?: string
  toolCalls?: Array<{ id: string; name: string; argumentsJson?: string }>
  toolCallId?: string
  toolName?: string
}

export interface ChatToolRecord {
  toolCallId: string
  name: string
  status: 'running' | 'done' | 'error'
  summary?: string
  output?: string
  argsJson?: string
}

/** User message attachment persisted in JSONL (Agent Desktop extension). */
export interface AdTranscriptAttachment {
  id: string
  kind: 'image' | 'pdf' | 'text' | 'file' | 'folder'
  name: string
  path: string
  source: 'store' | 'workspace'
}

/** @deprecated legacy flat entries — kept for migration readers only */
export type TranscriptEntry = ClaudeTranscriptEntry
