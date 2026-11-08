import { ipcRenderer } from 'electron'

export interface RecordFileChangeDto {
  absolutePath: string
  workspaceRoot?: string | null
  content: string
  previousContent?: string | null
  changeKind: 'modified' | 'created'
  conversationId?: string | null
  requestId?: string | null
  model?: string | null
  source?: 'composer' | 'tab'
  recordHashes?: boolean
}

export interface ScoredCommitDto {
  commitHash: string
  branchName: string
  scoredAt: number
  linesAdded: number | null
  linesDeleted: number | null
  composerLinesAdded: number | null
  composerLinesDeleted: number | null
  humanLinesAdded: number | null
  humanLinesDeleted: number | null
  v1AiPercentage: string | null
  v2AiPercentage: string | null
  commitMessage: string | null
  commitDate: string | null
}

export interface RecentWorkspaceEntry {
  path: string
  openedAt: number
}

export type BottomPanelTabDto = 'terminal' | 'problems'

export interface PanelLayoutDto {
  sidebar: number
  chat: number
  sidebarCollapsed: boolean
  chatCollapsed: boolean
  bottomPanelOpen?: boolean
  bottomPanelTab?: BottomPanelTabDto
  bottomPanelHeight?: number
}

export interface WorkspaceUiPrefsDto {
  bottomPanelOpenOnStartup: boolean
  bottomPanelTabOnStartup: BottomPanelTabDto
  autoScanProblemsOnOpen: boolean
}

export interface DevContextDto {
  appRoot: string
  rendererSrcRoot: string
  isSelfWorkspace: boolean
}

export type WindowBootMode = 'restore' | 'blank' | 'workspace'

export interface WindowBootConfig {
  mode: WindowBootMode
  workspacePath?: string
}

export interface OpenWorkspaceWindowOptions {
  forceNew?: boolean
  openInNewWindow?: boolean
}

export const desktopApi = {
  getWindowBoot: (): Promise<WindowBootConfig> => ipcRenderer.invoke('app:getWindowBoot'),
  openWorkspaceWindow: (
    workspacePath?: string,
    options?: OpenWorkspaceWindowOptions | boolean,
  ): Promise<string | null> => {
    const payload = typeof options === 'boolean' ? { forceNew: options } : options
    return ipcRenderer.invoke('app:openWorkspaceWindow', workspacePath, payload)
  },

  newWindow: (): Promise<void> => ipcRenderer.invoke('app:newWindow'),

  focusWorkspaceWindow: (workspacePath: string): Promise<boolean> =>
    ipcRenderer.invoke('app:focusWorkspaceWindow', workspacePath),

  syncWindowWorkspace: (workspaceRoot?: string | null): Promise<void> =>
    ipcRenderer.invoke('app:syncWindowWorkspace', workspaceRoot),

  ensureWorkspaceReady: (workspaceRoot: string): Promise<string> =>
    ipcRenderer.invoke('app:ensureWorkspaceReady', workspaceRoot),

  setWindowTitle: (workspaceRoot?: string | null): Promise<void> =>
    ipcRenderer.invoke('app:setWindowTitle', workspaceRoot),

  getDevContext: (workspaceRoot?: string | null): Promise<DevContextDto> =>
    ipcRenderer.invoke('app:getDevContext', workspaceRoot),

  recordFileChange: (input: RecordFileChangeDto): Promise<void> =>
    ipcRenderer.invoke('aiTracking:recordFileChange', input),

  recordFileDelete: (input: {
    absolutePath: string
    workspaceRoot?: string | null
    conversationId?: string | null
    composerId?: string | null
    model?: string | null
  }): Promise<void> => ipcRenderer.invoke('aiTracking:recordFileDelete', input),

  upsertConversationSummary: (input: {
    conversationId: string
    title?: string | null
    model?: string | null
    mode?: string | null
  }): Promise<void> => ipcRenderer.invoke('aiTracking:upsertConversationSummary', input),

  scoreRecentCommits: (gitRoot: string, limit?: number): Promise<ScoredCommitDto[]> =>
    ipcRenderer.invoke('aiTracking:scoreRecentCommits', gitRoot, limit),

  resolveGitRoot: (workspaceRoot: string): Promise<string | null> =>
    ipcRenderer.invoke('aiTracking:resolveGitRoot', workspaceRoot),

  getRecentWorkspaces: (): Promise<RecentWorkspaceEntry[]> =>
    ipcRenderer.invoke('userState:getRecentWorkspaces'),

  setRecentWorkspaces: (entries: RecentWorkspaceEntry[]): Promise<void> =>
    ipcRenderer.invoke('userState:setRecentWorkspaces', entries),

  getLastWorkspace: (): Promise<string | null> => ipcRenderer.invoke('userState:getLastWorkspace'),

  setLastWorkspace: (path: string): Promise<void> =>
    ipcRenderer.invoke('userState:setLastWorkspace', path),

  getPanelLayout: (): Promise<PanelLayoutDto | null> => ipcRenderer.invoke('userState:getPanelLayout'),

  setPanelLayout: (layout: Partial<PanelLayoutDto>): Promise<void> =>
    ipcRenderer.invoke('userState:setPanelLayout', layout),

  getWorkspaceUiPrefs: (): Promise<WorkspaceUiPrefsDto> =>
    ipcRenderer.invoke('userState:getWorkspaceUiPrefs'),

  setWorkspaceUiPrefs: (prefs: WorkspaceUiPrefsDto): Promise<void> =>
    ipcRenderer.invoke('userState:setWorkspaceUiPrefs', prefs),

  getWorkspaceSession: (workspaceRoot: string): Promise<string | null> =>
    ipcRenderer.invoke('userState:getWorkspaceSession', workspaceRoot),

  setWorkspaceSession: (workspaceRoot: string, sessionId: string): Promise<void> =>
    ipcRenderer.invoke('userState:setWorkspaceSession', workspaceRoot, sessionId),

  setTitleBarOverlay: (overlay: { color: string; symbolColor: string }): Promise<void> =>
    ipcRenderer.invoke('app:setTitleBarOverlay', overlay),

  openExternal: (url: string): Promise<void> => ipcRenderer.invoke('app:openExternal', url),
}

export type DesktopApi = typeof desktopApi
