import { readdir, readFile, stat, writeFile } from 'node:fs/promises'
import { join } from 'node:path'

const SKIP_DIRS = new Set(['.git', 'node_modules', 'target', '.idea', 'dist', 'build', '.svn', '.hg'])
const MAX_FILE_BYTES = 512 * 1024
const MAX_FILES_SCANNED = 400

export interface WorkspaceReplaceRequest {
  rootPath: string
  pattern: string
  replacement: string
  caseInsensitive?: boolean
  wholeWord?: boolean
  glob?: string
}

export interface WorkspaceReplaceResult {
  fileCount: number
  replacementCount: number
  truncated: boolean
  error?: string
}

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function buildPattern(pattern: string, caseInsensitive?: boolean, wholeWord?: boolean): RegExp {
  let body = escapeRegex(pattern)
  if (wholeWord) body = `\\b${body}\\b`
  return new RegExp(body, caseInsensitive ? 'gi' : 'g')
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

export async function runWorkspaceReplace(
  request: WorkspaceReplaceRequest,
): Promise<WorkspaceReplaceResult> {
  const { rootPath, pattern, replacement, caseInsensitive, wholeWord, glob } = request
  const trimmed = pattern.trim()

  if (!trimmed) {
    return { fileCount: 0, replacementCount: 0, truncated: false, error: '请输入要查找的内容' }
  }

  const regex = buildPattern(trimmed, caseInsensitive, wholeWord)
  let fileCount = 0
  let replacementCount = 0
  let filesScanned = 0
  let truncated = false

  await walkFiles(rootPath, rootPath, glob, async filePath => {
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

    regex.lastIndex = 0
    const matches = content.match(regex)
    if (!matches?.length) return true

    const next = content.replace(regex, replacement)
    if (next === content) return true

    try {
      await writeFile(filePath, next, 'utf8')
      fileCount += 1
      replacementCount += matches.length
    } catch {
      // skip unreadable / locked files
    }

    return true
  })

  return { fileCount, replacementCount, truncated }
}
