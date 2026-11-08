export interface CharRange {
  /** 1-based inclusive start column in the new document */
  startColumn: number
  /** 1-based exclusive end column in the new document */
  endColumn: number
}

export interface RemovedZone {
  afterLineNumber: number
  lines: string[]
}

export interface InlineDiffResult {
  /** Brand-new lines in the new document (whole line highlighted). */
  pureAddedLines: number[]
  /** Lines that changed but correspond to an old line (gutter + inline chars). */
  modifiedLines: number[]
  /** Added character spans inside modified/new lines. */
  charAddedRanges: Array<{ lineNumber: number } & CharRange>
  /** Truly deleted lines (not 1:1 replacements) shown as ghost rows. */
  removedZones: RemovedZone[]
}

function normalizeNewlines(text: string): string {
  return text.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
}

function splitLines(text: string): string[] {
  const normalized = normalizeNewlines(text)
  if (normalized.length === 0) return []
  const lines = normalized.split('\n')
  if (lines[lines.length - 1] === '') lines.pop()
  return lines
}

type LineOp =
  | { kind: 'equal'; oldIndex: number; newIndex: number }
  | { kind: 'insert'; newIndex: number; text: string }
  | { kind: 'delete'; oldIndex: number; text: string }

function diffLineOps(oldLines: string[], newLines: string[]): LineOp[] {
  const n = oldLines.length
  const m = newLines.length
  const dp = Array.from({ length: n + 1 }, () => new Array<number>(m + 1).fill(0))

  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      if (oldLines[i] === newLines[j]) {
        dp[i]![j] = dp[i + 1]![j + 1]! + 1
      } else {
        dp[i]![j] = Math.max(dp[i + 1]![j]!, dp[i]![j + 1]!)
      }
    }
  }

  const ops: LineOp[] = []
  let i = 0
  let j = 0
  while (i < n || j < m) {
    if (i < n && j < m && oldLines[i] === newLines[j]) {
      ops.push({ kind: 'equal', oldIndex: i, newIndex: j })
      i++
      j++
    } else if (j < m && (i >= n || dp[i]![j + 1]! >= dp[i + 1]![j]!)) {
      ops.push({ kind: 'insert', newIndex: j, text: newLines[j]! })
      j++
    } else {
      ops.push({ kind: 'delete', oldIndex: i, text: oldLines[i]! })
      i++
    }
  }
  return ops
}

function isSignificantDiffChar(ch: string): boolean {
  return /[\p{L}\p{N}]/u.test(ch)
}

/** Drop punctuation-only highlights (unchanged `:[]()` etc. should not get green boxes). */
function filterSignificantCharRanges(newText: string, ranges: CharRange[]): CharRange[] {
  const filtered: CharRange[] = []

  for (const range of ranges) {
    let start = range.startColumn
    let end = range.endColumn

    while (start < end) {
      const ch = newText[start - 1] ?? ''
      if (isSignificantDiffChar(ch)) break
      start++
    }
    while (end > start) {
      const ch = newText[end - 2] ?? ''
      if (isSignificantDiffChar(ch)) break
      end--
    }
    if (start >= end) continue

    const slice = newText.slice(start - 1, end - 1)
    if (!isSignificantDiffChar(slice)) continue

    const prev = filtered[filtered.length - 1]
    if (prev && prev.endColumn === start) {
      prev.endColumn = end
    } else {
      filtered.push({ startColumn: start, endColumn: end })
    }
  }

  return filtered
}

/** Character-level LCS → added column ranges in the new string (1-based). */
export function charAddedRangesInNew(oldText: string, newText: string): CharRange[] {
  if (oldText === newText || newText.length === 0) return []
  if (oldText.length === 0) {
    return [{ startColumn: 1, endColumn: newText.length + 1 }]
  }

  const n = oldText.length
  const m = newText.length
  const dp = Array.from({ length: n + 1 }, () => new Array<number>(m + 1).fill(0))

  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      if (oldText[i] === newText[j]) {
        dp[i]![j] = dp[i + 1]![j + 1]! + 1
      } else {
        dp[i]![j] = Math.max(dp[i + 1]![j]!, dp[i]![j + 1]!)
      }
    }
  }

  const addedCols = new Set<number>()
  let i = 0
  let j = 0
  while (i < n || j < m) {
    if (i < n && j < m && oldText[i] === newText[j]) {
      i++
      j++
    } else if (j < m && (i >= n || dp[i]![j + 1]! >= dp[i + 1]![j]!)) {
      addedCols.add(j)
      j++
    } else {
      i++
    }
  }

  const ranges: CharRange[] = []
  let start = -1
  for (let col = 0; col < m; col++) {
    if (addedCols.has(col)) {
      if (start < 0) start = col
    } else if (start >= 0) {
      ranges.push({ startColumn: start + 1, endColumn: col + 1 })
      start = -1
    }
  }
  if (start >= 0) {
    ranges.push({ startColumn: start + 1, endColumn: m + 1 })
  }

  return filterSignificantCharRanges(newText, ranges)
}

function pushRemovedZone(
  zones: RemovedZone[],
  afterLineNumber: number,
  lines: string[],
): void {
  if (!lines.length) return
  zones.push({ afterLineNumber, lines: [...lines] })
}

/** Cursor-style inline diff: char highlights on modified lines, not whole-block copy/delete. */
export function computeInlineDiff(before: string, after: string): InlineDiffResult {
  const oldLines = splitLines(before)
  const newLines = splitLines(after)
  const ops = diffLineOps(oldLines, newLines)

  const pureAddedLines: number[] = []
  const modifiedLines: number[] = []
  const charAddedRanges: InlineDiffResult['charAddedRanges'] = []
  const removedZones: RemovedZone[] = []

  let pendingDeletes: string[] = []
  let pendingAfter = 0

  const flushDeletes = (): void => {
    pushRemovedZone(removedZones, pendingAfter, pendingDeletes)
    pendingDeletes = []
  }

  for (let idx = 0; idx < ops.length; idx++) {
    const op = ops[idx]!
    const next = ops[idx + 1]

    if (op.kind === 'equal') {
      flushDeletes()
      pendingAfter = op.newIndex + 1
      continue
    }

    if (op.kind === 'delete' && next?.kind === 'insert') {
      flushDeletes()
      const lineNumber = next.newIndex + 1
      modifiedLines.push(lineNumber)
      for (const range of charAddedRangesInNew(op.text, next.text)) {
        charAddedRanges.push({ lineNumber, ...range })
      }
      pendingAfter = lineNumber
      idx++
      continue
    }

    if (op.kind === 'insert') {
      flushDeletes()
      pureAddedLines.push(op.newIndex + 1)
      pendingAfter = op.newIndex + 1
      continue
    }

    if (op.kind === 'delete') {
      pendingDeletes.push(op.text)
      if (pendingDeletes.length === 1) {
        pendingAfter = Math.max(0, op.oldIndex > 0 ? op.oldIndex : 0)
        // Map old delete position to new doc line before deleted block
        const prevEqual = ops
          .slice(0, idx)
          .reverse()
          .find(o => o.kind === 'equal') as { kind: 'equal'; newIndex: number } | undefined
        pendingAfter = prevEqual ? prevEqual.newIndex + 1 : 0
      }
    }
  }

  flushDeletes()
  return { pureAddedLines, modifiedLines, charAddedRanges, removedZones }
}

/** @deprecated Use fields on InlineDiffResult directly. */
export function computeInlineDiffLegacy(before: string, after: string): {
  addedLines: number[]
  removedZones: RemovedZone[]
} {
  const result = computeInlineDiff(before, after)
  return {
    addedLines: [...result.pureAddedLines, ...result.modifiedLines],
    removedZones: result.removedZones,
  }
}
