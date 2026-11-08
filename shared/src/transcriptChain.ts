/**
 * Ported from Claude Code sessionStorage.ts — parentUuid chain rebuild on load.
 * @see claude_code_src-master/src/utils/sessionStorage.ts buildConversationChain
 */
import type { ClaudeTranscriptEntry } from './transcript'

export type ChainMessage = ClaudeTranscriptEntry & { uuid: string; timestamp: string }

export function isTranscriptMessage(entry: ClaudeTranscriptEntry): boolean {
  return (
    entry.type === 'user' ||
    entry.type === 'assistant' ||
    entry.type === 'attachment' ||
    entry.type === 'system'
  )
}

function asChainMessage(entry: ClaudeTranscriptEntry): ChainMessage | null {
  if (!isTranscriptMessage(entry)) return null
  if (typeof entry.uuid !== 'string' || typeof entry.timestamp !== 'string') return null
  return entry as ChainMessage
}

export function findLatestMessage(
  messages: Iterable<ChainMessage>,
  predicate: (m: ChainMessage) => boolean,
): ChainMessage | undefined {
  let latest: ChainMessage | undefined
  let maxTime = -Infinity
  for (const m of messages) {
    if (!predicate(m)) continue
    const t = Date.parse(m.timestamp)
    if (t > maxTime) {
      maxTime = t
      latest = m
    }
  }
  return latest
}

/** Terminal leaves: user/assistant nodes with no user/assistant children. */
export function computeLeafUuids(messages: Map<string, ChainMessage>): Set<string> {
  const parentUuids = new Set<string>()
  for (const msg of messages.values()) {
    if (msg.parentUuid) parentUuids.add(msg.parentUuid)
  }
  const terminalMessages = [...messages.values()].filter(msg => !parentUuids.has(msg.uuid))

  const leafUuids = new Set<string>()
  for (const terminal of terminalMessages) {
    const seen = new Set<string>()
    let current: ChainMessage | undefined = terminal
    while (current) {
      if (seen.has(current.uuid)) break
      seen.add(current.uuid)
      if (current.type === 'user' || current.type === 'assistant') {
        leafUuids.add(current.uuid)
        break
      }
      current = current.parentUuid ? messages.get(current.parentUuid) : undefined
    }
  }
  return leafUuids
}

/**
 * Recover sibling assistant blocks and tool_results orphaned by single-parent walk.
 * Ported from recoverOrphanedParallelToolResults.
 */
function recoverOrphanedParallelToolResults(
  messages: Map<string, ChainMessage>,
  chain: ChainMessage[],
  seen: Set<string>,
): ChainMessage[] {
  const chainAssistants = chain.filter(m => m.type === 'assistant')
  if (chainAssistants.length === 0) return chain

  const anchorByMsgId = new Map<string, ChainMessage>()
  for (const a of chainAssistants) {
    const msgId = (a.message as { id?: string } | undefined)?.id
    if (msgId) anchorByMsgId.set(msgId, a)
  }

  const siblingsByMsgId = new Map<string, ChainMessage[]>()
  const toolResultsByAsst = new Map<string, ChainMessage[]>()
  for (const m of messages.values()) {
    const msgId = (m.message as { id?: string } | undefined)?.id
    if (m.type === 'assistant' && msgId) {
      const group = siblingsByMsgId.get(msgId)
      if (group) group.push(m)
      else siblingsByMsgId.set(msgId, [m])
    } else if (
      m.type === 'user' &&
      m.parentUuid &&
      Array.isArray(m.message?.content) &&
      m.message.content.some(
        (b: { type?: string }) => b.type === 'tool_result',
      )
    ) {
      const group = toolResultsByAsst.get(m.parentUuid)
      if (group) group.push(m)
      else toolResultsByAsst.set(m.parentUuid, [m])
    }
  }

  const processedGroups = new Set<string>()
  const inserts = new Map<string, ChainMessage[]>()
  for (const asst of chainAssistants) {
    const msgId = (asst.message as { id?: string } | undefined)?.id
    if (!msgId || processedGroups.has(msgId)) continue
    processedGroups.add(msgId)

    const group = siblingsByMsgId.get(msgId) ?? [asst]
    const orphanedSiblings = group.filter(s => !seen.has(s.uuid))
    const orphanedTRs: ChainMessage[] = []
    for (const member of group) {
      const trs = toolResultsByAsst.get(member.uuid)
      if (!trs) continue
      for (const tr of trs) {
        if (!seen.has(tr.uuid)) orphanedTRs.push(tr)
      }
    }
    if (orphanedSiblings.length === 0 && orphanedTRs.length === 0) continue

    orphanedSiblings.sort((a, b) => a.timestamp.localeCompare(b.timestamp))
    orphanedTRs.sort((a, b) => a.timestamp.localeCompare(b.timestamp))

    const anchor = anchorByMsgId.get(msgId)!
    const recovered = [...orphanedSiblings, ...orphanedTRs]
    for (const r of recovered) seen.add(r.uuid)
    inserts.set(anchor.uuid, recovered)
  }

  if (inserts.size === 0) return chain
  const result: ChainMessage[] = []
  for (const m of chain) {
    result.push(m)
    const toInsert = inserts.get(m.uuid)
    if (toInsert) result.push(...toInsert)
  }
  return result
}

/** Walk parentUuid from leaf to root, reverse to chronological order. */
export function buildConversationChain(
  messages: Map<string, ChainMessage>,
  leafMessage: ChainMessage,
): ChainMessage[] {
  const transcript: ChainMessage[] = []
  const seen = new Set<string>()
  let current: ChainMessage | undefined = leafMessage
  while (current) {
    if (seen.has(current.uuid)) break
    seen.add(current.uuid)
    transcript.push(current)
    current = current.parentUuid ? messages.get(current.parentUuid) : undefined
  }
  transcript.reverse()
  return recoverOrphanedParallelToolResults(messages, transcript, seen)
}

/** Parse JSONL lines into chain-participant map (Claude loadTranscriptFile first pass). */
export function entriesToMessageMap(entries: ClaudeTranscriptEntry[]): Map<string, ChainMessage> {
  const messages = new Map<string, ChainMessage>()
  for (const entry of entries) {
    const msg = asChainMessage(entry)
    if (msg) messages.set(msg.uuid, msg)
  }
  return messages
}

/**
 * Order raw JSONL entries by parentUuid chain (Claude --resume path).
 * Metadata lines (custom-title, etc.) are appended after the chain in file order.
 */
export function orderEntriesByChain(entries: ClaudeTranscriptEntry[]): ClaudeTranscriptEntry[] {
  const metadata: ClaudeTranscriptEntry[] = []
  const chainEntries: ClaudeTranscriptEntry[] = []
  for (const e of entries) {
    if (isTranscriptMessage(e)) chainEntries.push(e)
    else metadata.push(e)
  }

  const messages = entriesToMessageMap(chainEntries)
  if (messages.size === 0) return entries

  const leafUuids = computeLeafUuids(messages)
  const mostRecentLeaf = findLatestMessage(messages.values(), m => leafUuids.has(m.uuid))
  if (!mostRecentLeaf) return entries

  const chain = buildConversationChain(messages, mostRecentLeaf)
  const chainIds = new Set(chain.map(m => m.uuid))
  const trailingMeta = metadata.filter(
    m => m.type === 'custom-title' || m.type === 'summary' || m.type === 'tag',
  )
  const orphanChain = chainEntries.filter(
    e => typeof e.uuid === 'string' && !chainIds.has(e.uuid),
  )
  return [...chain, ...orphanChain, ...trailingMeta]
}
