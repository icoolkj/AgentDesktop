/**
 * Ported from Claude Code loadTranscriptFile (sessionStorage.ts).
 * @see claude_code_src-master/src/utils/sessionStorage.ts
 */
import { readFile, stat } from 'node:fs/promises'
import {
  buildConversationChain,
  computeLeafUuids,
  entriesToMessageMap,
  findLatestMessage,
  isTranscriptMessage,
  type ChainMessage,
} from '@agent-desktop/shared-types'
import type { ClaudeTranscriptEntry } from '@agent-desktop/shared-types'
import { walkChainBeforeParse } from './walkChainBeforeParse'
import { readTranscriptForLoad, SKIP_PRECOMPACT_THRESHOLD } from './readTranscriptForLoad'
import { scanPreBoundaryMetadata } from './scanPreBoundaryMetadata'

type LegacyProgressEntry = {
  type: 'progress'
  uuid: string
  parentUuid: string | null
}

function isLegacyProgressEntry(entry: unknown): entry is LegacyProgressEntry {
  return (
    typeof entry === 'object' &&
    entry !== null &&
    'type' in entry &&
    (entry as { type: string }).type === 'progress' &&
    'uuid' in entry &&
    typeof (entry as { uuid: unknown }).uuid === 'string'
  )
}

function parseJsonlBuffer(buf: Buffer): ClaudeTranscriptEntry[] {
  const entries: ClaudeTranscriptEntry[] = []
  for (const line of buf.toString('utf8').split('\n')) {
    const trimmed = line.trim()
    if (!trimmed) continue
    try {
      entries.push(JSON.parse(trimmed) as ClaudeTranscriptEntry)
    } catch {
      // skip malformed
    }
  }
  return entries
}

export type LoadTranscriptResult = {
  chainEntries: ClaudeTranscriptEntry[]
  allEntries: ClaudeTranscriptEntry[]
  customTitle?: string
  lastPrompt?: string
}

function applyProgressBridge(allEntries: ClaudeTranscriptEntry[]): Map<string, ChainMessage> {
  const messages = entriesToMessageMap(allEntries)
  const progressBridge = new Map<string, string | null>()

  for (const entry of allEntries) {
    if (isLegacyProgressEntry(entry)) {
      const parent = entry.parentUuid
      progressBridge.set(
        entry.uuid,
        parent && progressBridge.has(parent) ? (progressBridge.get(parent) ?? null) : parent,
      )
      continue
    }
    if (
      isTranscriptMessage(entry) &&
      entry.uuid &&
      entry.parentUuid &&
      progressBridge.has(entry.parentUuid)
    ) {
      entry.parentUuid = progressBridge.get(entry.parentUuid) ?? null
      const msg = messages.get(entry.uuid)
      if (msg) msg.parentUuid = entry.parentUuid
    }
  }
  return messages
}

function extractMetadata(allEntries: ClaudeTranscriptEntry[]): {
  customTitle?: string
  lastPrompt?: string
} {
  let customTitle: string | undefined
  let lastPrompt: string | undefined
  for (const entry of allEntries) {
    if (entry.type === 'custom-title' && entry.customTitle) {
      customTitle = entry.customTitle
    } else if (entry.type === 'last-prompt') {
      const lp = (entry as Record<string, unknown>).lastPrompt
      if (typeof lp === 'string') lastPrompt = lp
    }
  }
  return { customTitle, lastPrompt }
}

/** Load transcript file Claude-style: compact-boundary skip, progress bridge, parentUuid chain. */
export async function loadTranscriptFile(filePath: string): Promise<LoadTranscriptResult> {
  const { size } = await stat(filePath)
  let buf: Buffer
  let hasPreservedSegment = false
  let metadataLines: string[] | null = null

  if (size > SKIP_PRECOMPACT_THRESHOLD) {
    const scan = await readTranscriptForLoad(filePath, size)
    buf = scan.postBoundaryBuf
    hasPreservedSegment = scan.hasPreservedSegment
    if (scan.boundaryStartOffset > 0) {
      metadataLines = await scanPreBoundaryMetadata(filePath, scan.boundaryStartOffset)
    }
  } else {
    buf = await readFile(filePath)
  }

  if (!hasPreservedSegment && buf.length > SKIP_PRECOMPACT_THRESHOLD) {
    buf = walkChainBeforeParse(buf)
  }

  if (metadataLines && metadataLines.length > 0) {
    const metaBuf = Buffer.from(`${metadataLines.join('\n')}\n`)
    buf = Buffer.concat([metaBuf, buf])
  }

  const allEntries = parseJsonlBuffer(buf)
  const { customTitle, lastPrompt } = extractMetadata(allEntries)
  const messages = applyProgressBridge(allEntries)

  if (messages.size === 0) {
    return { chainEntries: allEntries, allEntries, customTitle, lastPrompt }
  }

  const leafUuids = computeLeafUuids(messages)
  const mostRecentLeaf = findLatestMessage(messages.values(), m => leafUuids.has(m.uuid))
  if (!mostRecentLeaf) {
    return { chainEntries: allEntries, allEntries, customTitle, lastPrompt }
  }

  const chain = buildConversationChain(messages, mostRecentLeaf)
  const chainIds = new Set(chain.map(m => m.uuid))
  const trailingMeta = allEntries.filter(
    e =>
      e.type === 'custom-title' ||
      e.type === 'last-prompt' ||
      e.type === 'tag' ||
      e.type === 'summary',
  )
  const orphanChain = allEntries.filter(
    e => isTranscriptMessage(e) && typeof e.uuid === 'string' && !chainIds.has(e.uuid),
  )

  return {
    chainEntries: [...chain, ...orphanChain, ...trailingMeta],
    allEntries,
    customTitle,
    lastPrompt,
  }
}

export function parseTranscriptLines(text: string): ClaudeTranscriptEntry[] {
  return parseJsonlBuffer(Buffer.from(text, 'utf8'))
}
