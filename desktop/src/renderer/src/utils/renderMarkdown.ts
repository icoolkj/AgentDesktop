const MERMAID_START_RE =
  /^(graph\s+(?:TB|BT|RL|LR|TD|BT)|flowchart\s+(?:TB|BT|RL|LR|TD|BT)|sequenceDiagram|classDiagram|stateDiagram-v2|stateDiagram|erDiagram|gantt|pie|gitGraph|mindmap|timeline|C4Context|journey)/i

const FENCE_RE = /```[\s\S]*?```/g

/** Minimal Markdown → HTML for assistant messages. Supports code blocks + Mermaid. */
export function renderSimpleMarkdown(text: string): string {
  const cleaned = stripInternalMarkup(text)
  return renderBlocks(promoteBareMermaidOutsideFences(cleaned))
}

function isMermaidSource(code: string): boolean {
  return MERMAID_START_RE.test(code.trim())
}

/** Only promote bare Mermaid outside existing ``` fences to avoid corrupting fenced blocks. */
function promoteBareMermaidOutsideFences(text: string): string {
  const parts: string[] = []
  let lastIndex = 0
  let match: RegExpExecArray | null
  const fence = new RegExp(FENCE_RE.source, 'g')

  while ((match = fence.exec(text)) !== null) {
    if (match.index > lastIndex) {
      parts.push(promoteBareMermaid(text.slice(lastIndex, match.index)))
    }
    parts.push(match[0])
    lastIndex = fence.lastIndex
  }

  if (lastIndex < text.length) {
    parts.push(promoteBareMermaid(text.slice(lastIndex)))
  }

  return parts.join('')
}

/** Wrap bare Mermaid diagrams (no ``` fence) so they can be rendered. */
function promoteBareMermaid(text: string): string {
  const lines = text.split('\n')
  const out: string[] = []
  let i = 0

  while (i < lines.length) {
    const trimmed = lines[i]?.trim() ?? ''
    if (MERMAID_START_RE.test(trimmed)) {
      const block: string[] = []
      while (i < lines.length) {
        const line = lines[i] ?? ''
        const lineTrim = line.trim()
        if (block.length > 0 && looksLikeProseLine(line)) break
        if (block.length > 0 && lineTrim.startsWith('```')) break
        if (block.length > 0 && lineTrim === '' && i + 1 < lines.length) {
          const nextLine = lines[i + 1] ?? ''
          if (looksLikeProseLine(nextLine) || /^#{1,6}\s/.test(nextLine.trim())) break
        }
        block.push(line)
        i++
      }
      out.push('```mermaid\n' + block.join('\n').trim() + '\n```')
      continue
    }
    out.push(lines[i] ?? '')
    i++
  }

  return out.join('\n')
}

function looksLikeProseLine(line: string): boolean {
  const t = line.trim()
  if (!t) return false
  if (/^#{1,6}\s/.test(t)) return true
  if (/^[-*+]\s+\S/.test(t)) return true
  if (/^\d+\.\s+\S/.test(t)) return true
  if (/^\|.+\|$/.test(t)) return true
  if (/^[\u4e00-\u9fffA-Za-z（(「『]/.test(t) && !looksLikeMermaidLine(line)) return true
  return false
}

function looksLikeMermaidLine(line: string): boolean {
  const t = line.trim()
  if (!t) return true
  if (MERMAID_START_RE.test(t)) return true
  if (
    /^(subgraph|end|style|classDef|class|click|linkStyle|direction|accTitle|accDescr|participant|actor|autonumber|activate|deactivate|deactivateAndDestroy|loop|alt|else|opt|par|rect|critical|break|Note)\b/i.test(
      t,
    )
  ) {
    return true
  }
  if (/(-[->x]|<<|-->>|--x|->>|->|-->|---|-\.->|<-->)/.test(t)) return true
  if (/[[\]{}|]/.test(t)) return true
  if (/^\s+/.test(line)) return true
  return false
}

function renderMermaidBlock(code: string): string {
  const trimmed = code.trim()
  return `<div class="md-mermaid">${escapeHtml(trimmed)}</div>`
}

function renderBlocks(text: string): string {
  const parts: string[] = []
  const fence = /```(\w*)\r?\n?([\s\S]*?)```/g
  let lastIndex = 0
  let match: RegExpExecArray | null

  while ((match = fence.exec(text)) !== null) {
    if (match.index > lastIndex) {
      parts.push(renderInline(text.slice(lastIndex, match.index)))
    }
    const lang = (match[1] ?? '').trim().toLowerCase()
    const code = match[2] ?? ''
    if (lang === 'mermaid' || isMermaidSource(code)) {
      parts.push(renderMermaidBlock(code))
    } else {
      parts.push(`<pre class="md-pre"><code>${escapeHtml(code.trim())}</code></pre>`)
    }
    lastIndex = fence.lastIndex
  }

  if (lastIndex < text.length) {
    parts.push(renderInline(text.slice(lastIndex)))
  }

  return parts.join('')
}

function renderInline(text: string): string {
  const lines = text.split('\n')
  const html: string[] = []
  let i = 0

  while (i < lines.length) {
    const line = lines[i] ?? ''

    if (isTableRow(line) && i + 1 < lines.length && isTableSeparator(lines[i + 1] ?? '')) {
      const tableLines = [line, lines[i + 1] ?? '']
      i += 2
      while (i < lines.length && isTableRow(lines[i] ?? '')) {
        tableLines.push(lines[i] ?? '')
        i++
      }
      html.push(renderTable(tableLines))
      continue
    }

    html.push(renderTextLine(line))
    i++
  }

  return html.join('')
}

function renderTextLine(line: string): string {
  const trimmed = line.trim()
  if (!trimmed) return '<br/>'

  const heading = trimmed.match(/^(#{1,3})\s+(.+)$/)
  if (heading) {
    const level = heading[1].length
    return `<h${level}>${escapeHtml(heading[2])}</h${level}>`
  }

  const escaped = escapeHtml(line)
  const withInline = escaped.replace(/`([^`]+)`/g, '<code class="md-code">$1</code>')
  const withBold = withInline.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  return `<p class="md-p">${withBold}</p>`
}

function isTableRow(line: string): boolean {
  const t = line.trim()
  return t.startsWith('|') && t.includes('|', 1)
}

function isTableSeparator(line: string): boolean {
  return /^\|(?:\s*:?-+:?\s*\|)+\s*$/.test(line.trim())
}

function parseTableRow(line: string): string[] {
  return line
    .trim()
    .replace(/^\|/, '')
    .replace(/\|$/, '')
    .split('|')
    .map(cell => cell.trim())
}

function renderTable(lines: string[]): string {
  if (lines.length < 2) return escapeHtml(lines.join('\n'))

  const header = parseTableRow(lines[0])
  const rows = lines.slice(2).map(parseTableRow)
  const headHtml = header.map(cell => `<th>${escapeHtml(cell)}</th>`).join('')
  const bodyHtml = rows
    .map(row => `<tr>${row.map(cell => `<td>${escapeHtml(cell)}</td>`).join('')}</tr>`)
    .join('')

  return `<div class="md-table-wrap"><table class="md-table"><thead><tr>${headHtml}</tr></thead><tbody>${bodyHtml}</tbody></table></div>`
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/** Strip DSML / internal tool markup leaked by some models. */
export function stripInternalMarkup(text: string): string {
  if (!text) return ''
  let result = text
  for (let i = 0; i < 8; i++) {
    let next = result.replace(/<\|DSML\|[^\n]*(?:\n[\s\S]*?)?(?:<\/\|DSML\|[^>\n]*>|(?=<\|DSML\|)|$)/gi, '')
    next = next.replace(/<[^>\n]*DSML[^>\n]*>[\s\S]*?(?:<\/[^>\n]*DSML[^>\n]*>|(?=<[^>\n]*DSML))/gi, '')
    if (next === result) break
    result = next
  }
  result = result.replace(/<\|DSML\|[^>\n]*>/gi, '')
  result = result.replace(/<[^>\n]*DSML[^>\n]*>/gi, '')

  const markupStart = findInternalMarkupStart(result)
  if (markupStart >= 0) {
    result = result.slice(0, markupStart)
  }
  result = result.replace(/(?:<\s*\|?\s*\|?\s*DSML|tool_calls?e?\s*=|\|DSML\|)[\s\S]*$/gi, '')
  return result.trim()
}

function findInternalMarkupStart(text: string): number {
  const upper = text.toUpperCase()
  const markers = ['<|DSML|', '|DSML|', 'TOOL_CALLSE', 'TOOL_CALLS']
  let earliest = -1
  for (const marker of markers) {
    const i = upper.indexOf(marker)
    if (i >= 0) {
      earliest = earliest < 0 ? i : Math.min(earliest, i)
    }
  }
  const spaced = text.match(/<\s*\|?\s*\|?\s*DSML/i)
  if (spaced?.index !== undefined) {
    earliest = earliest < 0 ? spaced.index : Math.min(earliest, spaced.index)
  }
  return earliest
}
