import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'

import { getConfigHomeDir, getUserStatePath } from '../paths/agentDesktopPaths'

function ensureDir(): void {
  mkdirSync(getConfigHomeDir(), { recursive: true })
}

export function readUserStateFile<T>(fileName: string, fallback: T): T {
  ensureDir()
  const path = getUserStatePath(fileName)
  if (!existsSync(path)) return fallback
  try {
    return JSON.parse(readFileSync(path, 'utf8')) as T
  } catch {
    return fallback
  }
}

export function writeUserStateFile<T>(fileName: string, value: T): void {
  ensureDir()
  writeFileSync(getUserStatePath(fileName), JSON.stringify(value, null, 2), 'utf8')
}

export interface RecentWorkspaceEntry {
  path: string
  openedAt: number
}

export function readRecentWorkspaces(): RecentWorkspaceEntry[] {
  return readUserStateFile<RecentWorkspaceEntry[]>('recent-workspaces.json', [])
}

export function writeRecentWorkspaces(entries: RecentWorkspaceEntry[]): void {
  writeUserStateFile('recent-workspaces.json', entries)
}

export function readLastWorkspacePath(): string | null {
  const path = readUserStateFile<string | null>('last-workspace.json', null)
  return path?.trim() || null
}

export function writeLastWorkspacePath(path: string | null): void {
  if (!path) {
    writeUserStateFile('last-workspace.json', null)
    return
  }
  writeUserStateFile('last-workspace.json', path)
}

export type BottomPanelTab = 'terminal' | 'problems'

export interface PanelLayoutState {
  sidebar: number
  chat: number
  sidebarCollapsed: boolean
  chatCollapsed: boolean
  bottomPanelOpen?: boolean
  bottomPanelTab?: BottomPanelTab
  bottomPanelHeight?: number
}

const PANEL_LAYOUT_DEFAULTS: PanelLayoutState = {
  sidebar: 260,
  chat: 360,
  sidebarCollapsed: false,
  chatCollapsed: false,
}

export function readPanelLayout(): PanelLayoutState | null {
  return readUserStateFile<PanelLayoutState | null>('panel-layout.json', null)
}

/** Merge partial updates so sidebar/chat and bottom-panel saves do not clobber each other. */
export function writePanelLayout(patch: Partial<PanelLayoutState>): void {
  const existing = readPanelLayout()
  const merged: PanelLayoutState = {
    sidebar: patch.sidebar ?? existing?.sidebar ?? PANEL_LAYOUT_DEFAULTS.sidebar,
    chat: patch.chat ?? existing?.chat ?? PANEL_LAYOUT_DEFAULTS.chat,
    sidebarCollapsed:
      patch.sidebarCollapsed ?? existing?.sidebarCollapsed ?? PANEL_LAYOUT_DEFAULTS.sidebarCollapsed,
    chatCollapsed: patch.chatCollapsed ?? existing?.chatCollapsed ?? PANEL_LAYOUT_DEFAULTS.chatCollapsed,
    bottomPanelOpen: patch.bottomPanelOpen ?? existing?.bottomPanelOpen,
    bottomPanelTab: patch.bottomPanelTab ?? existing?.bottomPanelTab,
    bottomPanelHeight: patch.bottomPanelHeight ?? existing?.bottomPanelHeight,
  }
  writeUserStateFile('panel-layout.json', merged)
}

export interface WorkspaceUiPrefs {
  /** Show bottom panel when a workspace window opens (before session restore). */
  bottomPanelOpenOnStartup: boolean
  /** Default tab when the bottom panel opens on startup. */
  bottomPanelTabOnStartup: BottomPanelTab
  /** Run tsc/eslint workspace scan automatically when a folder is opened. */
  autoScanProblemsOnOpen: boolean
}

export const DEFAULT_WORKSPACE_UI_PREFS: WorkspaceUiPrefs = {
  bottomPanelOpenOnStartup: true,
  bottomPanelTabOnStartup: 'terminal',
  autoScanProblemsOnOpen: false,
}

export function readWorkspaceUiPrefs(): WorkspaceUiPrefs {
  const saved = readUserStateFile<Partial<WorkspaceUiPrefs> | null>('workspace-ui-prefs.json', null)
  return normalizeWorkspaceUiPrefs(saved)
}

export function writeWorkspaceUiPrefs(prefs: WorkspaceUiPrefs): void {
  writeUserStateFile('workspace-ui-prefs.json', normalizeWorkspaceUiPrefs(prefs))
}

function normalizeWorkspaceUiPrefs(input: Partial<WorkspaceUiPrefs> | null | undefined): WorkspaceUiPrefs {
  const tab = input?.bottomPanelTabOnStartup
  return {
    bottomPanelOpenOnStartup: input?.bottomPanelOpenOnStartup ?? DEFAULT_WORKSPACE_UI_PREFS.bottomPanelOpenOnStartup,
    bottomPanelTabOnStartup: tab === 'problems' ? 'problems' : 'terminal',
    autoScanProblemsOnOpen: input?.autoScanProblemsOnOpen ?? DEFAULT_WORKSPACE_UI_PREFS.autoScanProblemsOnOpen,
  }
}

export interface WorkspaceSessionState {
  sessionId: string
}

export function readWorkspaceSession(workspaceRoot: string): string | null {
  const map = readUserStateFile<Record<string, string>>('workspace-sessions.json', {})
  const key = workspaceRoot.replace(/\\/g, '/').replace(/\/+$/, '').toLowerCase()
  return map[key] ?? null
}

export function writeWorkspaceSession(workspaceRoot: string, sessionId: string): void {
  const map = readUserStateFile<Record<string, string>>('workspace-sessions.json', {})
  const key = workspaceRoot.replace(/\\/g, '/').replace(/\/+$/, '').toLowerCase()
  map[key] = sessionId
  writeUserStateFile('workspace-sessions.json', map)
}
