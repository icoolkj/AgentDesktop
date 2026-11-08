import { resolveGitRootFromWorkspace, runGit } from './gitRunner'

const FIELD_SEP = '\x1f'
const DEFAULT_LIMIT = 50
const MAX_LIMIT = 200

export interface GitLogEntryDto {
  hash: string
  shortHash: string
  subject: string
  author: string
  authorEmail: string
  date: number
  refs: string
}

export interface GitLogResultDto {
  entries: GitLogEntryDto[]
  hasMore: boolean
  error?: string
}

function parseLogLine(line: string): GitLogEntryDto | null {
  const parts = line.split(FIELD_SEP)
  if (parts.length < 6) return null

  const [hash, shortHash, subject, author, authorEmail, dateStr, refs = ''] = parts
  if (!hash || !shortHash) return null

  const date = Number.parseInt(dateStr ?? '0', 10)
  return {
    hash,
    shortHash,
    subject: subject ?? '',
    author: author ?? '',
    authorEmail: authorEmail ?? '',
    date: Number.isFinite(date) ? date : 0,
    refs: refs.trim(),
  }
}

export async function loadGitLog(
  workspaceRoot: string,
  options?: { limit?: number; skip?: number },
): Promise<GitLogResultDto> {
  const gitRoot = await resolveGitRootFromWorkspace(workspaceRoot)
  if (!gitRoot) {
    return { entries: [], hasMore: false, error: '当前工作区不是 Git 仓库' }
  }

  const headCheck = await runGit(gitRoot, ['rev-parse', '--verify', 'HEAD'])
  if (!headCheck.ok) {
    return { entries: [], hasMore: false }
  }

  const limit = Math.min(Math.max(options?.limit ?? DEFAULT_LIMIT, 1), MAX_LIMIT)
  const skip = Math.max(options?.skip ?? 0, 0)
  const fetchCount = limit + 1

  const result = await runGit(gitRoot, [
    'log',
    `--pretty=format:%H${FIELD_SEP}%h${FIELD_SEP}%s${FIELD_SEP}%an${FIELD_SEP}%ae${FIELD_SEP}%at${FIELD_SEP}%D`,
    '--no-decorate',
    '-n',
    String(fetchCount),
    '--skip',
    String(skip),
  ])

  if (!result.ok) {
    return {
      entries: [],
      hasMore: false,
      error: result.stderr.trim() || result.stdout.trim() || '无法读取 Git 日志',
    }
  }

  const lines = result.stdout.split(/\r?\n/).filter(Boolean)
  const hasMore = lines.length > limit
  const entries = lines
    .slice(0, limit)
    .map(parseLogLine)
    .filter((entry): entry is GitLogEntryDto => entry != null)

  return { entries, hasMore }
}
