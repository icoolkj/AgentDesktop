export interface HighlightSegment {
  text: string
  highlight: boolean
}

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

export function buildHighlightSegments(
  line: string,
  query: string,
  caseSensitive: boolean,
  wholeWord: boolean,
): HighlightSegment[] {
  const trimmed = query.trim()
  if (!trimmed) {
    return [{ text: line, highlight: false }]
  }

  let body = escapeRegex(trimmed)
  if (wholeWord) body = `\\b${body}\\b`

  const re = new RegExp(body, caseSensitive ? 'g' : 'gi')
  const segments: HighlightSegment[] = []
  let lastIndex = 0

  for (const match of line.matchAll(re)) {
    const index = match.index ?? 0
    const matched = match[0]
    if (!matched) continue

    if (index > lastIndex) {
      segments.push({ text: line.slice(lastIndex, index), highlight: false })
    }
    segments.push({ text: matched, highlight: true })
    lastIndex = index + matched.length
  }

  if (lastIndex < line.length) {
    segments.push({ text: line.slice(lastIndex), highlight: false })
  }

  return segments.length > 0 ? segments : [{ text: line, highlight: false }]
}
