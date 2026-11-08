const RULE_FILES = ['AGENTS.md', '.cursorrules', 'CLAUDE.md'] as const
const MAX_RULE_BYTES = 64_000

/** Load project rules — Claude Code skips missing CLAUDE.md with ENOENT (no error). */
export async function loadProjectRules(workspaceRoot: string | undefined): Promise<string | undefined> {
  if (!workspaceRoot) return undefined

  for (const name of RULE_FILES) {
    const sep = workspaceRoot.includes('\\') ? '\\' : '/'
    const filePath = `${workspaceRoot.replace(/[/\\]+$/, '')}${sep}${name}`
    try {
      const content = await window.workspaceApi.readFile(filePath, {
        maxBytes: MAX_RULE_BYTES,
        optional: true,
      })
      const trimmed = content?.trim()
      if (trimmed) return trimmed
    } catch {
      // unreadable — try next candidate
    }
  }
  return undefined
}
