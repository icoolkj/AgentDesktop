import { resolveGitRootFromWorkspace, runGit, type GitRunResult } from './gitRunner'

export interface GitRemoteDto {
  name: string
  fetchUrl: string
  pushUrl?: string
}

export interface GitChangeDto {
  status: string
  path: string
  kind: 'file' | 'directory'
  staged: boolean
  unstaged: boolean
}

export interface GitSyncStatusDto {
  trackingBranch?: string
  ahead: number
  behind: number
}

export interface GitInfoDto {
  isRepo: boolean
  gitRoot?: string
  branch?: string
  remotes: GitRemoteDto[]
  changes: GitChangeDto[]
  syncStatus?: GitSyncStatusDto
  error?: string
}

export type GitInfoLoadScope = 'full' | 'changes' | 'sync'

function parsePorcelainColumns(raw: string): { status: string; staged: boolean; unstaged: boolean } {
  const padded = raw.padEnd(2, ' ')
  const index = padded[0] ?? ' '
  const worktree = padded[1] ?? ' '

  if (index === '?' && worktree === '?') {
    return { status: '??', staged: false, unstaged: true }
  }

  return {
    status: raw.trimEnd() || ' ',
    staged: index !== ' ' && index !== '?',
    unstaged: worktree !== ' ' && worktree !== '?',
  }
}

/** Drop folder rows when individual files under that folder are already listed. */
function filterFlattenedChanges(changes: GitChangeDto[]): GitChangeDto[] {
  const files = changes.filter(c => c.kind === 'file')
  return changes.filter(change => {
    if (change.kind === 'file') return true
    const prefix = `${change.path.replace(/\\/g, '/').replace(/\/+$/, '')}/`
    const hasNestedFile = files.some(f => f.path.replace(/\\/g, '/').startsWith(prefix))
    return !hasNestedFile
  })
}

function parseStatusLines(statusResult: GitRunResult): GitChangeDto[] {
  const changes: GitChangeDto[] = []
  if (!statusResult.ok) return changes

  for (const line of statusResult.stdout.split(/\r?\n/).filter(Boolean)) {
    const rawStatus = line.slice(0, 2)
    const { status, staged, unstaged } = parsePorcelainColumns(rawStatus)
    let path = line.slice(3).trim()
    if (!path) continue

    if (path.includes(' -> ')) {
      path = path.split(' -> ').pop()!.trim()
    }

    const kind: 'file' | 'directory' = path.endsWith('/') ? 'directory' : 'file'
    changes.push({ status, path, kind, staged, unstaged })
  }

  return filterFlattenedChanges(changes)
}

async function loadSyncStatus(gitRoot: string): Promise<GitSyncStatusDto | undefined> {
  const upstream = await runGit(gitRoot, ['rev-parse', '--abbrev-ref', '@{u}'])
  if (!upstream.ok || !upstream.stdout.trim()) {
    return { ahead: 0, behind: 0 }
  }

  const trackingBranch = upstream.stdout.trim()
  const countResult = await runGit(gitRoot, ['rev-list', '--left-right', '--count', 'HEAD...@{u}'])
  if (!countResult.ok) {
    return { trackingBranch, ahead: 0, behind: 0 }
  }

  const [aheadStr, behindStr] = countResult.stdout.trim().split(/\s+/)
  return {
    trackingBranch,
    ahead: Number.parseInt(aheadStr ?? '0', 10) || 0,
    behind: Number.parseInt(behindStr ?? '0', 10) || 0,
  }
}

function parseRemotes(remoteResult: GitRunResult): GitRemoteDto[] {
  const remotes: GitRemoteDto[] = []
  if (!remoteResult.ok) return remotes

  const remoteMap = new Map<string, GitRemoteDto>()
  for (const line of remoteResult.stdout.split(/\r?\n/).filter(Boolean)) {
    const parts = line.split(/\s+/)
    if (parts.length < 3) continue
    const name = parts[0]!
    const url = parts[1]!
    const kind = parts[2]!
    const existing = remoteMap.get(name) ?? { name, fetchUrl: url }
    if (kind === '(fetch)') existing.fetchUrl = url
    if (kind === '(push)') existing.pushUrl = url
    remoteMap.set(name, existing)
  }
  remotes.push(...remoteMap.values())
  return remotes
}

export async function loadGitInfo(
  workspaceRoot: string,
  scope: GitInfoLoadScope = 'full',
): Promise<GitInfoDto> {
  const gitRoot = await resolveGitRootFromWorkspace(workspaceRoot)
  if (!gitRoot) {
    return { isRepo: false, remotes: [], changes: [] }
  }

  if (scope === 'changes') {
    const [branchResult, statusResult] = await Promise.all([
      runGit(gitRoot, ['rev-parse', '--abbrev-ref', 'HEAD']),
      runGit(gitRoot, ['status', '--porcelain', '-uall']),
    ])

    if (!branchResult.ok && !statusResult.ok) {
      return {
        isRepo: true,
        gitRoot,
        remotes: [],
        changes: [],
        error: branchResult.stderr.trim() || statusResult.stderr.trim() || '无法读取 Git 状态',
      }
    }

    return {
      isRepo: true,
      gitRoot,
      branch: branchResult.ok ? branchResult.stdout.trim() : undefined,
      remotes: [],
      changes: parseStatusLines(statusResult),
    }
  }

  if (scope === 'sync') {
    const [branchResult, remoteResult, syncStatus] = await Promise.all([
      runGit(gitRoot, ['rev-parse', '--abbrev-ref', 'HEAD']),
      runGit(gitRoot, ['remote', '-v']),
      loadSyncStatus(gitRoot),
    ])

    return {
      isRepo: true,
      gitRoot,
      branch: branchResult.ok ? branchResult.stdout.trim() : undefined,
      remotes: parseRemotes(remoteResult),
      changes: [],
      syncStatus,
    }
  }

  const [branchResult, remoteResult, statusResult, syncStatus] = await Promise.all([
    runGit(gitRoot, ['rev-parse', '--abbrev-ref', 'HEAD']),
    runGit(gitRoot, ['remote', '-v']),
    runGit(gitRoot, ['status', '--porcelain', '-uall']),
    loadSyncStatus(gitRoot),
  ])
  const branch = branchResult.ok ? branchResult.stdout.trim() : undefined
  const filteredChanges = parseStatusLines(statusResult)

  if (!branchResult.ok && !statusResult.ok) {
    return {
      isRepo: true,
      gitRoot,
      remotes: parseRemotes(remoteResult),
      changes: filteredChanges,
      syncStatus,
      error: branchResult.stderr.trim() || statusResult.stderr.trim() || '无法读取 Git 状态',
    }
  }

  return {
    isRepo: true,
    gitRoot,
    branch,
    remotes: parseRemotes(remoteResult),
    changes: filteredChanges,
    syncStatus,
  }
}
