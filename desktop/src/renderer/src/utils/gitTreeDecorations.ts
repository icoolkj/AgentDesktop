export type GitDecorationKind = 'modified' | 'untracked' | 'added' | 'deleted' | 'renamed'

export interface GitTreeDecoration {
  kind: GitDecorationKind
  /** File badge: M / U / A / D / R */
  badge: string | null
  /** Folder dot on the right (Cursor-style ancestor hint) */
  dirDot: GitDecorationKind | null
  colorClass: string
}

export function decorationKindFromStatus(status: string): GitDecorationKind {
  const s = status.trim()
  if (s === '?' || s.includes('?')) return 'untracked'
  if (s.includes('U') && !s.includes('M')) return 'untracked'
  if (s.includes('D')) return 'deleted'
  if (s.includes('A')) return 'added'
  if (s.includes('R')) return 'renamed'
  return 'modified'
}

export function badgeForKind(kind: GitDecorationKind): string {
  switch (kind) {
    case 'untracked':
      return 'U'
    case 'added':
      return 'A'
    case 'deleted':
      return 'D'
    case 'renamed':
      return 'R'
    default:
      return 'M'
  }
}

export function colorClassForKind(kind: GitDecorationKind): string {
  switch (kind) {
    case 'untracked':
    case 'added':
      return 'is-git-untracked'
    case 'deleted':
      return 'is-git-deleted'
    default:
      return 'is-git-modified'
  }
}
