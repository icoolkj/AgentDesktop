const MAX_RECENT = 10

export interface RecentWorkspace {
  path: string
  openedAt: number
}

function normalizePathKey(path: string): string {
  return path.replace(/\\/g, '/').replace(/\/+$/, '').toLowerCase()
}

/** Playwright E2E uses mkdtemp('agent-desktop-e2e-*') — must not pollute last-workspace. */
export function isEphemeralWorkspacePath(path: string): boolean {
  const name = path.replace(/\\/g, '/').replace(/\/+$/, '').split('/').pop() ?? ''
  return /^agent-desktop-e2e-/i.test(name)
}

export async function getLastWorkspacePath(): Promise<string | null> {
  return window.desktopApi.getLastWorkspace()
}

export async function saveLastWorkspacePath(path: string): Promise<void> {
  await window.desktopApi.setLastWorkspace(path)
}

export async function clearLastWorkspacePath(): Promise<void> {
  await window.desktopApi.setLastWorkspace('')
}

export async function listRecentWorkspaces(): Promise<RecentWorkspace[]> {
  const items = await window.desktopApi.getRecentWorkspaces()
  if (items.length > 0) {
    return items
      .filter(item => item?.path)
      .sort((a, b) => (b.openedAt ?? 0) - (a.openedAt ?? 0))
      .slice(0, MAX_RECENT)
  }

  const last = await getLastWorkspacePath()
  if (!last) return []
  return [{ path: last, openedAt: Date.now() }]
}

export async function addRecentWorkspace(path: string): Promise<void> {
  const trimmed = path.trim()
  if (!trimmed || isEphemeralWorkspacePath(trimmed)) return

  const key = normalizePathKey(trimmed)
  const now = Date.now()
  const next: RecentWorkspace[] = [
    { path: trimmed, openedAt: now },
    ...(await listRecentWorkspaces()).filter(item => normalizePathKey(item.path) !== key),
  ].slice(0, MAX_RECENT)

  await window.desktopApi.setRecentWorkspaces(next)
  await saveLastWorkspacePath(trimmed)
}

export async function rememberWorkspace(path: string): Promise<void> {
  await addRecentWorkspace(path)
}

export function workspaceDisplayName(path: string): string {
  const normalized = path.replace(/\\/g, '/').replace(/\/+$/, '')
  const parts = normalized.split('/')
  return parts[parts.length - 1] || path
}
