import type { TranscriptLoadResult } from '@agent-desktop/shared-types'
import { dirname } from 'node:path'
import {
  extractFirstPromptFromHead,
  getProjectDir,
  normalizeProjectPath,
  projectPathsMatch,
  readSessionLite,
  resolveCwdForSession,
  resolveSessionFilePath,
} from './claudeSessionPortable'
import { listSessionsImpl } from './listSessionsImpl'
import {
  bindSessionFile,
  flushSession,
  recordAssistantText,
  recordAssistantToolUses,
  recordCompactBoundary,
  recordCustomTitle,
  recordToolResults,
  recordUserMessage,
  restoreChainFromEntries,
  switchTranscriptSession as switchTranscriptSessionImpl,
  truncateTranscriptToMessage,
  type ClaudeTranscriptEntry,
} from './transcriptProject'
import { loadTranscriptFile } from './transcriptLoad'

export type { ClaudeTranscriptEntry }

export async function listTranscriptSessions(
  workspaceRoot: string | null | undefined,
  limit = 30,
): Promise<import('@agent-desktop/shared-types').TranscriptSessionSummary[]> {
  const cwd = resolveCwdForSession(workspaceRoot)
  return listSessionsImpl({ dir: cwd, limit })
}

export async function loadTranscriptSession(
  sessionId: string,
  workspaceRoot?: string | null,
): Promise<TranscriptLoadResult> {
  const cwd = resolveCwdForSession(workspaceRoot)
  const hasWorkspace = Boolean(workspaceRoot?.trim())

  let resolved =
    (await resolveSessionFilePath(sessionId, cwd)) ??
    (hasWorkspace ? undefined : await resolveSessionFilePath(sessionId))

  if (
    resolved &&
    hasWorkspace &&
    resolved.projectPath &&
    !projectPathsMatch(resolved.projectPath, workspaceRoot!)
  ) {
    resolved = undefined
  }

  if (!resolved) {
    switchTranscriptSessionImpl(sessionId, cwd, null)
    return { sessionId, entries: [], title: `Chat ${sessionId.slice(0, 8)}`, cwd }
  }

  switchTranscriptSessionImpl(
    sessionId,
    cwd,
    normalizeProjectPath(dirname(resolved.filePath)) === normalizeProjectPath(getProjectDir(cwd))
      ? null
      : dirname(resolved.filePath),
  )
  bindSessionFile(resolved, sessionId, cwd)
  const loaded = await loadTranscriptFile(resolved.filePath)
  const entries = loaded.chainEntries as TranscriptLoadResult['entries']
  restoreChainFromEntries(sessionId, cwd, entries as ClaudeTranscriptEntry[])

  let title = `Chat ${sessionId.slice(0, 8)}`
  const lite = await readSessionLite(resolved.filePath)
  const firstPrompt = lite ? extractFirstPromptFromHead(lite.head) : ''
  if (loaded.customTitle) title = loaded.customTitle
  else if (loaded.lastPrompt) title = loaded.lastPrompt
  else if (firstPrompt) title = firstPrompt.length > 80 ? `${firstPrompt.slice(0, 77)}...` : firstPrompt

  return {
    sessionId,
    entries,
    title,
    cwd: resolved.projectPath ?? cwd,
  }
}

export async function appendUser(
  sessionId: string,
  workspaceRoot: string | null | undefined,
  text: string,
  attachments?: import('./attachmentStorage').PersistUserAttachmentInput[],
): Promise<import('./attachmentStorage').AppendUserResult> {
  const cwd = resolveCwdForSession(workspaceRoot)
  return recordUserMessage(sessionId, cwd, text, attachments)
}

export async function appendCompactBoundary(
  sessionId: string,
  workspaceRoot: string | null | undefined,
  boundary: {
    trigger: 'auto' | 'manual'
    usedChars: number
    maxChars: number
    summary?: string
  },
): Promise<void> {
  const cwd = resolveCwdForSession(workspaceRoot)
  await recordCompactBoundary(sessionId, cwd, boundary)
}

export async function appendToolUse(
  sessionId: string,
  workspaceRoot: string | null | undefined,
  tool: { toolCallId: string; name: string; argsJson?: string },
) {
  const cwd = resolveCwdForSession(workspaceRoot)
  await recordAssistantToolUses(sessionId, cwd, [tool])
}

export async function appendToolResult(
  sessionId: string,
  workspaceRoot: string | null | undefined,
  result: { toolCallId: string; name: string; ok: boolean; output: string },
) {
  const cwd = resolveCwdForSession(workspaceRoot)
  await recordToolResults(sessionId, cwd, [result])
}

export async function appendAssistantText(
  sessionId: string,
  workspaceRoot: string | null | undefined,
  text: string,
) {
  const cwd = resolveCwdForSession(workspaceRoot)
  await recordAssistantText(sessionId, cwd, text)
}

export async function setSessionTitle(
  sessionId: string,
  workspaceRoot: string | null | undefined,
  title: string,
) {
  const cwd = resolveCwdForSession(workspaceRoot)
  await recordCustomTitle(sessionId, cwd, title)
}

export async function flushTranscript(sessionId: string, workspaceRoot: string | null | undefined) {
  const cwd = resolveCwdForSession(workspaceRoot)
  await flushSession(sessionId, cwd)
}

export function switchTranscriptSession(
  sessionId: string,
  workspaceRoot: string | null | undefined,
  sessionProjectDir: string | null,
): void {
  const cwd = resolveCwdForSession(workspaceRoot)
  switchTranscriptSessionImpl(sessionId, cwd, sessionProjectDir)
}

export async function truncateTranscript(
  sessionId: string,
  workspaceRoot: string | null | undefined,
  messageUuid: string,
): Promise<void> {
  const cwd = resolveCwdForSession(workspaceRoot)
  await truncateTranscriptToMessage(sessionId, cwd, messageUuid)
}
