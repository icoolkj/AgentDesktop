const MAX_GIT_STATUS_LINES = 40

/** Format git porcelain for system prompt — Claude Code cwd context parity. */
export async function loadGitStatusSummary(workspaceRoot: string | undefined): Promise<string | undefined> {
  if (!workspaceRoot) return undefined
  try {
    const info = await window.workspaceApi.gitInfo(workspaceRoot, 'changes')
    if (!info.isRepo) return undefined
    const branch = info.branch ? `branch: ${info.branch}\n` : ''
    const changes = info.changes ?? []
    if (changes.length === 0) {
      return `${branch}git status: clean working tree`
    }
    const lines = changes.slice(0, MAX_GIT_STATUS_LINES).map(c => {
      const flags = [c.staged ? 'staged' : null, c.unstaged ? 'unstaged' : null]
        .filter(Boolean)
        .join('+')
      return `${c.status} ${c.path}${flags ? ` (${flags})` : ''}`
    })
    const suffix =
      changes.length > MAX_GIT_STATUS_LINES
        ? `\n… and ${changes.length - MAX_GIT_STATUS_LINES} more`
        : ''
    return `${branch}git status (${changes.length} change(s)):\n${lines.join('\n')}${suffix}`
  } catch {
    return undefined
  }
}
