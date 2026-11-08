import { ipcRenderer } from 'electron'
import type { AdTranscriptAttachment, TranscriptLoadResult, TranscriptSessionSummary } from '@agent-desktop/shared-types'

export const transcriptApi = {
  listSessions: (workspaceRoot?: string | null, limit?: number): Promise<TranscriptSessionSummary[]> =>
    ipcRenderer.invoke('transcript:listSessions', workspaceRoot, limit),

  loadSession: (sessionId: string, workspaceRoot?: string | null): Promise<TranscriptLoadResult> =>
    ipcRenderer.invoke('transcript:loadSession', sessionId, workspaceRoot),

  switchSession: (
    sessionId: string,
    workspaceRoot: string | null | undefined,
    sessionProjectDir: string | null,
  ): Promise<void> => ipcRenderer.invoke('transcript:switchSession', sessionId, workspaceRoot, sessionProjectDir),

  appendUser: (
    sessionId: string,
    workspaceRoot: string | null | undefined,
    text: string,
    attachments?: Array<{
      id: string
      kind: 'image' | 'pdf' | 'pdf_reference' | 'text' | 'file' | 'folder'
      name: string
      filePath?: string
      base64?: string
      mimeType?: string
    }>,
  ): Promise<{ messageUuid: string; storedAttachments?: AdTranscriptAttachment[] }> =>
    ipcRenderer.invoke('transcript:appendUser', sessionId, workspaceRoot, text, attachments),

  appendCompactBoundary: (
    sessionId: string,
    workspaceRoot: string | null | undefined,
    boundary: {
      trigger: 'auto' | 'manual'
      usedChars: number
      maxChars: number
      summary?: string
    },
  ): Promise<void> =>
    ipcRenderer.invoke('transcript:appendCompactBoundary', sessionId, workspaceRoot, boundary),

  appendToolUse: (
    sessionId: string,
    workspaceRoot: string | null | undefined,
    tool: { toolCallId: string; name: string; argsJson?: string },
  ): Promise<void> => ipcRenderer.invoke('transcript:appendToolUse', sessionId, workspaceRoot, tool),

  appendToolResult: (
    sessionId: string,
    workspaceRoot: string | null | undefined,
    result: { toolCallId: string; name: string; ok: boolean; output: string },
  ): Promise<void> => ipcRenderer.invoke('transcript:appendToolResult', sessionId, workspaceRoot, result),

  appendAssistantText: (
    sessionId: string,
    workspaceRoot: string | null | undefined,
    text: string,
  ): Promise<void> => ipcRenderer.invoke('transcript:appendAssistantText', sessionId, workspaceRoot, text),

  setTitle: (sessionId: string, workspaceRoot: string | null | undefined, title: string): Promise<void> =>
    ipcRenderer.invoke('transcript:setTitle', sessionId, workspaceRoot, title),

  flush: (sessionId: string, workspaceRoot: string | null | undefined): Promise<void> =>
    ipcRenderer.invoke('transcript:flush', sessionId, workspaceRoot),

  truncate: (
    sessionId: string,
    workspaceRoot: string | null | undefined,
    messageUuid: string,
  ): Promise<void> => ipcRenderer.invoke('transcript:truncate', sessionId, workspaceRoot, messageUuid),
}

export type TranscriptApi = typeof transcriptApi
