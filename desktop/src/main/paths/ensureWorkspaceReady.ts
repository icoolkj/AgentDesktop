import { writeFileSync } from 'node:fs'
import { join } from 'node:path'

import { resolveGitFileContext } from '../aiTracking/gitContext'
import { ensureProjectLayout } from './agentDesktopPaths'

export async function ensureWorkspaceReady(workspaceRoot: string): Promise<string> {
  const projectDir = ensureProjectLayout(workspaceRoot)
  const git = await resolveGitFileContext(workspaceRoot, workspaceRoot)

  writeFileSync(
    join(projectDir, 'workspace-meta.json'),
    JSON.stringify(
      {
        workspaceRoot,
        projectDir,
        gitRoot: git.gitRoot,
        gitBranch: git.branch,
        gitHead: git.head,
        updatedAt: Date.now(),
      },
      null,
      2,
    ),
    'utf8',
  )

  return projectDir
}
