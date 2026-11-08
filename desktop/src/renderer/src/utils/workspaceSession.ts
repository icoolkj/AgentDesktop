/** Per-project session id persistence — stored in ~/.agent-desktop/workspace-sessions.json */

export function normalizeWorkspacePath(path: string): string {
  return path.replace(/\\/g, '/').replace(/\/+$/, '').normalize('NFC').toLowerCase()
}

export async function loadSessionIdForWorkspace(
  workspaceRoot: string | null | undefined,
): Promise<string | null> {
  if (!workspaceRoot?.trim()) return null
  return window.desktopApi.getWorkspaceSession(workspaceRoot.trim())
}

export async function saveSessionIdForWorkspace(
  workspaceRoot: string | null | undefined,
  sessionId: string,
): Promise<void> {
  if (!workspaceRoot?.trim()) return
  await window.desktopApi.setWorkspaceSession(workspaceRoot.trim(), sessionId)
}

export function sessionBelongsToWorkspace(
  sessionCwd: string | undefined,
  workspaceRoot: string | null | undefined,
): boolean {
  if (!workspaceRoot?.trim()) {
    return !sessionCwd?.trim() || sessionCwd.includes('no-workspace')
  }
  if (!sessionCwd?.trim()) return false
  return normalizeWorkspacePath(sessionCwd) === normalizeWorkspacePath(workspaceRoot)
}
