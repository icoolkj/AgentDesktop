import { readdir } from 'node:fs/promises'
import { join } from 'node:path'

import { shouldSkipExplorerEntry } from './explorerDirs'

export interface WorkspacePathMatchDto {
  name: string
  path: string
  relativePath: string
  kind: 'file' | 'directory'
}

const MAX_SCAN = 20_000
const COLLECT_CAP = 400

function scorePath(name: string, relPath: string, query: string): number {
  const q = query.toLowerCase()
  if (!q) return 1

  const nameL = name.toLowerCase()
  const relL = relPath.replace(/\\/g, '/').toLowerCase()

  if (nameL === q) return 100
  if (nameL.startsWith(q)) return 92
  if (nameL.includes(q)) return 84

  for (const segment of relL.split('/')) {
    if (segment === q) return 78
    if (segment.startsWith(q)) return 72
    if (segment.includes(q)) return 66
  }

  if (relL.includes(`/${q}`)) return 60
  if (relL.includes(q)) return 50
  return 0
}

async function walk(
  dir: string,
  rootPath: string,
  rel: string,
  query: string,
  matches: Array<WorkspacePathMatchDto & { score: number }>,
  state: { scanned: number },
): Promise<void> {
  if (matches.length >= COLLECT_CAP || state.scanned >= MAX_SCAN) return

  let entries
  try {
    entries = await readdir(dir, { withFileTypes: true })
  } catch {
    return
  }

  for (const entry of entries) {
    if (state.scanned >= MAX_SCAN || matches.length >= COLLECT_CAP) return
    state.scanned++

    if (shouldSkipExplorerEntry(entry.name, entry.isDirectory())) continue

    const entryRel = rel ? `${rel}/${entry.name}` : entry.name
    const fullPath = join(dir, entry.name)
    const score = scorePath(entry.name, entryRel, query)

    if (entry.isDirectory()) {
      if (score > 0) {
        matches.push({
          name: entry.name,
          path: fullPath,
          relativePath: entryRel,
          kind: 'directory',
          score,
        })
      }
      await walk(fullPath, rootPath, entryRel, query, matches, state)
      continue
    }

    if (!entry.isFile() || score <= 0) continue
    matches.push({
      name: entry.name,
      path: fullPath,
      relativePath: entryRel,
      kind: 'file',
      score,
    })
  }
}

export async function searchWorkspacePaths(
  rootPath: string,
  query: string,
  limit = 20,
): Promise<WorkspacePathMatchDto[]> {
  const trimmed = query.trim()
  if (!trimmed) return []

  const matches: Array<WorkspacePathMatchDto & { score: number }> = []
  await walk(rootPath, rootPath, '', trimmed, matches, { scanned: 0 })

  matches.sort((a, b) => {
    if (b.score !== a.score) return b.score - a.score
    if (a.relativePath.length !== b.relativePath.length) {
      return a.relativePath.length - b.relativePath.length
    }
    return a.relativePath.localeCompare(b.relativePath)
  })

  const seen = new Set<string>()
  const unique: WorkspacePathMatchDto[] = []
  for (const match of matches) {
    const key = match.relativePath.toLowerCase()
    if (seen.has(key)) continue
    seen.add(key)
    unique.push({
      name: match.name,
      path: match.path,
      relativePath: match.relativePath,
      kind: match.kind,
    })
    if (unique.length >= limit) break
  }

  return unique
}
