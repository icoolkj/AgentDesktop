import { ipcMain } from 'electron'

import {
  recordFileChange,
  recordFileDelete,
  scoreCommit,
  scoreRecentCommits,
  upsertConversationSummary,
} from '../aiTracking'
import { resolveGitFileContext } from '../aiTracking/gitContext'
import { ensureProjectLayout } from '../paths/agentDesktopPaths'
import {
  readLastWorkspacePath,
  readPanelLayout,
  readRecentWorkspaces,
  readWorkspaceSession,
  readWorkspaceUiPrefs,
  writeLastWorkspacePath,
  writePanelLayout,
  writeRecentWorkspaces,
  writeWorkspaceSession,
  writeWorkspaceUiPrefs,
  type PanelLayoutState,
  type RecentWorkspaceEntry,
  type WorkspaceUiPrefs,
} from '../userState/userStateStore'

export function setupAiTrackingIpcHandlers(): void {
  ipcMain.handle(
    'aiTracking:recordFileChange',
    async (
      _,
      input: {
        absolutePath: string
        workspaceRoot?: string | null
        content: string
        previousContent?: string | null
        changeKind: 'modified' | 'created'
        conversationId: string
        requestId?: string | null
        model?: string | null
        source?: 'composer' | 'tab'
      },
    ) => {
      if (input.workspaceRoot) ensureProjectLayout(input.workspaceRoot)
      await recordFileChange(input)
    },
  )

  ipcMain.handle(
    'aiTracking:recordFileDelete',
    async (
      _,
      input: {
        absolutePath: string
        workspaceRoot?: string | null
        conversationId?: string | null
        composerId?: string | null
        model?: string | null
      },
    ) => {
      await recordFileDelete(input)
    },
  )

  ipcMain.handle(
    'aiTracking:upsertConversationSummary',
    async (
      _,
      input: {
        conversationId: string
        title?: string | null
        tldr?: string | null
        overview?: string | null
        summaryBullets?: string | null
        model?: string | null
        mode?: string | null
      },
    ) => {
      upsertConversationSummary(input)
    },
  )

  ipcMain.handle('aiTracking:scoreCommit', async (_, gitRoot: string, commitHash: string) => {
    if (!gitRoot || !commitHash) return null
    return scoreCommit(gitRoot, commitHash)
  })

  ipcMain.handle('aiTracking:scoreRecentCommits', async (_, gitRoot: string, limit?: number) => {
    if (!gitRoot) return []
    return scoreRecentCommits(gitRoot, limit ?? 20)
  })

  ipcMain.handle('aiTracking:resolveGitRoot', async (_, workspaceRoot: string) => {
    const ctx = await resolveGitFileContext(workspaceRoot, workspaceRoot)
    return ctx.gitRoot
  })
}

export function setupUserStateIpcHandlers(): void {
  ipcMain.handle('userState:getRecentWorkspaces', () => readRecentWorkspaces())

  ipcMain.handle('userState:setRecentWorkspaces', (_, entries: RecentWorkspaceEntry[]) => {
    writeRecentWorkspaces(entries)
  })

  ipcMain.handle('userState:getLastWorkspace', () => readLastWorkspacePath())

  ipcMain.handle('userState:setLastWorkspace', (_, path: string) => {
    writeLastWorkspacePath(path)
  })

  ipcMain.handle('userState:getPanelLayout', () => readPanelLayout())

  ipcMain.handle('userState:setPanelLayout', (_, layout: Partial<PanelLayoutState>) => {
    writePanelLayout(layout)
  })

  ipcMain.handle('userState:getWorkspaceUiPrefs', () => readWorkspaceUiPrefs())

  ipcMain.handle('userState:setWorkspaceUiPrefs', (_, prefs: WorkspaceUiPrefs) => {
    writeWorkspaceUiPrefs(prefs)
  })

  ipcMain.handle('userState:getWorkspaceSession', (_, workspaceRoot: string) =>
    readWorkspaceSession(workspaceRoot),
  )

  ipcMain.handle('userState:setWorkspaceSession', (_, workspaceRoot: string, sessionId: string) => {
    writeWorkspaceSession(workspaceRoot, sessionId)
  })
}
