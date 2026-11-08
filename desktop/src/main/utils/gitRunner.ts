import { spawn } from 'node:child_process'
import { access } from 'node:fs/promises'
import { join } from 'node:path'

import { resolveGitFileContext } from '../aiTracking/gitContext'

const gitRootCache = new Map<string, string | null>()

export interface GitRunResult {
  ok: boolean
  stdout: string
  stderr: string
  code: number | null
}

export interface GitOpResult {
  ok: boolean
  stdout: string
  stderr: string
  error?: string
}

export async function resolveGitRootFromWorkspace(workspaceRoot: string): Promise<string | null> {
  const key = workspaceRoot.replace(/\\/g, '/').toLowerCase()
  if (gitRootCache.has(key)) {
    return gitRootCache.get(key) ?? null
  }

  const ctx = await resolveGitFileContext(workspaceRoot, workspaceRoot)
  gitRootCache.set(key, ctx.gitRoot)
  return ctx.gitRoot
}

export function clearGitRootCache(workspaceRoot?: string): void {
  if (workspaceRoot) {
    gitRootCache.delete(workspaceRoot.replace(/\\/g, '/').toLowerCase())
    return
  }
  gitRootCache.clear()
}

async function isGitBusy(cwd: string): Promise<boolean> {
  const lockPaths = [
    join(cwd, '.git', 'index.lock'),
    join(cwd, '.git', 'shallow.lock'),
    join(cwd, '.git', 'HEAD.lock'),
  ]
  for (const lockPath of lockPaths) {
    try {
      await access(lockPath)
      return true
    } catch {
      // lock file absent
    }
  }
  return false
}

function spawnGit(cwd: string, args: string[], timeoutMs: number): Promise<GitRunResult> {
  return new Promise(resolve => {
    const child = spawn('git', args, { cwd, windowsHide: true })
    let stdout = ''
    let stderr = ''
    let settled = false

    const timer = setTimeout(() => {
      if (settled) return
      settled = true
      child.kill()
      resolve({
        ok: false,
        stdout,
        stderr: stderr || `git ${args.join(' ')} timed out`,
        code: null,
      })
    }, timeoutMs)

    child.stdout.on('data', (chunk: Buffer) => {
      stdout += chunk.toString('utf8')
    })
    child.stderr.on('data', (chunk: Buffer) => {
      stderr += chunk.toString('utf8')
    })
    child.on('error', err => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      resolve({ ok: false, stdout: '', stderr: err.message, code: null })
    })
    child.on('close', code => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      resolve({ ok: code === 0, stdout, stderr, code })
    })
  })
}

export async function runGit(cwd: string, args: string[], timeoutMs = 120_000): Promise<GitRunResult> {
  if (await isGitBusy(cwd)) {
    return {
      ok: false,
      stdout: '',
      stderr: 'Git 仓库正被其他程序占用（如 IDEA 正在提交），请稍后重试',
      code: null,
    }
  }
  return spawnGit(cwd, args, timeoutMs)
}

export function toGitOpResult(result: GitRunResult, fallbackError: string): GitOpResult {
  if (result.ok) {
    return { ok: true, stdout: result.stdout, stderr: result.stderr }
  }
  return {
    ok: false,
    stdout: result.stdout,
    stderr: result.stderr,
    error: result.stderr.trim() || result.stdout.trim() || fallbackError,
  }
}

export async function runGitInWorkspace(
  workspaceRoot: string,
  args: string[],
  fallbackError: string,
): Promise<GitOpResult> {
  const gitRoot = await resolveGitRootFromWorkspace(workspaceRoot)
  if (!gitRoot) {
    return { ok: false, stdout: '', stderr: '', error: '当前工作区不是 Git 仓库' }
  }
  const result = await runGit(gitRoot, args)
  return toGitOpResult(result, fallbackError)
}
