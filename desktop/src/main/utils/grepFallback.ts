import { readdir, readFile, stat } from 'node:fs/promises'
import { join } from 'node:path'
import type { GrepMatchDto, GrepRequestDto, GrepResultDto } from './grepTypes'

const SKIP_DIRS = new Set(['.git', 'node_modules', 'target', '.idea', 'dist', 'build', '.svn', '.hg'])
const MAX_FILE_BYTES = 512 * 1024
const MAX_FILES_SCANNED = 400
const DEFAULT_HEAD_LIMIT = 250

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function buildPattern(pattern: string, caseInsensitive?: boolean, wholeWord?: boolean): RegExp {
  let body = escapeRegex(pattern)
  if (wholeWord) body = `\\b${body}\\b`
  return new RegExp(body, caseInsensitive ? 'i' : '')
}

function matchesGlob(fileName: string, relPath: string, glob?: string): boolean {
  if (!glob?.trim()) return true
  for (const part of glob.split(/[,\s]+/)) {
    const g = part.trim()
    if (!g) continue
    if (g.startsWith('*.')) {
      if (fileName.endsWith(g.slice(1))) return true
      continue
    }
    if (g.includes('*')) {
      const re = new RegExp(`^${g.replace(/\./g, '\\.').replace(/\*/g, '.*')}$`)
      if (re.test(fileName) || re.test(relPath.replace(/\\/g, '/'))) return true
      continue
    }
    if (fileName === g || relPath.endsWith(g)) return true
  }
  return false
}

async function walkFiles(
  dir: string,
  rootPath: string,
  glob: string | undefined,
  onFile: (filePath: string, relPath: string) => Promise<boolean>,
): Promise<boolean> {
  let entries
  try {
    entries = await readdir(dir, { withFileTypes: true })
  } catch {
    return true
  }

  for (const entry of entries) {
    if (entry.name.startsWith('.')) continue
    const fullPath = join(dir, entry.name)
    if (entry.isDirectory()) {
      if (SKIP_DIRS.has(entry.name)) continue
      const stop = await walkFiles(fullPath, rootPath, glob, onFile)
      if (!stop) return false
      continue
    }
    if (!entry.isFile()) continue

    const relPath = fullPath.slice(rootPath.length + 1)
    if (!matchesGlob(entry.name, relPath, glob)) continue

    const stop = await onFile(fullPath, relPath)
    if (!stop) return false
  }

  return true
}

export async function runFallbackGrep(request: GrepRequestDto): Promise<GrepResultDto> {
  const { rootPath, pattern, caseInsensitive, wholeWord, glob } = request
  const headLimit = request.headLimit ?? DEFAULT_HEAD_LIMIT

  if (!pattern.trim()) {
    return { matches: [], truncated: false, error: '请输入搜索内容', engine: 'fallback' }
  }

  const regex = buildPattern(pattern.trim(), caseInsensitive, wholeWord)
  const matches: GrepMatchDto[] = []
  let filesScanned = 0
  let truncated = false

  await walkFiles(rootPath, rootPath, glob, async filePath => {
    if (matches.length >= headLimit) {
      truncated = true
      return false
    }
    if (filesScanned >= MAX_FILES_SCANNED) {
      truncated = true
      return false
    }

    filesScanned += 1

    let info
    try {
      info = await stat(filePath)
    } catch {
      return true
    }
    if (!info.isFile() || info.size > MAX_FILE_BYTES) return true

    let content: string
    try {
      content = await readFile(filePath, 'utf8')
    } catch {
      return true
    }

    const lines = content.split('\n')
    for (let i = 0; i < lines.length; i++) {
      if (matches.length >= headLimit) {
        truncated = true
        return false
      }
      if (regex.test(lines[i]!)) {
        matches.push({
          file: filePath.replace(/\\/g, '/'),
          line: i + 1,
          column: 1,
          text: lines[i]!,
        })
      }
    }

    return true
  })

  return { matches, truncated, engine: 'fallback' }
}
