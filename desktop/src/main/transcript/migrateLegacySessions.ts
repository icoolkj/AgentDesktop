/**
 * One-time migration of legacy transcript paths to Claude Code layout:
 *   ~/.agent-desktop/sessions/*.jsonl
 *   ~/.agent-desktop/projects/<id>/sessions/*.jsonl
 *   ~/.agent-desktop/projects/<id>/agent-transcripts/*.jsonl
 * → ~/.agent-desktop/projects/<id>/<sessionId>.jsonl
 */
import { existsSync } from 'node:fs'
import { mkdir, readdir, rename, stat } from 'node:fs/promises'
import { basename, dirname, join } from 'node:path'
import { getConfigHomeDir, getProjectsDir } from '../paths/agentDesktopPaths'
import { getProjectDir, resolveCwdForSession, validateUuid } from './claudeSessionPortable'

const LEGACY_SUBDIRS = ['sessions', 'agent-transcripts'] as const

async function moveJsonlIfNeeded(source: string, dest: string): Promise<boolean> {
  if (existsSync(dest)) return false
  try {
    await mkdir(dirname(dest), { recursive: true })
    await rename(source, dest)
    console.log('[transcript] migrated legacy session:', basename(source))
    return true
  } catch (error) {
    console.warn('[transcript] legacy migration skipped:', source, error)
    return false
  }
}

async function migrateDirJsonl(sourceDir: string, destProjectDir: string): Promise<number> {
  let moved = 0
  let names: string[]
  try {
    names = await readdir(sourceDir)
  } catch {
    return 0
  }

  for (const name of names) {
    if (!name.endsWith('.jsonl')) continue
    const sessionId = validateUuid(name.slice(0, -6))
    if (!sessionId) continue
    const source = join(sourceDir, name)
    try {
      const info = await stat(source)
      if (!info.isFile() || info.size === 0) continue
    } catch {
      continue
    }
    const dest = join(destProjectDir, name)
    if (await moveJsonlIfNeeded(source, dest)) moved += 1
  }
  return moved
}

export async function migrateLegacySessions(): Promise<number> {
  let total = 0
  const home = getConfigHomeDir()
  const legacyHomeSessions = join(home, 'sessions')
  const noWorkspaceDir = getProjectDir(resolveCwdForSession(null))
  total += await migrateDirJsonl(legacyHomeSessions, noWorkspaceDir)

  const projectsDir = getProjectsDir()
  let projectNames: string[]
  try {
    projectNames = await readdir(projectsDir)
  } catch {
    return total
  }

  for (const projectName of projectNames) {
    const projectDir = join(projectsDir, projectName)
    try {
      const info = await stat(projectDir)
      if (!info.isDirectory()) continue
    } catch {
      continue
    }
    for (const sub of LEGACY_SUBDIRS) {
      total += await migrateDirJsonl(join(projectDir, sub), projectDir)
    }
  }

  if (total > 0) {
    console.log(`[transcript] migrated ${total} legacy session file(s)`)
  }
  return total
}
