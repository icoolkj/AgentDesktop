// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { resolve } from 'node:path'

import {
  dedupeDiagnostics,
  normalizeDiagnosticPath,
  parseEslintJsonDiagnostics,
  parseTscDiagnostics,
} from './workspaceDiagnosticsParse'

// 用真实绝对路径作为 cwd：Windows 盘符字面量在 POSIX 上会被当成相对路径
const testCwd = resolve('proj-fixture')

describe('workspaceDiagnosticsParse', () => {
  it('parses tsc error lines', () => {
    const output = `src/a.ts(10,5): error TS2322: Type 'string' is not assignable to type 'number'.`
    const problems = parseTscDiagnostics(output, testCwd)
    expect(problems).toHaveLength(1)
    expect(problems[0]?.severity).toBe('error')
    expect(problems[0]?.code).toBe('TS2322')
    expect(problems[0]?.line).toBe(10)
  })

  it('parses eslint json output', () => {
    const json = JSON.stringify([
      {
        filePath: resolve(testCwd, 'src/a.ts'),
        messages: [{ line: 2, column: 3, message: 'Unexpected var', severity: 2, ruleId: 'no-var' }],
      },
    ])
    const problems = parseEslintJsonDiagnostics(json, testCwd)
    expect(problems).toHaveLength(1)
    expect(problems[0]?.source).toBe('eslint')
    expect(problems[0]?.severity).toBe('error')
  })

  it('normalizes relative paths against cwd', () => {
    const path = normalizeDiagnosticPath('src/a.ts', testCwd)
    expect(path.replace(/\\/g, '/')).toBe(resolve(testCwd, 'src/a.ts').replace(/\\/g, '/'))
  })

  it('dedupes identical diagnostics', () => {
    const one = parseTscDiagnostics(`src/a.ts(1,1): error TS1000: x`, testCwd)
    const merged = dedupeDiagnostics([...one, ...one])
    expect(merged).toHaveLength(1)
  })
})
