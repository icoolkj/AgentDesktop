import { execFile as execFileCb } from 'node:child_process'
import { promisify } from 'node:util'

import { relativeGitPath, resolveGitFileContext } from '../aiTracking/gitContext'
import { isGitIgnored } from './gitIgnore'

const execFileAsync = promisify(execFileCb)

export interface GitFileBaselineDto {
  status: string | null
  baseline: string | null
}

export async function loadGitFileBaseline(
  workspaceRoot: string,
  absoluteFilePath: string,
): Promise<GitFileBaselineDto> {
  if (await isGitIgnored(workspaceRoot, absoluteFilePath)) {
    return { status: null, baseline: null }
  }

  const ctx = await resolveGitFileContext(absoluteFilePath, workspaceRoot)
  if (!ctx.gitRoot) {
    return { status: null, baseline: null }
  }

  const rel = relativeGitPath(ctx.gitRoot, absoluteFilePath)
  if (!rel) {
    return { status: null, baseline: null }
  }

  const gitRel = rel.replace(/\\/g, '/')

  try {
    const { stdout: statusOut } = await execFileAsync(
      'git',
      ['status', '--porcelain', '--', gitRel],
      { cwd: ctx.gitRoot, timeout: 8000 },
    )
    const line = statusOut.split(/\r?\n/).find(Boolean)
    if (!line) {
      return { status: null, baseline: null }
    }

    const status = line.slice(0, 2).trimEnd() || '?'
    if (status === '?' || line.startsWith('??')) {
      return { status: '?', baseline: '' }
    }

    try {
      const { stdout: headContent } = await execFileAsync(
        'git',
        ['show', `HEAD:${gitRel}`],
        { cwd: ctx.gitRoot, timeout: 15000, maxBuffer: 50 * 1024 * 1024 },
      )
      return { status, baseline: headContent }
    } catch {
      return { status, baseline: '' }
    }
  } catch {
    return { status: null, baseline: null }
  }
}
