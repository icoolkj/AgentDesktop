import { relativeGitPath } from '../aiTracking/gitContext'
import { resolveGitRootFromWorkspace, runGit, runGitInWorkspace, type GitOpResult } from './gitRunner'

export type GitDiscardScope = 'head' | 'worktree'

function relPathsFromAbs(gitRoot: string, absPaths: string[]): string[] {
  return absPaths
    .map(abs => relativeGitPath(gitRoot, abs).replace(/\\/g, '/'))
    .filter(Boolean)
}

async function hasGitHead(gitRoot: string): Promise<boolean> {
  const result = await runGit(gitRoot, ['rev-parse', '--verify', 'HEAD'])
  return result.ok
}

export async function gitStage(workspaceRoot: string, absPaths: string[]): Promise<GitOpResult> {
  const gitRoot = await resolveGitRootFromWorkspace(workspaceRoot)
  if (!gitRoot) return { ok: false, stdout: '', stderr: '', error: '当前工作区不是 Git 仓库' }

  const relPaths = relPathsFromAbs(gitRoot, absPaths)
  if (!relPaths.length) return { ok: false, stdout: '', stderr: '', error: '没有可暂存的文件' }

  return runGitInWorkspace(workspaceRoot, ['add', '--', ...relPaths], '暂存失败')
}

export async function gitStageAll(workspaceRoot: string): Promise<GitOpResult> {
  return runGitInWorkspace(workspaceRoot, ['add', '-A'], '暂存全部失败')
}

export async function gitUnstage(workspaceRoot: string, absPaths: string[]): Promise<GitOpResult> {
  const gitRoot = await resolveGitRootFromWorkspace(workspaceRoot)
  if (!gitRoot) return { ok: false, stdout: '', stderr: '', error: '当前工作区不是 Git 仓库' }

  const relPaths = relPathsFromAbs(gitRoot, absPaths)
  if (!relPaths.length) return { ok: false, stdout: '', stderr: '', error: '没有可取消暂存的文件' }

  return runGitInWorkspace(workspaceRoot, ['restore', '--staged', '--', ...relPaths], '取消暂存失败')
}

export async function gitUnstageAll(workspaceRoot: string): Promise<GitOpResult> {
  const gitRoot = await resolveGitRootFromWorkspace(workspaceRoot)
  if (!gitRoot) return { ok: false, stdout: '', stderr: '', error: '当前工作区不是 Git 仓库' }

  // 用 `.` 取消全部暂存；勿写 `restore --staged HEAD`（HEAD 会被当成 pathspec）
  return runGitInWorkspace(workspaceRoot, ['restore', '--staged', '--', '.'], '取消全部暂存失败')
}

export async function gitCommit(workspaceRoot: string, message: string): Promise<GitOpResult> {
  const trimmed = message.trim()
  if (!trimmed) {
    return { ok: false, stdout: '', stderr: '', error: '请输入提交说明' }
  }
  return runGitInWorkspace(workspaceRoot, ['commit', '-m', trimmed], '提交失败')
}

export async function gitFetch(workspaceRoot: string, remote?: string): Promise<GitOpResult> {
  const args = remote ? ['fetch', remote] : ['fetch']
  return runGitInWorkspace(workspaceRoot, args, '获取远程更新失败')
}

export async function gitPull(workspaceRoot: string, remote?: string, branch?: string): Promise<GitOpResult> {
  const args = ['pull']
  if (remote) args.push(remote)
  if (branch) args.push(branch)
  return runGitInWorkspace(workspaceRoot, args, '拉取失败')
}

export async function gitPush(workspaceRoot: string, remote?: string, branch?: string): Promise<GitOpResult> {
  const args = ['push']
  if (remote && branch) {
    args.push(remote, branch)
  } else if (remote) {
    args.push(remote)
  }
  return runGitInWorkspace(workspaceRoot, args, '推送失败')
}

export async function gitDiscard(
  workspaceRoot: string,
  absPaths: string[],
  scope: GitDiscardScope = 'head',
): Promise<GitOpResult> {
  const gitRoot = await resolveGitRootFromWorkspace(workspaceRoot)
  if (!gitRoot) return { ok: false, stdout: '', stderr: '', error: '当前工作区不是 Git 仓库' }

  const relPaths = relPathsFromAbs(gitRoot, absPaths)
  if (!relPaths.length) return { ok: false, stdout: '', stderr: '', error: '没有可还原的文件' }

  const untracked: string[] = []
  const tracked: string[] = []

  for (const rel of relPaths) {
    const check = await runGit(gitRoot, ['ls-files', '--error-unmatch', '--', rel])
    if (check.ok) tracked.push(rel)
    else untracked.push(rel)
  }

  if (scope === 'head') {
    if (tracked.length) {
      const hasHead = await hasGitHead(gitRoot)
      if (hasHead) {
        const restore = await runGitInWorkspace(
          workspaceRoot,
          ['restore', '--source=HEAD', '--staged', '--worktree', '--', ...tracked],
          '还原失败',
        )
        if (!restore.ok) return restore
      } else {
        const unstage = await runGitInWorkspace(
          workspaceRoot,
          ['restore', '--staged', '--', ...tracked],
          '还原失败',
        )
        if (!unstage.ok) return unstage
      }
    }
  } else if (tracked.length) {
    const restore = await runGitInWorkspace(
      workspaceRoot,
      ['restore', '--worktree', '--', ...tracked],
      '还原工作区失败',
    )
    if (!restore.ok) return restore
  }

  if (untracked.length) {
    const clean = await runGitInWorkspace(
      workspaceRoot,
      ['clean', '-fd', '--', ...untracked],
      '删除未跟踪文件失败',
    )
    if (!clean.ok) return clean
  }

  return { ok: true, stdout: '', stderr: '' }
}
