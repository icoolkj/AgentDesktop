import { execFile as execFileCb } from 'node:child_process'
import { promisify } from 'node:util'

const execFileAsync = promisify(execFileCb)

export interface GitFileContext {
  gitRoot: string | null
  gitPath: string | null
  branch: string | null
  head: string | null
}

/** Cursor-style gitPath: forward slashes, lowercase drive letter on Windows. */
export function toGitPathStyle(absolutePath: string): string {
  let normalized = absolutePath.replace(/\\/g, '/')
  if (/^[A-Za-z]:/.test(normalized)) {
    normalized = `${normalized[0]!.toLowerCase()}${normalized.slice(1)}`
  }
  return normalized
}

export function relativeGitPath(gitRoot: string, absolutePath: string): string {
  const root = toGitPathStyle(gitRoot).replace(/\/+$/, '')
  const file = toGitPathStyle(absolutePath)
  if (file.toLowerCase() === root.toLowerCase()) return ''
  const prefix = `${root}/`
  if (file.toLowerCase().startsWith(prefix.toLowerCase())) {
    return file.slice(prefix.length)
  }
  return file
}

export async function resolveGitFileContext(
  absoluteFilePath: string,
  cwd?: string,
): Promise<GitFileContext> {
  const startDir =
    cwd ?? (absoluteFilePath.includes('\\') || absoluteFilePath.includes('/')
      ? absoluteFilePath.replace(/[\\/][^\\/]+$/, '')
      : absoluteFilePath)

  try {
    const { stdout: rootOut } = await execFileAsync(
      'git',
      ['rev-parse', '--show-toplevel'],
      { cwd: startDir, timeout: 5000 },
    )
    const gitRoot = rootOut.trim()
    if (!gitRoot) {
      return { gitRoot: null, gitPath: null, branch: null, head: null }
    }

    const [{ stdout: branchOut }, { stdout: headOut }] = await Promise.all([
      execFileAsync('git', ['rev-parse', '--abbrev-ref', 'HEAD'], { cwd: gitRoot, timeout: 5000 }),
      execFileAsync('git', ['rev-parse', 'HEAD'], { cwd: gitRoot, timeout: 5000 }),
    ])

    const rel = relativeGitPath(gitRoot, absoluteFilePath)
    const gitPath = rel ? `${toGitPathStyle(gitRoot)}/${rel}` : toGitPathStyle(gitRoot)

    return {
      gitRoot,
      gitPath,
      branch: branchOut.trim() || null,
      head: headOut.trim() || null,
    }
  } catch {
    return {
      gitRoot: null,
      gitPath: toGitPathStyle(absoluteFilePath),
      branch: null,
      head: null,
    }
  }
}

export async function listCommitsSince(
  gitRoot: string,
  sinceCommit: string | null,
  limit = 50,
): Promise<string[]> {
  try {
    const args =
      sinceCommit != null
        ? ['rev-list', '--max-count', String(limit), `${sinceCommit}..HEAD`]
        : ['rev-list', '--max-count', String(limit), 'HEAD']
    const { stdout } = await execFileAsync('git', args, { cwd: gitRoot, timeout: 15000 })
    return stdout
      .split('\n')
      .map(line => line.trim())
      .filter(Boolean)
  } catch {
    return []
  }
}
