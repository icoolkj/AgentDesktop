import { ipcMain } from 'electron'
import {
  appendAssistantText,
  appendCompactBoundary,
  appendToolResult,
  appendToolUse,
  appendUser,
  flushTranscript,
  listTranscriptSessions,
  loadTranscriptSession,
  setSessionTitle,
  switchTranscriptSession,
  truncateTranscript,
} from '../transcript/transcriptStorage'

export function setupTranscriptIpcHandlers(): void {
  ipcMain.handle('transcript:listSessions', async (_, workspaceRoot?: string | null, limit?: number) => {
    return listTranscriptSessions(workspaceRoot, limit ?? 30)
  })

  ipcMain.handle('transcript:loadSession', async (_, sessionId: string, workspaceRoot?: string | null) => {
    return loadTranscriptSession(sessionId, workspaceRoot)
  })

  ipcMain.handle(
    'transcript:switchSession',
    (_, sessionId: string, workspaceRoot: string | null | undefined, sessionProjectDir: string | null) => {
      switchTranscriptSession(sessionId, workspaceRoot, sessionProjectDir)
    },
  )

  ipcMain.handle(
    'transcript:appendUser',
    async (
      _,
      sessionId: string,
      workspaceRoot: string | null | undefined,
      text: string,
      attachments?: import('../transcript/attachmentStorage').PersistUserAttachmentInput[],
    ) => {
      return appendUser(sessionId, workspaceRoot, text, attachments)
    },
  )

  ipcMain.handle(
    'transcript:appendCompactBoundary',
    async (
      _,
      sessionId: string,
      workspaceRoot: string | null | undefined,
      boundary: {
        trigger: 'auto' | 'manual'
        usedChars: number
        maxChars: number
        summary?: string
      },
    ) => {
      await appendCompactBoundary(sessionId, workspaceRoot, boundary)
    },
  )

  ipcMain.handle(
    'transcript:appendToolUse',
    async (
      _,
      sessionId: string,
      workspaceRoot: string | null | undefined,
      tool: { toolCallId: string; name: string; argsJson?: string },
    ) => {
      await appendToolUse(sessionId, workspaceRoot, tool)
    },
  )

  ipcMain.handle(
    'transcript:appendToolResult',
    async (
      _,
      sessionId: string,
      workspaceRoot: string | null | undefined,
      result: { toolCallId: string; name: string; ok: boolean; output: string },
    ) => {
      await appendToolResult(sessionId, workspaceRoot, result)
    },
  )

  ipcMain.handle(
    'transcript:appendAssistantText',
    async (_, sessionId: string, workspaceRoot: string | null | undefined, text: string) => {
      await appendAssistantText(sessionId, workspaceRoot, text)
    },
  )

  ipcMain.handle(
    'transcript:setTitle',
    async (_, sessionId: string, workspaceRoot: string | null | undefined, title: string) => {
      await setSessionTitle(sessionId, workspaceRoot, title)
    },
  )

  ipcMain.handle(
    'transcript:flush',
    async (_, sessionId: string, workspaceRoot: string | null | undefined) => {
      await flushTranscript(sessionId, workspaceRoot)
    },
  )

  ipcMain.handle(
    'transcript:truncate',
    async (
      _,
      sessionId: string,
      workspaceRoot: string | null | undefined,
      messageUuid: string,
    ) => {
      await truncateTranscript(sessionId, workspaceRoot, messageUuid)
    },
  )
}
