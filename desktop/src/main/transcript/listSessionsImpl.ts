/**
 * Ported from Claude Code listSessionsImpl.ts
 * @see claude_code_src-master/src/utils/listSessionsImpl.ts
 */
import type { Dirent } from 'node:fs'
import { readdir, stat } from 'node:fs/promises'
import { basename, join } from 'node:path'
import type { TranscriptSessionSummary } from '@agent-desktop/shared-types'
import {
  extractFirstPromptFromHead,
  extractJsonStringField,
  extractLastJsonStringField,
  findProjectDir,
  getProjectsDir,
  listCandidates,
  readSessionLite,
  canonicalizePath,
  sanitizePath,
  MAX_SANITIZED_LENGTH,
  type LiteSessionFile,
  type SessionCandidate,
} from './claudeSessionPortable'
import { getWorktreePathsPortable } from './getWorktreePathsPortable'

const READ_BATCH_SIZE = 32

export function parseSessionInfoFromLite(
  sessionId: string,
  lite: LiteSessionFile,
  projectPath?: string,
): TranscriptSessionSummary | null {
  const { head, tail, mtime, size } = lite

  const firstNewline = head.indexOf('\n')
  const firstLine = firstNewline >= 0 ? head.slice(0, firstNewline) : head
  if (
    firstLine.includes('"isSidechain":true') ||
    firstLine.includes('"isSidechain": true')
  ) {
    return null
  }

  const customTitle =
    extractLastJsonStringField(tail, 'customTitle') ||
    extractLastJsonStringField(head, 'customTitle') ||
    extractLastJsonStringField(tail, 'aiTitle') ||
    extractLastJsonStringField(head, 'aiTitle') ||
    undefined
  const firstPrompt = extractFirstPromptFromHead(head) || undefined
  const summary =
    customTitle ||
    extractLastJsonStringField(tail, 'lastPrompt') ||
    extractLastJsonStringField(tail, 'summary') ||
    firstPrompt

  if (!summary) return null

  const sessionCwd = extractJsonStringField(head, 'cwd') || projectPath || undefined

  return {
    sessionId,
    title: summary,
    firstPrompt,
    cwd: sessionCwd,
    updatedAt: new Date(mtime).toISOString(),
    fileSize: size,
    messageCount: 0,
  }
}

function compareDesc(a: SessionCandidate, b: SessionCandidate): number {
  if (b.mtime !== a.mtime) return b.mtime - a.mtime
  return b.sessionId < a.sessionId ? -1 : b.sessionId > a.sessionId ? 1 : 0
}

async function readCandidate(c: SessionCandidate): Promise<TranscriptSessionSummary | null> {
  const lite = await readSessionLite(c.filePath)
  if (!lite) return null
  const info = parseSessionInfoFromLite(c.sessionId, lite, c.projectPath)
  if (!info) return null
  if (c.mtime) info.updatedAt = new Date(c.mtime).toISOString()
  return info
}

async function applySortAndLimit(
  candidates: SessionCandidate[],
  limit: number,
  offset: number,
): Promise<TranscriptSessionSummary[]> {
  candidates.sort(compareDesc)
  const sessions: TranscriptSessionSummary[] = []
  const want = limit > 0 ? limit : Infinity
  let skipped = 0
  const seen = new Set<string>()

  for (let i = 0; i < candidates.length && sessions.length < want; ) {
    const batchEnd = Math.min(i + READ_BATCH_SIZE, candidates.length)
    const batch = candidates.slice(i, batchEnd)
    const results = await Promise.all(batch.map(readCandidate))
    for (let j = 0; j < results.length && sessions.length < want; j++) {
      i++
      const r = results[j]
      if (!r) continue
      if (seen.has(r.sessionId)) continue
      seen.add(r.sessionId)
      if (skipped < offset) {
        skipped++
        continue
      }
      sessions.push(r)
    }
  }
  return sessions
}

async function readAllAndSort(candidates: SessionCandidate[]): Promise<TranscriptSessionSummary[]> {
  const all = await Promise.all(candidates.map(readCandidate))
  const byId = new Map<string, TranscriptSessionSummary>()
  for (const s of all) {
    if (!s) continue
    const existing = byId.get(s.sessionId)
    const sTime = Date.parse(s.updatedAt)
    const eTime = existing ? Date.parse(existing.updatedAt) : -1
    if (!existing || sTime > eTime) byId.set(s.sessionId, s)
  }
  const sessions = [...byId.values()]
  sessions.sort((a, b) => {
    const bt = Date.parse(b.updatedAt)
    const at = Date.parse(a.updatedAt)
    if (bt !== at) return bt - at
    return b.sessionId < a.sessionId ? -1 : b.sessionId > a.sessionId ? 1 : 0
  })
  return sessions
}

async function gatherProjectCandidates(
  dir: string,
  includeWorktrees: boolean,
  doStat: boolean,
): Promise<SessionCandidate[]> {
  const canonicalDir = await canonicalizePath(dir)

  let worktreePaths: string[]
  if (includeWorktrees) {
    try {
      worktreePaths = await getWorktreePathsPortable(canonicalDir)
    } catch {
      worktreePaths = []
    }
  } else {
    worktreePaths = []
  }

  if (worktreePaths.length <= 1) {
    const projectDir = await findProjectDir(canonicalDir)
    if (!projectDir) return []
    return listCandidates(projectDir, doStat, canonicalDir)
  }

  const projectsDir = getProjectsDir()
  const caseInsensitive = process.platform === 'win32'

  const indexed = worktreePaths.map(wt => {
    const sanitized = sanitizePath(wt)
    return {
      path: wt,
      prefix: caseInsensitive ? sanitized.toLowerCase() : sanitized,
    }
  })
  indexed.sort((a, b) => b.prefix.length - a.prefix.length)

  let allDirents: Dirent[]
  try {
    allDirents = await readdir(projectsDir, { withFileTypes: true })
  } catch {
    const projectDir = await findProjectDir(canonicalDir)
    if (!projectDir) return []
    return listCandidates(projectDir, doStat, canonicalDir)
  }

  const all: SessionCandidate[] = []
  const seenDirs = new Set<string>()

  const canonicalProjectDir = await findProjectDir(canonicalDir)
  if (canonicalProjectDir) {
    const dirBase = basename(canonicalProjectDir)
    seenDirs.add(caseInsensitive ? dirBase.toLowerCase() : dirBase)
    all.push(...(await listCandidates(canonicalProjectDir, doStat, canonicalDir)))
  }

  for (const dirent of allDirents) {
    if (!dirent.isDirectory()) continue
    const dirName = caseInsensitive ? dirent.name.toLowerCase() : dirent.name
    if (seenDirs.has(dirName)) continue

    for (const { path: wtPath, prefix } of indexed) {
      const isMatch =
        dirName === prefix ||
        (prefix.length >= MAX_SANITIZED_LENGTH && dirName.startsWith(`${prefix}-`))
      if (isMatch) {
        seenDirs.add(dirName)
        all.push(
          ...(await listCandidates(join(projectsDir, dirent.name), doStat, wtPath)),
        )
        break
      }
    }
  }

  return all
}

export type ListSessionsOptions = {
  dir?: string
  limit?: number
  offset?: number
  includeWorktrees?: boolean
}

export async function listSessionsImpl(
  options?: ListSessionsOptions,
): Promise<TranscriptSessionSummary[]> {
  const { dir, limit, offset, includeWorktrees } = options ?? {}
  const off = offset ?? 0
  const doStat = (limit !== undefined && limit > 0) || off > 0

  let candidates: SessionCandidate[]
  if (dir) {
    candidates = await gatherProjectCandidates(dir, includeWorktrees ?? true, doStat)
  } else {
    const projectsDir = getProjectsDir()
    let names: string[]
    try {
      names = await readdir(projectsDir)
    } catch {
      return []
    }
    const perProject = await Promise.all(
      names.map(async name => {
        try {
          const s = await stat(join(projectsDir, name))
          if (!s.isDirectory()) return []
        } catch {
          return []
        }
        return listCandidates(join(projectsDir, name), doStat)
      }),
    )
    candidates = perProject.flat()
  }

  if (!doStat) return readAllAndSort(candidates)
  return applySortAndLimit(candidates, limit ?? 0, off)
}
