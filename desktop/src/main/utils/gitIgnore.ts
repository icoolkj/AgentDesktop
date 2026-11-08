import { spawn } from 'node:child_process'

import { relativeGitPath, resolveGitFileContext } from '../aiTracking/gitContext'

const gitRootCache = new Map<string, string | null>()

async function resolveGitRootCached(workspaceRoot: string): Promise<string | null> {
  const key = workspaceRoot.replace(/\\/g, '/').toLowerCase()
  if (gitRootCache.has(key)) return gitRootCache.get(key) ?? null

  const ctx = await resolveGitFileContext(workspaceRoot, workspaceRoot)
  const root = ctx.gitRoot ?? null
  gitRootCache.set(key, root)
  return root
}

function gitCheckIgnoreStdin(gitRoot: string, stdin: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const child = spawn('git', ['check-ignore', '--stdin'], { cwd: gitRoot, windowsHide: true })
    let stdout = ''
    child.stdout.on('data', (chunk: Buffer) => {
      stdout += chunk.toString('utf8')
    })
    child.on('error', reject)
    child.on('close', code => {
      if (code === 0 || code === 1) resolve(stdout)
      else reject(new Error(`git check-ignore exited with ${code}`))
    })
    child.stdin.write(stdin)
    child.stdin.end()
  })
}

export async function isGitIgnored(workspaceRoot: string, absoluteFilePath: string): Promise<boolean> {
  const results = await batchGitIgnored(workspaceRoot, [absoluteFilePath])
  return results[absoluteFilePath] ?? false
}

/** One git process for many paths (via --stdin). */
export async function batchGitIgnored(
  workspaceRoot: string,
  absolutePaths: string[],
): Promise<Record<string, boolean>> {
  const unique = [...new Set(absolutePaths.filter(Boolean))]
  if (!unique.length) return {}

  const gitRoot = await resolveGitRootCached(workspaceRoot)
  if (!gitRoot) {
    return Object.fromEntries(unique.map(path => [path, false]))
  }

  const relByAbs = new Map<string, string>()
  for (const abs of unique) {
    const rel = relativeGitPath(gitRoot, abs).replace(/\\/g, '/')
    if (rel) relByAbs.set(abs, rel)
  }

  if (!relByAbs.size) {
    return Object.fromEntries(unique.map(path => [path, false]))
  }

  const stdin = [...relByAbs.values()].join('\n')

  try {
    const stdout = await gitCheckIgnoreStdin(gitRoot, `${stdin}\n`)
    const ignoredRel = new Set(
      stdout
        .split(/\r?\n/)
        .map(line => line.trim())
        .filter(Boolean),
    )

    const result: Record<string, boolean> = {}
    for (const abs of unique) {
      const rel = relByAbs.get(abs)
      result[abs] = rel != null && ignoredRel.has(rel)
    }
    return result
  } catch {
    return Object.fromEntries(unique.map(path => [path, false]))
  }
}

export function clearGitRootCache(): void {
  gitRootCache.clear()
}
