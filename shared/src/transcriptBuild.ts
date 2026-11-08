import type {
  AdTranscriptAttachment,
  ChatToolRecord,
  ClaudeContentBlock,
  ClaudeTranscriptEntry,
} from './transcript'
import { orderEntriesByChain } from './transcriptChain'

export interface UiChatAttachment {
  id: string
  kind: 'image' | 'pdf' | 'text' | 'file' | 'folder'
  name: string
  filePath: string
  source: 'store' | 'workspace'
}

export interface UiChatMessage {
  role: 'user' | 'assistant'
  content: string
  /** Transcript UUID for user messages — used by /rewind. */
  messageUuid?: string
  attachments?: UiChatAttachment[]
  tools?: ChatToolRecord[]
  createdAt?: string
}

function summarizeTool(name: string, output: string): string | undefined {
  if (!output) return undefined
  if (name === 'list_dir') {
    const lines = output.split('\n').filter(l => l.trim()).length
    return lines > 0 ? `${lines} entries` : undefined
  }
  const fileMatch = output.match(/^File:\s*(.+?)(?:\s|\(|$)/m)
  if (fileMatch) return fileMatch[1].trim()
  return undefined
}

function textFromContent(content: string | ClaudeContentBlock[] | undefined): string {
  if (!content) return ''
  if (typeof content === 'string') return content
  return content
    .filter((b): b is { type: 'text'; text: string } => b.type === 'text')
    .map(b => b.text)
    .join('\n')
    .trim()
}

function toolUsesFromAssistant(content: string | ClaudeContentBlock[] | undefined): ChatToolRecord[] {
  if (!content || typeof content === 'string') return []
  return content
    .filter((b): b is Extract<ClaudeContentBlock, { type: 'tool_use' }> => b.type === 'tool_use')
    .map(b => ({
      toolCallId: b.id,
      name: b.name,
      status: 'running' as const,
      argsJson: b.input ? JSON.stringify(b.input) : undefined,
    }))
}

function toolResultsFromUser(content: string | ClaudeContentBlock[] | undefined): ChatToolRecord[] {
  if (!content || typeof content === 'string') return []
  return content
    .filter((b): b is Extract<ClaudeContentBlock, { type: 'tool_result' }> => b.type === 'tool_result')
    .map(b => ({
      toolCallId: b.tool_use_id,
      name: 'tool',
      status: b.is_error ? ('error' as const) : ('done' as const),
      output: b.content,
      summary: summarizeTool('tool', b.content),
    }))
}

/** Build UI from Claude Code JSONL (user/assistant + tool_use/tool_result blocks). */
export function transcriptToUiMessages(entries: ClaudeTranscriptEntry[]): UiChatMessage[] {
  const ordered = orderEntriesByChain(entries)
  const messages: UiChatMessage[] = []
  let pendingTools: ChatToolRecord[] = []

  const flushAssistant = (text: string, timestamp?: string) => {
    const tools = [...pendingTools]
    pendingTools = []
    const trimmed = text.trim()
    if (!trimmed && tools.length === 0) return
    messages.push({
      role: 'assistant',
      content: trimmed || '（无文字输出，请展开工具详情查看分析过程）',
      tools: tools.length > 0 ? tools : undefined,
      createdAt: timestamp,
    })
  }

  for (const entry of ordered) {
    if (entry.type === 'custom-title') continue

    if (entry.type === 'user' && entry.message) {
      const toolResults = toolResultsFromUser(entry.message.content)
      if (toolResults.length > 0) {
        for (const tr of toolResults) {
          const existing = pendingTools.find(t => t.toolCallId === tr.toolCallId)
      if (existing) {
        existing.status = tr.status
        existing.output = tr.output
        existing.summary = tr.summary
        if (existing.name === 'tool' && tr.name !== 'tool') {
          existing.name = tr.name
        }
      } else {
        pendingTools.push(tr)
      }
        }
        continue
      }
      flushAssistant('', entry.timestamp)
      const text = textFromContent(entry.message.content)
      if (text) {
        messages.push({
          role: 'user',
          content: text,
          messageUuid: typeof entry.uuid === 'string' ? entry.uuid : undefined,
          attachments: mapAdAttachments(entry.adAttachments),
          createdAt: entry.timestamp,
        })
      }
      continue
    }

    if (entry.type === 'assistant' && entry.message) {
      const uses = toolUsesFromAssistant(entry.message.content)
      if (uses.length > 0) {
        pendingTools.push(...uses)
        continue
      }
      flushAssistant(textFromContent(entry.message.content), entry.timestamp)
    }
  }

  flushAssistant('', undefined)

  return messages
}

function mapAdAttachments(items: AdTranscriptAttachment[] | undefined): UiChatAttachment[] | undefined {
  if (!items?.length) return undefined
  return items.map(item => ({
    id: item.id,
    kind: item.kind,
    name: item.name,
    filePath: item.path,
    source: item.source,
  }))
}

export {
  SYNTHETIC_TOOL_RESULT_PLACEHOLDER,
  apiTranscriptToOpenAiHistory,
  ensureToolResultPairing,
  normalizeTranscriptForAPI,
  transcriptToLlmHistory,
} from './transcriptNormalize'

export type { ApiTranscriptMessage } from './transcriptNormalize'
