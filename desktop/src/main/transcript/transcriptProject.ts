/**
 * Claude Code Project.insertMessageChain + materializeSessionFile + reAppendSessionMetadata.
 * @see claude_code_src-master/src/utils/sessionStorage.ts
 */
import { appendFileSync, mkdirSync } from 'node:fs'
import { appendFile, mkdir, writeFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { randomUUID } from 'node:crypto'
import {
  buildConversationChain,
  entriesToMessageMap,
  isTranscriptMessage,
} from '@agent-desktop/shared-types'
import { loadTranscriptFile } from './transcriptLoad'
import {
  extractLastJsonStringField,
  getProjectDir,
  normalizeProjectPath,
  projectPathsMatch,
  type SessionFileResolution,
} from './claudeSessionPortable'
import { getGitBranch } from './gitBranch'
import { readFileTailSync } from './readFileTailSync'

export const TRANSCRIPT_VERSION = '0.1.0'

type ContentBlock =
  | { type: 'text'; text: string }
  | { type: 'tool_use'; id: string; name: string; input: Record<string, unknown> }
  | { type: 'tool_result'; tool_use_id: string; content: string; is_error?: boolean }

export type ClaudeTranscriptEntry = Record<string, unknown>

type SessionState = {
  sessionFile: string | null
  /** Storage dir under ~/.agent-desktop/projects/…; null = derive from cwd (CC sessionProjectDir). */
  sessionProjectDir: string | null
  pendingEntries: ClaudeTranscriptEntry[]
  parentUuid: string | null
  writeChain: Promise<void>
  /** User message uuid before current tool round (parallel tool_use parent). */
  toolTurnUserUuid: string | null
  /** Shared message.id for parallel tool_use siblings. */
  toolTurnMessageId: string | null
  toolCallToAssistantUuid: Map<string, string>
  currentSessionTitle: string | undefined
  currentSessionLastPrompt: string | undefined
  gitBranch: string | undefined
}

const sessions = new Map<string, SessionState>()

function sessionKey(sessionId: string, cwd: string): string {
  return `${sessionId}::${cwd}`
}

function getState(sessionId: string, cwd: string): SessionState {
  const key = sessionKey(sessionId, cwd)
  let state = sessions.get(key)
  if (!state) {
    state = {
      sessionFile: null,
      sessionProjectDir: null,
      pendingEntries: [],
      parentUuid: null,
      writeChain: Promise.resolve(),
      toolTurnUserUuid: null,
      toolTurnMessageId: null,
      toolCallToAssistantUuid: new Map(),
      currentSessionTitle: undefined,
      currentSessionLastPrompt: undefined,
      gitBranch: undefined,
    }
    sessions.set(key, state)
  }
  return state
}

function queueWrite(state: SessionState, fn: () => Promise<void>): Promise<void> {
  state.writeChain = state.writeChain.then(fn).catch(() => {})
  return state.writeChain
}

async function appendEntryToFile(fullPath: string, entry: ClaudeTranscriptEntry): Promise<void> {
  const line = `${JSON.stringify(entry)}\n`
  try {
    await appendFile(fullPath, line, 'utf8')
  } catch {
    await mkdir(dirname(fullPath), { recursive: true })
    await appendFile(fullPath, line, 'utf8')
  }
}

/** Sync append for reAppendSessionMetadata (Claude appendEntryToFile). */
function appendEntryToFileSync(fullPath: string, entry: ClaudeTranscriptEntry): void {
  const line = `${JSON.stringify(entry)}\n`
  try {
    appendFileSync(fullPath, line, 'utf8')
  } catch {
    mkdirSync(dirname(fullPath), { recursive: true })
    appendFileSync(fullPath, line, 'utf8')
  }
}

/** Claude reAppendSessionMetadata — tail refresh + EOF metadata re-append. */
function reAppendSessionMetadata(sessionId: string, state: SessionState): void {
  if (!state.sessionFile) return

  const tail = readFileTailSync(state.sessionFile)
  const tailLines = tail.split('\n')

  let titleLine: string | undefined
  for (let i = tailLines.length - 1; i >= 0; i--) {
    const line = tailLines[i]
    if (line?.startsWith('{"type":"custom-title"')) {
      titleLine = line
      break
    }
  }
  if (titleLine) {
    const tailTitle = extractLastJsonStringField(titleLine, 'customTitle')
    if (tailTitle !== undefined) {
      state.currentSessionTitle = tailTitle || undefined
    }
  }

  if (state.currentSessionLastPrompt) {
    appendEntryToFileSync(state.sessionFile, {
      type: 'last-prompt',
      lastPrompt: state.currentSessionLastPrompt,
      sessionId,
    })
  }
  if (state.currentSessionTitle) {
    appendEntryToFileSync(state.sessionFile, {
      type: 'custom-title',
      customTitle: state.currentSessionTitle,
      sessionId,
    })
  }
}

/** CC getTranscriptPath — sessionProjectDir ?? getProjectDir(cwd). */
function getTranscriptPath(sessionId: string, cwd: string, state: SessionState): string {
  const projectDir = state.sessionProjectDir ?? getProjectDir(cwd)
  return join(projectDir, `${sessionId}.jsonl`)
}

function deriveSessionProjectDir(filePath: string, workspaceCwd: string): string | null {
  const storageDir = dirname(filePath)
  const defaultDir = getProjectDir(workspaceCwd)
  return normalizeProjectPath(storageDir) === normalizeProjectPath(defaultDir) ? null : storageDir
}

async function materializeSessionFile(sessionId: string, cwd: string, state: SessionState): Promise<void> {
  if (state.sessionFile) return
  const fullPath = getTranscriptPath(sessionId, cwd, state)
  await mkdir(dirname(fullPath), { recursive: true })
  state.sessionFile = fullPath

  if (state.gitBranch === undefined) {
    state.gitBranch = await getGitBranch(cwd)
  }

  reAppendSessionMetadata(sessionId, state)

  const buffered = state.pendingEntries
  state.pendingEntries = []
  for (const entry of buffered) {
    await appendEntryToFile(fullPath, entry)
  }
}

function baseFields(
  sessionId: string,
  cwd: string,
  parentUuid: string | null,
  gitBranch?: string,
): ClaudeTranscriptEntry {
  return {
    parentUuid,
    sessionId,
    cwd,
    version: TRANSCRIPT_VERSION,
    timestamp: new Date().toISOString(),
    userType: 'external',
    entrypoint: 'agent-desktop',
    ...(gitBranch ? { gitBranch } : {}),
  }
}

function isChainParticipant(entry: ClaudeTranscriptEntry): boolean {
  const t = entry.type
  return t === 'user' || t === 'assistant'
}

async function appendEntry(
  sessionId: string,
  cwd: string,
  entry: ClaudeTranscriptEntry,
  state: SessionState,
): Promise<void> {
  const needsMaterialize =
    state.sessionFile === null && (entry.type === 'user' || entry.type === 'assistant')
  if (needsMaterialize) {
    await materializeSessionFile(sessionId, cwd, state)
  } else if (state.sessionFile === null) {
    state.pendingEntries.push(entry)
    return
  }
  await appendEntryToFile(state.sessionFile!, entry)
  if (isChainParticipant(entry) && typeof entry.uuid === 'string') {
    state.parentUuid = entry.uuid
  }
}

function cacheLastPrompt(text: string, state: SessionState): void {
  const flat = text.replace(/\n/g, ' ').trim()
  if (!flat) return
  state.currentSessionLastPrompt =
    flat.length > 200 ? `${flat.slice(0, 200).trim()}\u2026` : flat
}

function clearToolTurn(state: SessionState): void {
  state.toolTurnUserUuid = null
  state.toolTurnMessageId = null
  state.toolCallToAssistantUuid.clear()
}

export async function recordUserMessage(
  sessionId: string,
  cwd: string,
  text: string,
  attachmentInputs?: import('./attachmentStorage').PersistUserAttachmentInput[],
): Promise<import('./attachmentStorage').AppendUserResult> {
  const state = getState(sessionId, cwd)
  const uuid = randomUUID()
  let storedAttachments: import('./attachmentStorage').AppendUserResult['storedAttachments']
  await queueWrite(state, async () => {
    clearToolTurn(state)
    cacheLastPrompt(text, state)
    if (state.gitBranch === undefined) {
      state.gitBranch = await getGitBranch(cwd)
    }
    if (attachmentInputs?.length) {
      const { persistUserAttachments } = await import('./attachmentStorage')
      storedAttachments = await persistUserAttachments(sessionId, cwd, uuid, attachmentInputs)
    }
    const entry: ClaudeTranscriptEntry = {
      ...baseFields(sessionId, cwd, state.parentUuid, state.gitBranch),
      type: 'user',
      uuid,
      message: { role: 'user', content: text },
      ...(storedAttachments?.length ? { adAttachments: storedAttachments } : {}),
    }
    await appendEntry(sessionId, cwd, entry, state)
    state.toolTurnUserUuid = uuid
  })
  return { messageUuid: uuid, storedAttachments }
}

/** Claude Code SystemCompactBoundaryMessage — written after autocompact / manual /compact. */
export async function recordCompactBoundary(
  sessionId: string,
  cwd: string,
  input: {
    trigger: 'auto' | 'manual'
    usedChars: number
    maxChars: number
    summary?: string
  },
): Promise<void> {
  const state = getState(sessionId, cwd)
  await queueWrite(state, async () => {
    if (state.gitBranch === undefined) {
      state.gitBranch = await getGitBranch(cwd)
    }
    const entry: ClaudeTranscriptEntry = {
      ...baseFields(sessionId, cwd, state.parentUuid, state.gitBranch),
      type: 'system',
      subtype: 'compact_boundary',
      uuid: randomUUID(),
      content: 'Conversation compacted',
      compactMetadata: {
        trigger: input.trigger,
        preChars: input.usedChars,
        maxChars: input.maxChars,
        ...(input.summary ? { summary: input.summary.slice(0, 4000) } : {}),
      },
    }
    await appendEntry(sessionId, cwd, entry, state)
  })
}

export async function recordAssistantToolUses(
  sessionId: string,
  cwd: string,
  tools: Array<{ toolCallId: string; name: string; argsJson?: string }>,
): Promise<void> {
  if (tools.length === 0) return
  const state = getState(sessionId, cwd)
  await queueWrite(state, async () => {
    if (state.gitBranch === undefined) {
      state.gitBranch = await getGitBranch(cwd)
    }
    const messageId = state.toolTurnMessageId ?? randomUUID()
    state.toolTurnMessageId = messageId
    const parentForTools = state.toolTurnUserUuid ?? state.parentUuid

    for (const t of tools) {
      const uuid = randomUUID()
      const content: ContentBlock[] = [
        {
          type: 'tool_use',
          id: t.toolCallId,
          name: t.name,
          input: parseToolInput(t.argsJson),
        },
      ]
      const entry: ClaudeTranscriptEntry = {
        ...baseFields(sessionId, cwd, parentForTools, state.gitBranch),
        type: 'assistant',
        uuid,
        message: { id: messageId, role: 'assistant', content },
      }
      await appendEntry(sessionId, cwd, entry, state)
      state.toolCallToAssistantUuid.set(t.toolCallId, uuid)
    }
  })
}

export async function recordToolResults(
  sessionId: string,
  cwd: string,
  results: Array<{ toolCallId: string; name: string; ok: boolean; output: string }>,
): Promise<void> {
  if (results.length === 0) return
  const state = getState(sessionId, cwd)
  await queueWrite(state, async () => {
    for (const r of results) {
      const uuid = randomUUID()
      const sourceToolAssistantUUID = state.toolCallToAssistantUuid.get(r.toolCallId)
      const parentUuid = sourceToolAssistantUUID ?? state.parentUuid
      const content: ContentBlock[] = [
        {
          type: 'tool_result',
          tool_use_id: r.toolCallId,
          content: r.output,
          is_error: !r.ok,
        },
      ]
      const entry: ClaudeTranscriptEntry = {
        ...baseFields(sessionId, cwd, parentUuid, state.gitBranch),
        type: 'user',
        uuid,
        sourceToolAssistantUUID,
        message: { role: 'user', content },
      }
      await appendEntry(sessionId, cwd, entry, state)
    }
  })
}

export async function recordAssistantText(sessionId: string, cwd: string, text: string): Promise<void> {
  const trimmed = text.trim()
  if (!trimmed) return
  const state = getState(sessionId, cwd)
  await queueWrite(state, async () => {
    clearToolTurn(state)
    const uuid = randomUUID()
    const entry: ClaudeTranscriptEntry = {
      ...baseFields(sessionId, cwd, state.parentUuid, state.gitBranch),
      type: 'assistant',
      uuid,
      message: { role: 'assistant', content: [{ type: 'text', text: trimmed }] },
    }
    await appendEntry(sessionId, cwd, entry, state)
  })
}

export async function recordCustomTitle(
  sessionId: string,
  cwd: string,
  customTitle: string,
  filePath?: string,
): Promise<void> {
  const state = getState(sessionId, cwd)
  state.currentSessionTitle = customTitle
  await queueWrite(state, async () => {
    const resolved = filePath ?? state.sessionFile ?? getTranscriptPath(sessionId, cwd, state)
    if (!state.sessionFile) {
      await mkdir(dirname(resolved), { recursive: true })
      state.sessionFile = resolved
    }
    await appendEntryToFile(resolved, { type: 'custom-title', customTitle, sessionId })
  })
}

function parseToolInput(argsJson?: string): Record<string, unknown> {
  if (!argsJson?.trim()) return {}
  try {
    return JSON.parse(argsJson) as Record<string, unknown>
  } catch {
    return { raw: argsJson }
  }
}

export function bindSessionFile(resolution: SessionFileResolution, sessionId: string, cwd: string): void {
  if (resolution.projectPath && !projectPathsMatch(resolution.projectPath, cwd)) {
    return
  }
  const state = getState(sessionId, cwd)
  state.sessionFile = resolution.filePath
  state.sessionProjectDir = deriveSessionProjectDir(resolution.filePath, cwd)
}

/**
 * CC switchSession — atomically bind sessionId to a transcript storage project dir.
 * Pass null when the session lives under the current workspace project dir.
 */
export function switchTranscriptSession(
  sessionId: string,
  cwd: string,
  sessionProjectDir: string | null,
): void {
  const state = getState(sessionId, cwd)
  state.sessionProjectDir = sessionProjectDir
  if (state.sessionFile) {
    const expected = getTranscriptPath(sessionId, cwd, state)
    if (normalizeProjectPath(state.sessionFile) !== normalizeProjectPath(expected)) {
      state.sessionFile = null
      state.pendingEntries = []
      state.parentUuid = null
      clearToolTurn(state)
    }
  }
}

export function restoreChainFromEntries(
  sessionId: string,
  cwd: string,
  entries: ClaudeTranscriptEntry[],
): void {
  const state = getState(sessionId, cwd)
  for (let i = entries.length - 1; i >= 0; i--) {
    const entry = entries[i]
    if ((entry.type === 'user' || entry.type === 'assistant') && typeof entry.uuid === 'string') {
      state.parentUuid = entry.uuid
      break
    }
  }
  for (const entry of entries) {
    if (entry.type === 'custom-title' && typeof entry.customTitle === 'string') {
      state.currentSessionTitle = entry.customTitle
    }
  }
}

export async function truncateTranscriptToMessage(
  sessionId: string,
  cwd: string,
  messageUuid: string,
): Promise<void> {
  const state = getState(sessionId, cwd)
  if (!state.sessionFile) {
    throw new Error('Session file not materialized')
  }

  const loaded = await loadTranscriptFile(state.sessionFile)
  const messages = entriesToMessageMap(loaded.allEntries)
  const target = messages.get(messageUuid)
  if (!target || target.type !== 'user') {
    throw new Error('Target is not a user message UUID')
  }

  const chain = buildConversationChain(messages, target)
  const keepUuids = new Set(chain.map(m => m.uuid))

  const kept = [] as typeof loaded.allEntries
  for (const entry of loaded.allEntries) {
    if (entry.type === 'custom-title') {
      kept.push(entry)
      continue
    }
    if (typeof entry.uuid === 'string' && isTranscriptMessage(entry)) {
      if (keepUuids.has(entry.uuid)) {
        kept.push(entry)
      }
      continue
    }
  }

  const body = kept.map(e => JSON.stringify(e)).join('\n') + '\n'
  await writeFile(state.sessionFile, body, 'utf8')
  clearToolTurn(state)
  restoreChainFromEntries(sessionId, cwd, kept)
  reAppendSessionMetadata(sessionId, state)
}

export async function flushSession(sessionId: string, cwd: string): Promise<void> {
  const state = getState(sessionId, cwd)
  await state.writeChain
  if (state.sessionFile) {
    reAppendSessionMetadata(sessionId, state)
  }
}
