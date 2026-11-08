/**
 * Claude Code API normalization pipeline (messages.ts).
 * normalizeMessagesForAPI + ensureToolResultPairing, then OpenAI/CPA wire format.
 *
 * @see claude_code_src-master/src/utils/messages.ts
 * @see claude_code_src-master/src/services/api/claude.ts
 */
import type { ClaudeContentBlock, ClaudeTranscriptEntry, LlmHistoryMessage } from './transcript'
import { orderEntriesByChain } from './transcriptChain'

/** Same string as Claude SYNTHETIC_TOOL_RESULT_PLACEHOLDER (messages.ts:246). */
export const SYNTHETIC_TOOL_RESULT_PLACEHOLDER =
  '[Tool result missing due to internal error]'

export const NO_CONTENT_MESSAGE = '(no content)'

/** Internal user/assistant shape during normalization (Claude TranscriptMessage subset). */
export type ApiTranscriptMessage = {
  type: 'user' | 'assistant'
  uuid?: string
  /** Assistant message.id — parallel tool_use siblings share this. */
  messageId?: string
  content: ClaudeContentBlock[]
  isMeta?: boolean
}

function normalizeContent(content: string | ClaudeContentBlock[] | undefined): ClaudeContentBlock[] {
  if (!content) return []
  if (typeof content === 'string') {
    const t = content.trim()
    return t ? [{ type: 'text', text: t }] : []
  }
  return [...content]
}

function isToolResultMessage(msg: ApiTranscriptMessage): boolean {
  return msg.type === 'user' && msg.content.some(b => b.type === 'tool_result')
}

function mergeAssistantMessages(a: ApiTranscriptMessage, b: ApiTranscriptMessage): ApiTranscriptMessage {
  return {
    ...a,
    content: [...a.content, ...b.content],
  }
}

function mergeUserMessages(a: ApiTranscriptMessage, b: ApiTranscriptMessage): ApiTranscriptMessage {
  return {
    ...a,
    content: [...a.content, ...b.content],
  }
}

function entryToApiMessage(entry: ClaudeTranscriptEntry): ApiTranscriptMessage | null {
  if (entry.type !== 'user' && entry.type !== 'assistant') return null
  if (!entry.message) return null
  const raw = entry.message as { id?: string; role: string; content: string | ClaudeContentBlock[] }
  const content = normalizeContent(raw.content)
  if (entry.type === 'assistant') {
    return {
      type: 'assistant',
      uuid: entry.uuid,
      messageId: raw.id,
      content,
    }
  }
  return {
    type: 'user',
    uuid: entry.uuid,
    content,
  }
}

/**
 * Port of normalizeMessagesForAPI (assistant merge by message.id, consecutive user merge).
 * Operates on Claude content blocks before OpenAI conversion.
 */
export function normalizeTranscriptForAPI(entries: ClaudeTranscriptEntry[]): ApiTranscriptMessage[] {
  const ordered = orderEntriesByChain(entries)
  const result: ApiTranscriptMessage[] = []

  for (const entry of ordered) {
    const apiMsg = entryToApiMessage(entry)
    if (!apiMsg) continue

    if (apiMsg.type === 'user') {
      const last = result[result.length - 1]
      if (last?.type === 'user') {
        result[result.length - 1] = mergeUserMessages(last, apiMsg)
        continue
      }
      result.push(apiMsg)
      continue
    }

    // assistant — merge by message.id walking backward past tool_result users
    let merged = false
    for (let i = result.length - 1; i >= 0; i--) {
      const msg = result[i]!
      if (msg.type !== 'assistant' && !isToolResultMessage(msg)) break
      if (msg.type === 'assistant') {
        if (msg.messageId && msg.messageId === apiMsg.messageId) {
          result[i] = mergeAssistantMessages(msg, apiMsg)
          merged = true
          break
        }
        continue
      }
    }
    if (!merged) result.push(apiMsg)
  }

  return result
}

/**
 * Port of ensureToolResultPairing (messages.ts:5133).
 * Repairs forward (synthetic tool_result) and reverse (strip orphans) mismatches.
 */
export function ensureToolResultPairing(
  messages: ApiTranscriptMessage[],
): ApiTranscriptMessage[] {
  const result: ApiTranscriptMessage[] = []
  const allSeenToolUseIds = new Set<string>()

  for (let i = 0; i < messages.length; i++) {
    const msg = messages[i]!

    if (msg.type !== 'assistant') {
      if (isToolResultMessage(msg) && result[result.length - 1]?.type !== 'assistant') {
        const stripped = msg.content.filter(b => b.type !== 'tool_result')
        if (stripped.length !== msg.content.length) {
          const content =
            stripped.length > 0
              ? stripped
              : result.length === 0
                ? [{ type: 'text' as const, text: '[Orphaned tool result removed due to conversation resume]' }]
                : null
          if (content) {
            result.push({ ...msg, content })
          }
          continue
        }
      }
      result.push(msg)
      continue
    }

    const seenInMessage = new Set<string>()
    let finalContent = msg.content.filter(block => {
      if (block.type !== 'tool_use') return true
      if (allSeenToolUseIds.has(block.id)) return false
      allSeenToolUseIds.add(block.id)
      seenInMessage.add(block.id)
      return true
    })

    if (finalContent.length === 0) {
      finalContent = [{ type: 'text', text: '[Tool use interrupted]' }]
    }

    const assistantMsg: ApiTranscriptMessage = { ...msg, content: finalContent }
    result.push(assistantMsg)

    const toolUseIds = [...seenInMessage]
    const nextMsg = messages[i + 1]

    const existingToolResultIds = new Set<string>()
    let hasDuplicateToolResults = false

    if (nextMsg?.type === 'user') {
      for (const block of nextMsg.content) {
        if (block.type === 'tool_result') {
          if (existingToolResultIds.has(block.tool_use_id)) {
            hasDuplicateToolResults = true
          }
          existingToolResultIds.add(block.tool_use_id)
        }
      }
    }

    const toolUseIdSet = new Set(toolUseIds)
    const missingIds = toolUseIds.filter(id => !existingToolResultIds.has(id))
    const orphanedIds = [...existingToolResultIds].filter(id => !toolUseIdSet.has(id))

    if (missingIds.length === 0 && orphanedIds.length === 0 && !hasDuplicateToolResults) {
      continue
    }

    const syntheticBlocks: ClaudeContentBlock[] = missingIds.map(id => ({
      type: 'tool_result',
      tool_use_id: id,
      content: SYNTHETIC_TOOL_RESULT_PLACEHOLDER,
      is_error: true,
    }))

    if (nextMsg?.type === 'user') {
      let content = [...nextMsg.content]

      if (orphanedIds.length > 0 || hasDuplicateToolResults) {
        const orphanedSet = new Set(orphanedIds)
        const seenTrIds = new Set<string>()
        content = content.filter(block => {
          if (block.type !== 'tool_result') return true
          if (orphanedSet.has(block.tool_use_id)) return false
          if (seenTrIds.has(block.tool_use_id)) return false
          seenTrIds.add(block.tool_use_id)
          return true
        })
      }

      const patchedContent = [...syntheticBlocks, ...content]
      if (patchedContent.length > 0) {
        result.push({ ...nextMsg, content: patchedContent, isMeta: nextMsg.isMeta ?? missingIds.length > 0 })
        i++
      } else {
        i++
        result.push({
          type: 'user',
          content: [{ type: 'text', text: NO_CONTENT_MESSAGE }],
          isMeta: true,
        })
      }
    } else if (syntheticBlocks.length > 0) {
      result.push({
        type: 'user',
        content: syntheticBlocks,
        isMeta: true,
      })
    }
  }

  return result
}

/** Convert normalized Claude blocks → OpenAI-compatible LlmHistoryMessage[]. */
export function apiTranscriptToOpenAiHistory(messages: ApiTranscriptMessage[]): LlmHistoryMessage[] {
  const out: LlmHistoryMessage[] = []

  for (const msg of messages) {
    if (msg.type === 'user') {
      const toolResults = msg.content.filter(
        (b): b is Extract<ClaudeContentBlock, { type: 'tool_result' }> => b.type === 'tool_result',
      )
      const text = msg.content
        .filter((b): b is Extract<ClaudeContentBlock, { type: 'text' }> => b.type === 'text')
        .map(b => b.text)
        .join('\n')
        .trim()

      for (const tr of toolResults) {
        out.push({
          role: 'tool',
          toolCallId: tr.tool_use_id,
          toolName: 'tool',
          content: tr.content,
        })
      }
      if (text) {
        out.push({ role: 'user', content: text })
      }
      continue
    }

    if (msg.type === 'assistant') {
      const toolUses = msg.content.filter(
        (b): b is Extract<ClaudeContentBlock, { type: 'tool_use' }> => b.type === 'tool_use',
      )
      const text = msg.content
        .filter((b): b is Extract<ClaudeContentBlock, { type: 'text' }> => b.type === 'text')
        .map(b => b.text)
        .join('\n')
        .trim()

      if (toolUses.length > 0) {
        out.push({
          role: 'assistant',
          content: text,
          toolCalls: toolUses.map(tu => ({
            id: tu.id,
            name: tu.name,
            argumentsJson: JSON.stringify(tu.input ?? {}),
          })),
        })
      } else if (text) {
        out.push({ role: 'assistant', content: text })
      }
    }
  }

  return out
}

function truncatePreservingToolRounds(messages: LlmHistoryMessage[], maxMessages: number): LlmHistoryMessage[] {
  if (messages.length <= maxMessages) return messages

  let start = messages.length - maxMessages
  while (start > 0 && messages[start]?.role === 'tool') {
    start--
  }
  while (start > 0) {
    const m = messages[start]
    if (m?.role === 'assistant' && m.toolCalls && m.toolCalls.length > 0) break
    if (m?.role !== 'tool') break
    start--
  }

  let slice = messages.slice(Math.max(0, start))
  while (slice.length > 0 && slice[0]?.role === 'tool') {
    slice = slice.slice(1)
  }

  if (slice.length <= maxMessages) return slice
  return truncatePreservingToolRounds(slice, maxMessages)
}

/**
 * Full Claude Code resume → API pipeline for OpenAI/CPA gateway.
 */
export function transcriptToLlmHistory(entries: ClaudeTranscriptEntry[], maxMessages = 48): LlmHistoryMessage[] {
  let normalized = normalizeTranscriptForAPI(entries)
  normalized = ensureToolResultPairing(normalized)
  let out = apiTranscriptToOpenAiHistory(normalized)
  if (out.length > maxMessages) {
    out = truncatePreservingToolRounds(out, maxMessages)
  }
  return out
}
