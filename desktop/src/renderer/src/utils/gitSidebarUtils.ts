import type { GitChange } from '../../../preload/api/workspaceApi'

export type GitPanelView = 'changes' | 'commit' | 'log' | 'sync'

export function sortFileChanges(changes: GitChange[]): GitChange[] {
  return [...changes]
    .filter(change => change.kind === 'file')
    .sort((a, b) =>
      a.path.replace(/\\/g, '/').localeCompare(b.path.replace(/\\/g, '/'), undefined, {
        sensitivity: 'base',
      }),
    )
}

export function pathSep(basePath: string): string {
  return basePath.includes('\\') ? '\\' : '/'
}

export function toDisplayPath(relPath: string, basePath: string): string {
  return relPath.replace(/\\/g, '/').replace(/\/+$/, '').split('/').join(pathSep(basePath))
}

export function resolveAbsPath(relPath: string, gitRoot: string): string {
  const sep = pathSep(gitRoot)
  return `${gitRoot}${gitRoot.endsWith(sep) ? '' : sep}${relPath.replace(/\//g, sep)}`
}

export function changeName(relPath: string): string {
  const normalized = relPath.replace(/\\/g, '/').replace(/\/+$/, '')
  const parts = normalized.split('/').filter(Boolean)
  return parts[parts.length - 1] ?? normalized
}

export function defaultPushTarget(
  branch: string | undefined,
  trackingBranch: string | undefined,
): { remote?: string; branch?: string } {
  if (!trackingBranch) return {}
  const slash = trackingBranch.indexOf('/')
  if (slash <= 0) return { branch: trackingBranch }
  return {
    remote: trackingBranch.slice(0, slash),
    branch: trackingBranch.slice(slash + 1) || branch,
  }
}

export function formatGitLogDate(unixSeconds: number): string {
  const date = new Date(unixSeconds * 1000)
  const diffMs = Date.now() - date.getTime()
  if (diffMs < 60_000) return '刚刚'
  if (diffMs < 3_600_000) return `${Math.floor(diffMs / 60_000)} 分钟前`
  if (diffMs < 86_400_000) return `${Math.floor(diffMs / 3_600_000)} 小时前`
  if (diffMs < 604_800_000) return `${Math.floor(diffMs / 86_400_000)} 天前`
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}
