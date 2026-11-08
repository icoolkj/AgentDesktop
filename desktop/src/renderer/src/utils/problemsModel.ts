/** Pure helpers for Problems panel — no Monaco dependency (testable). */

import { editorPathKey } from '@renderer/utils/editorPath'

export type ProblemSeverity = 'error' | 'warning' | 'info'

export interface ProblemEntry {
  path: string
  line: number
  column: number
  endLine: number
  endColumn: number
  message: string
  severity: ProblemSeverity
  source?: string
  code?: string
}

export interface ProblemFileGroup {
  path: string
  relativePath: string
  problems: ProblemEntry[]
}

const SEVERITY_RANK: Record<ProblemSeverity, number> = {
  error: 0,
  warning: 1,
  info: 2,
}

export function severityLabel(severity: ProblemSeverity): string {
  switch (severity) {
    case 'error':
      return '错误'
    case 'warning':
      return '警告'
    case 'info':
      return '信息'
  }
}

export function compareProblems(a: ProblemEntry, b: ProblemEntry): number {
  const rank = SEVERITY_RANK[a.severity] - SEVERITY_RANK[b.severity]
  if (rank !== 0) return rank
  const path = a.path.localeCompare(b.path)
  if (path !== 0) return path
  if (a.line !== b.line) return a.line - b.line
  return a.column - b.column
}

export function groupProblemsByFile(
  entries: ProblemEntry[],
  workspaceRoot: string | undefined,
): ProblemFileGroup[] {
  const map = new Map<string, ProblemEntry[]>()
  for (const entry of [...entries].sort(compareProblems)) {
    const list = map.get(entry.path) ?? []
    list.push(entry)
    map.set(entry.path, list)
  }

  return [...map.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([path, problems]) => ({
      path,
      relativePath: relativeDisplayPath(workspaceRoot, path),
      problems,
    }))
}

export function countBySeverity(entries: ProblemEntry[]): {
  errors: number
  warnings: number
  infos: number
} {
  let errors = 0
  let warnings = 0
  let infos = 0
  for (const entry of entries) {
    if (entry.severity === 'error') errors++
    else if (entry.severity === 'warning') warnings++
    else infos++
  }
  return { errors, warnings, infos }
}

function problemKey(entry: ProblemEntry): string {
  return `${editorPathKey(entry.path)}|${entry.line}|${entry.column}|${entry.message}`
}

/** Merge editor markers and workspace scan results without duplicates. */
export function mergeProblemEntries(...lists: ProblemEntry[][]): ProblemEntry[] {
  const seen = new Set<string>()
  const out: ProblemEntry[] = []
  for (const list of lists) {
    for (const entry of list) {
      const key = problemKey(entry)
      if (seen.has(key)) continue
      seen.add(key)
      out.push(entry)
    }
  }
  return out.sort(compareProblems)
}

function relativeDisplayPath(workspaceRoot: string | undefined, absolutePath: string): string {
  if (!workspaceRoot) {
    return basename(absolutePath)
  }
  const root = normalizeSlashes(workspaceRoot).replace(/\/+$/, '')
  const file = normalizeSlashes(absolutePath)
  if (file.toLowerCase().startsWith(`${root.toLowerCase()}/`)) {
    return file.slice(root.length + 1)
  }
  return basename(absolutePath)
}

function basename(filePath: string): string {
  const normalized = normalizeSlashes(filePath)
  const parts = normalized.split('/')
  return parts[parts.length - 1] || filePath
}

function normalizeSlashes(value: string): string {
  return value.replace(/\\/g, '/')
}
