export type DiagnosticSeverity = 'error' | 'warning' | 'info'

export interface DiagnosticProblemDto {
  path: string
  line: number
  column: number
  endLine: number
  endColumn: number
  message: string
  severity: DiagnosticSeverity
  source?: string
  code?: string
}

const TSC_LINE =
  /^(.+)\((\d+),(\d+)\): (error|warning) (?:TS(\d+)|[^\s]+): (.*)$/

export function parseTscDiagnostics(output: string, cwd: string): DiagnosticProblemDto[] {
  const problems: DiagnosticProblemDto[] = []
  for (const rawLine of output.split(/\r?\n/)) {
    const line = rawLine.trim()
    if (!line) continue
    const match = TSC_LINE.exec(line)
    if (!match) continue

    const [, filePart, lineStr, colStr, kind, code, message] = match
    problems.push({
      path: normalizeDiagnosticPath(filePart!, cwd),
      line: Number(lineStr),
      column: Number(colStr),
      endLine: Number(lineStr),
      endColumn: Number(colStr),
      message: message!.trim(),
      severity: kind === 'warning' ? 'warning' : 'error',
      source: 'typescript',
      code: code ? `TS${code}` : undefined,
    })
  }
  return problems
}

interface ESLintJsonMessage {
  line?: number
  column?: number
  endLine?: number
  endColumn?: number
  message?: string
  severity?: number
  ruleId?: string | null
}

interface ESLintJsonFile {
  filePath?: string
  messages?: ESLintJsonMessage[]
}

export function parseEslintJsonDiagnostics(output: string, cwd: string): DiagnosticProblemDto[] {
  let parsed: ESLintJsonFile[]
  try {
    parsed = JSON.parse(output) as ESLintJsonFile[]
  } catch {
    return []
  }
  if (!Array.isArray(parsed)) return []

  const problems: DiagnosticProblemDto[] = []
  for (const file of parsed) {
    if (!file.filePath || !file.messages?.length) continue
    const path = normalizeDiagnosticPath(file.filePath, cwd)
    for (const msg of file.messages) {
      if (!msg.message || !msg.line || !msg.column) continue
      problems.push({
        path,
        line: msg.line,
        column: msg.column,
        endLine: msg.endLine ?? msg.line,
        endColumn: msg.endColumn ?? msg.column,
        message: msg.message,
        severity: msg.severity === 1 ? 'warning' : 'error',
        source: 'eslint',
        code: msg.ruleId ?? undefined,
      })
    }
  }
  return problems
}

import { isAbsolute, resolve } from 'node:path'

export function normalizeDiagnosticPath(filePath: string, cwd: string): string {
  const trimmed = filePath.trim()
  if (!trimmed) return trimmed
  const resolved = isAbsolute(trimmed) ? trimmed : resolve(cwd, trimmed)
  return process.platform === 'win32' ? resolved.replace(/\//g, '\\') : resolved.replace(/\\/g, '/')
}

export function dedupeDiagnostics(problems: DiagnosticProblemDto[]): DiagnosticProblemDto[] {
  const seen = new Set<string>()
  const out: DiagnosticProblemDto[] = []
  for (const problem of problems) {
    const key = `${problem.path}|${problem.line}|${problem.column}|${problem.severity}|${problem.message}`
    if (seen.has(key)) continue
    seen.add(key)
    out.push(problem)
  }
  return out
}
