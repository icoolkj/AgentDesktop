/**
 * Ported from Claude Code sessionStoragePortable.ts (path + lite read + resolve).
 * @see claude_code_src-master/src/utils/sessionStoragePortable.ts
 */
import { open, readdir, realpath, stat } from 'node:fs/promises'
import { join } from 'node:path'
import { getWorktreePathsPortable } from './getWorktreePathsPortable'
import {
  getConfigHomeDir,
  getProjectsDir,
  MAX_SANITIZED_LENGTH,
  sanitizePath,
} from '../paths/agentDesktopPaths'

export { getConfigHomeDir, getProjectsDir, sanitizePath, MAX_SANITIZED_LENGTH } from '../paths/agentDesktopPaths'

export const LITE_READ_BUF_SIZE = 65536

const uuidRegex =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export function validateUuid(maybeUuid: unknown): string | null {
  if (typeof maybeUuid !== 'string') return null
  return uuidRegex.test(maybeUuid) ? maybeUuid : null
}

export function normalizeProjectPath(path: string): string {
  return path.replace(/\\/g, '/').replace(/\/+$/, '').normalize('NFC').toLowerCase()
}

export function projectPathsMatch(a: string | undefined, b: string | undefined): boolean {
  if (!a?.trim() || !b?.trim()) return false
  return normalizeProjectPath(a) === normalizeProjectPath(b)
}

export function getProjectDir(projectPath: string): string {
  return join(getProjectsDir(), sanitizePath(projectPath))
}

export async function canonicalizePath(dir: string): Promise<string> {
  try {
    return (await realpath(dir)).normalize('NFC')
  } catch {
    return dir.normalize('NFC')
  }
}

export async function findProjectDir(projectPath: string): Promise<string | undefined> {
  const exact = getProjectDir(projectPath)
  try {
    await readdir(exact)
    return exact
  } catch {
    const sanitized = sanitizePath(projectPath)
    if (sanitized.length <= MAX_SANITIZED_LENGTH) {
      return undefined
    }
    const prefix = sanitized.slice(0, MAX_SANITIZED_LENGTH)
    const projectsDir = getProjectsDir()
    try {
      const dirents = await readdir(projectsDir, { withFileTypes: true })
      const match = dirents.find(d => d.isDirectory() && d.name.startsWith(`${prefix}-`))
      return match ? join(projectsDir, match.name) : undefined
    } catch {
      return undefined
    }
  }
}

export function unescapeJsonString(raw: string): string {
  if (!raw.includes('\\')) return raw
  try {
    return JSON.parse(`"${raw}"`) as string
  } catch {
    return raw
  }
}

export function extractJsonStringField(text: string, key: string): string | undefined {
  const patterns = [`"${key}":"`, `"${key}": "`]
  for (const pattern of patterns) {
    const idx = text.indexOf(pattern)
    if (idx < 0) continue

    const valueStart = idx + pattern.length
    let i = valueStart
    while (i < text.length) {
      if (text[i] === '\\') {
        i += 2
        continue
      }
      if (text[i] === '"') {
        return unescapeJsonString(text.slice(valueStart, i))
      }
      i++
    }
  }
  return undefined
}

export function extractLastJsonStringField(text: string, key: string): string | undefined {
  const patterns = [`"${key}":"`, `"${key}": "`]
  let lastValue: string | undefined
  for (const pattern of patterns) {
    let searchFrom = 0
    while (true) {
      const idx = text.indexOf(pattern, searchFrom)
      if (idx < 0) break

      const valueStart = idx + pattern.length
      let i = valueStart
      while (i < text.length) {
        if (text[i] === '\\') {
          i += 2
          continue
        }
        if (text[i] === '"') {
          lastValue = unescapeJsonString(text.slice(valueStart, i))
          break
        }
        i++
      }
      searchFrom = i + 1
    }
  }
  return lastValue
}

const SKIP_FIRST_PROMPT_PATTERN =
  /^(?:\s*<[a-z][\w-]*[\s>]|\[Request interrupted by user[^\]]*\])/
const COMMAND_NAME_RE = /<command-name>(.*?)<\/command-name>/

export function extractFirstPromptFromHead(head: string): string {
  let start = 0
  let commandFallback = ''
  while (start < head.length) {
    const newlineIdx = head.indexOf('\n', start)
    const line = newlineIdx >= 0 ? head.slice(start, newlineIdx) : head.slice(start)
    start = newlineIdx >= 0 ? newlineIdx + 1 : head.length

    if (!line.includes('"type":"user"') && !line.includes('"type": "user"')) continue
    if (line.includes('"tool_result"')) continue
    if (line.includes('"isMeta":true') || line.includes('"isMeta": true')) continue
    if (line.includes('"isCompactSummary":true') || line.includes('"isCompactSummary": true')) continue

    try {
      const entry = JSON.parse(line) as Record<string, unknown>
      if (entry.type !== 'user') continue

      const message = entry.message as Record<string, unknown> | undefined
      if (!message) continue

      const content = message.content
      const texts: string[] = []
      if (typeof content === 'string') {
        texts.push(content)
      } else if (Array.isArray(content)) {
        for (const block of content as Record<string, unknown>[]) {
          if (block.type === 'text' && typeof block.text === 'string') {
            texts.push(block.text as string)
          }
        }
      }

      for (const raw of texts) {
        let result = raw.replace(/\n/g, ' ').trim()
        if (!result) continue

        const cmdMatch = COMMAND_NAME_RE.exec(result)
        if (cmdMatch) {
          if (!commandFallback) commandFallback = cmdMatch[1]!
          continue
        }

        const bashMatch = /<bash-input>([\s\S]*?)<\/bash-input>/.exec(result)
        if (bashMatch) return `! ${bashMatch[1]!.trim()}`

        if (SKIP_FIRST_PROMPT_PATTERN.test(result)) continue

        if (result.length > 200) {
          result = `${result.slice(0, 200).trim()}\u2026`
        }
        return result
      }
    } catch {
      continue
    }
  }
  if (commandFallback) return commandFallback
  return ''
}

export type LiteSessionFile = {
  head: string
  tail: string
  mtime: number
  size: number
}

export async function readSessionLite(filePath: string): Promise<LiteSessionFile | null> {
  try {
    const fh = await open(filePath, 'r')
    try {
      const fileStat = await fh.stat()
      const buf = Buffer.allocUnsafe(LITE_READ_BUF_SIZE)
      const headResult = await fh.read(buf, 0, LITE_READ_BUF_SIZE, 0)
      if (headResult.bytesRead === 0) return null

      const head = buf.toString('utf8', 0, headResult.bytesRead)
      const tailOffset = Math.max(0, fileStat.size - LITE_READ_BUF_SIZE)
      let tail = head
      if (tailOffset > 0) {
        const tailResult = await fh.read(buf, 0, LITE_READ_BUF_SIZE, tailOffset)
        tail = buf.toString('utf8', 0, tailResult.bytesRead)
      }

      return {
        mtime: fileStat.mtime.getTime(),
        size: fileStat.size,
        head,
        tail,
      }
    } finally {
      await fh.close()
    }
  } catch {
    return null
  }
}

export type SessionFileResolution = {
  filePath: string
  projectPath: string | undefined
  fileSize: number
}

/** Claude Code resolveSessionFilePath — stat-only, size>0, scan all projects when dir omitted. */
export async function resolveSessionFilePath(
  sessionId: string,
  dir?: string,
): Promise<SessionFileResolution | undefined> {
  const fileName = `${sessionId}.jsonl`

  if (dir) {
    const canonical = await canonicalizePath(dir)
    const projectDir = await findProjectDir(canonical)
    if (projectDir) {
      const filePath = join(projectDir, fileName)
      try {
        const s = await stat(filePath)
        if (s.size > 0) return { filePath, projectPath: canonical, fileSize: s.size }
      } catch {
        // ENOENT
      }
    }
    let worktreePaths: string[]
    try {
      worktreePaths = await getWorktreePathsPortable(canonical)
    } catch {
      worktreePaths = []
    }
    for (const wt of worktreePaths) {
      if (wt === canonical) continue
      const wtProjectDir = await findProjectDir(wt)
      if (!wtProjectDir) continue
      const filePath = join(wtProjectDir, fileName)
      try {
        const s = await stat(filePath)
        if (s.size > 0) return { filePath, projectPath: wt, fileSize: s.size }
      } catch {
        // ENOENT
      }
    }
    return undefined
  }

  const projectsDir = getProjectsDir()
  let names: string[]
  try {
    names = await readdir(projectsDir)
  } catch {
    return undefined
  }
  for (const name of names) {
    const filePath = join(projectsDir, name, fileName)
    try {
      const s = await stat(filePath)
      if (s.size > 0) return { filePath, projectPath: undefined, fileSize: s.size }
    } catch {
      // keep scanning
    }
  }
  return undefined
}

export type SessionCandidate = {
  sessionId: string
  filePath: string
  mtime: number
  projectPath?: string
}

/** Claude listSessionsImpl.listCandidates — UUID-only session ids. */
export async function listCandidates(
  projectDir: string,
  doStat: boolean,
  projectPath?: string,
): Promise<SessionCandidate[]> {
  let names: string[]
  try {
    names = await readdir(projectDir)
  } catch {
    return []
  }

  const results = await Promise.all(
    names.map(async (name): Promise<SessionCandidate | null> => {
      if (!name.endsWith('.jsonl')) return null
      const sessionId = validateUuid(name.slice(0, -6))
      if (!sessionId) return null
      const filePath = join(projectDir, name)
      if (!doStat) return { sessionId, filePath, mtime: 0, projectPath }
      try {
        const s = await stat(filePath)
        if (s.size === 0) return null
        return { sessionId, filePath, mtime: s.mtime.getTime(), projectPath }
      } catch {
        return null
      }
    }),
  )

  return results.filter((c): c is SessionCandidate => c !== null)
}

export function resolveCwdForSession(workspaceRoot?: string | null): string {
  if (workspaceRoot?.trim()) return workspaceRoot.trim()
  return join(getConfigHomeDir(), 'no-workspace')
}

export function transcriptPathForSession(sessionId: string, cwd: string): string {
  return join(getProjectDir(cwd), `${sessionId}.jsonl`)
}
