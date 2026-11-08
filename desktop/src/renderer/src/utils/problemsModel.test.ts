import { describe, expect, it } from 'vitest'

import {
  compareProblems,
  countBySeverity,
  groupProblemsByFile,
  mergeProblemEntries,
  type ProblemEntry,
} from './problemsModel'

const sample = (overrides: Partial<ProblemEntry>): ProblemEntry => ({
  path: 'D:/proj/src/a.ts',
  line: 1,
  column: 1,
  endLine: 1,
  endColumn: 5,
  message: 'msg',
  severity: 'error',
  ...overrides,
})

describe('problemsModel', () => {
  it('sorts errors before warnings and by line', () => {
    const sorted = [
      sample({ severity: 'warning', line: 2 }),
      sample({ severity: 'error', line: 5 }),
      sample({ severity: 'error', line: 1 }),
    ].sort(compareProblems)

    expect(sorted.map(item => `${item.severity}:${item.line}`)).toEqual([
      'error:1',
      'error:5',
      'warning:2',
    ])
  })

  it('groups by file with relative paths', () => {
    const groups = groupProblemsByFile(
      [
        sample({ path: 'D:/proj/src/a.ts', line: 2 }),
        sample({ path: 'D:/proj/src/a.ts', line: 1, severity: 'warning' }),
        sample({ path: 'D:/proj/src/b.ts', line: 3, message: 'other' }),
      ],
      'D:/proj',
    )

    expect(groups).toHaveLength(2)
    expect(groups[0]?.relativePath).toBe('src/a.ts')
    expect(groups[0]?.problems).toHaveLength(2)
    expect(groups[1]?.relativePath).toBe('src/b.ts')
  })

  it('counts severities', () => {
    expect(
      countBySeverity([
        sample({ severity: 'error' }),
        sample({ severity: 'warning' }),
        sample({ severity: 'info' }),
        sample({ severity: 'error' }),
      ]),
    ).toEqual({ errors: 2, warnings: 1, infos: 1 })
  })

  it('merges editor and workspace lists without duplicates', () => {
    const merged = mergeProblemEntries(
      [sample({ line: 1, message: 'same', source: 'monaco' })],
      [
        sample({ line: 1, message: 'same', source: 'typescript' }),
        sample({ line: 2, severity: 'warning', message: 'other' }),
      ],
    )
    expect(merged).toHaveLength(2)
  })
})
