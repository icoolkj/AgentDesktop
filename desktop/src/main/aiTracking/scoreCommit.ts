import { execFile as execFileCb } from 'node:child_process'
import { promisify } from 'node:util'

import { getAiTrackingDb } from './database'
import { hashAiCodeLine } from './hash'
import { lookupHashSource } from './recordFileChange'

const execFileAsync = promisify(execFileCb)

export interface ScoredCommitRow {
  commitHash: string
  branchName: string
  scoredAt: number
  linesAdded: number
  linesDeleted: number
  tabLinesAdded: number
  tabLinesDeleted: number
  composerLinesAdded: number
  composerLinesDeleted: number
  humanLinesAdded: number
  humanLinesDeleted: number
  blankLinesAdded: number
  blankLinesDeleted: number
  commitMessage: string | null
  commitDate: string | null
  v1AiPercentage: string | null
  v2AiPercentage: string | null
}

interface ParsedLine {
  kind: 'add' | 'del' | 'context'
  text: string
}

function parseUnifiedDiff(diff: string): ParsedLine[] {
  const lines: ParsedLine[] = []
  for (const raw of diff.split('\n')) {
    if (raw.startsWith('+++') || raw.startsWith('---') || raw.startsWith('@@')) continue
    if (raw.startsWith('+')) lines.push({ kind: 'add', text: raw.slice(1) })
    else if (raw.startsWith('-')) lines.push({ kind: 'del', text: raw.slice(1) })
    else if (raw.startsWith(' ')) lines.push({ kind: 'context', text: raw.slice(1) })
  }
  return lines
}

function formatPercentage(ai: number, total: number): string | null {
  if (total <= 0) return null
  return ((ai / total) * 100).toFixed(2)
}

export async function scoreCommit(gitRoot: string, commitHash: string): Promise<ScoredCommitRow | null> {
  const db = getAiTrackingDb()

  try {
    const [{ stdout: branchOut }, { stdout: diffOut }, { stdout: metaOut }] = await Promise.all([
      execFileAsync('git', ['rev-parse', '--abbrev-ref', 'HEAD'], { cwd: gitRoot, timeout: 5000 }),
      execFileAsync('git', ['show', commitHash, '--format=', '--unified=0', '--no-color'], {
        cwd: gitRoot,
        timeout: 30000,
        maxBuffer: 20 * 1024 * 1024,
      }),
      execFileAsync('git', ['show', commitHash, '--format=%B%n---DATE---%n%ci', '--no-patch'], {
        cwd: gitRoot,
        timeout: 5000,
      }),
    ])

    const branchName = branchOut.trim() || 'HEAD'
    const existing = db
      .prepare(`SELECT 1 FROM scored_commits WHERE commitHash = ? AND branchName = ?`)
      .get(commitHash, branchName)
    if (existing) {
      return db
        .prepare(`SELECT * FROM scored_commits WHERE commitHash = ? AND branchName = ?`)
        .get(commitHash, branchName) as ScoredCommitRow
    }

    const parsed = parseUnifiedDiff(diffOut)
    let linesAdded = 0
    let linesDeleted = 0
    let composerLinesAdded = 0
    let composerLinesDeleted = 0
    let tabLinesAdded = 0
    let tabLinesDeleted = 0
    let humanLinesAdded = 0
    let humanLinesDeleted = 0
    let blankLinesAdded = 0
    let blankLinesDeleted = 0

    for (const line of parsed) {
      const trimmed = line.text.trim()
      const isBlank = trimmed.length === 0
      const hash = isBlank ? null : hashAiCodeLine(line.text)
      const hashSource = hash != null ? lookupHashSource(db, hash) : null

      if (line.kind === 'add') {
        linesAdded++
        if (isBlank) blankLinesAdded++
        else if (hashSource === 'composer') composerLinesAdded++
        else if (hashSource === 'tab') tabLinesAdded++
        else humanLinesAdded++
      } else if (line.kind === 'del') {
        linesDeleted++
        if (isBlank) blankLinesDeleted++
        else if (hashSource === 'composer') composerLinesDeleted++
        else if (hashSource === 'tab') tabLinesDeleted++
        else humanLinesDeleted++
      }
    }

    const metaParts = metaOut.split('\n---DATE---\n')
    const commitMessage = metaParts[0]?.trim() || null
    const commitDate = metaParts[1]?.trim() || null
    const nonBlankAdded = linesAdded - blankLinesAdded
    const aiAdded = composerLinesAdded + tabLinesAdded
    const v1AiPercentage = formatPercentage(composerLinesAdded, nonBlankAdded)
    const v2AiPercentage = formatPercentage(aiAdded, linesAdded)

    const row: ScoredCommitRow = {
      commitHash,
      branchName,
      scoredAt: Date.now(),
      linesAdded,
      linesDeleted,
      tabLinesAdded,
      tabLinesDeleted,
      composerLinesAdded,
      composerLinesDeleted,
      humanLinesAdded,
      humanLinesDeleted,
      blankLinesAdded,
      blankLinesDeleted,
      commitMessage,
      commitDate,
      v1AiPercentage,
      v2AiPercentage,
    }

    db.prepare(
      `INSERT INTO scored_commits (
        commitHash, branchName, scoredAt,
        linesAdded, linesDeleted, tabLinesAdded, tabLinesDeleted,
        composerLinesAdded, composerLinesDeleted, humanLinesAdded, humanLinesDeleted,
        blankLinesAdded, blankLinesDeleted, commitMessage, commitDate,
        v1AiPercentage, v2AiPercentage
      ) VALUES (
        @commitHash, @branchName, @scoredAt,
        @linesAdded, @linesDeleted, @tabLinesAdded, @tabLinesDeleted,
        @composerLinesAdded, @composerLinesDeleted, @humanLinesAdded, @humanLinesDeleted,
        @blankLinesAdded, @blankLinesDeleted, @commitMessage, @commitDate,
        @v1AiPercentage, @v2AiPercentage
      )`,
    ).run(row)

    return row
  } catch {
    return null
  }
}

export async function scoreRecentCommits(gitRoot: string, limit = 20): Promise<ScoredCommitRow[]> {
  try {
    const { stdout } = await promisify(execFileCb)('git', ['rev-list', '--max-count', String(limit), 'HEAD'], {
      cwd: gitRoot,
      timeout: 15000,
    })
    const commits = stdout
      .split('\n')
      .map(line => line.trim())
      .filter(Boolean)

    const results: ScoredCommitRow[] = []
    for (const hash of commits) {
      const row = await scoreCommit(gitRoot, hash)
      if (row) results.push(row)
    }
    return results
  } catch {
    return []
  }
}
