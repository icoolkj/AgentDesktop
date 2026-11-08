/**
 * Git branch for transcript session stamping (Claude insertMessageChain gitBranch).
 */
import { execFile as execFileCb } from 'node:child_process'
import { promisify } from 'node:util'

const execFileAsync = promisify(execFileCb)

export async function getGitBranch(cwd: string): Promise<string | undefined> {
  try {
    const { stdout } = await execFileAsync(
      'git',
      ['rev-parse', '--abbrev-ref', 'HEAD'],
      { cwd, timeout: 5000 },
    )
    const branch = stdout.trim()
    return branch || undefined
  } catch {
    return undefined
  }
}
