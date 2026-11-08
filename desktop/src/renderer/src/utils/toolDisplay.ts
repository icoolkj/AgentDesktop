/** Human-readable tool labels (Claude Code formatToolUseSummary / Cursor-style). */

export type ToolActivityLike = {
  toolCallId: string
  name: string
  status: 'running' | 'done' | 'error'
  summary?: string
  output?: string
  argsJson?: string
  startedAt?: number
  endedAt?: number
}

const VERB: Record<string, string> = {
  read_file: 'Read',
  list_dir: 'Listed',
  write_file: 'Wrote',
  search_replace: 'Edited',
  grep: 'Searched',
  glob: 'Globbed',
  Task: 'Agent',
  WebSearch: 'Web search',
  WebFetch: 'Fetched',
}

const PATH_KEYS = ['path', 'file_path', 'target_directory', 'directory']
const TEXT_KEYS = ['pattern', 'query', 'glob_pattern', 'glob', 'command', 'old_string']

export type ToolGroupKind = 'read' | 'search' | 'edit' | 'other'

export type ToolActivityGroup = {
  kind: ToolGroupKind
  title: string
  tools: ToolActivityLike[]
  liveHint?: string
}

export type AgentRunSummary = {
  headline: string
  phases: Array<{ label: string; detail: string }>
  totalSteps: number
  totalDurationMs: number
}

function truncate(text: string, max = 56): string {
  const oneLine = text.replace(/\s+/g, ' ').trim()
  if (oneLine.length <= max) return oneLine
  return `${oneLine.slice(0, max - 1)}…`
}

function pickString(input: Record<string, unknown>, keys: string[]): string | undefined {
  for (const key of keys) {
    const v = input[key]
    if (typeof v === 'string' && v.trim()) return v.trim()
  }
  for (const v of Object.values(input)) {
    if (typeof v === 'string' && v.trim()) return v.trim()
  }
  return undefined
}

export function parseToolArgs(argsJson?: string): Record<string, unknown> | null {
  if (!argsJson?.trim()) return null
  try {
    const parsed = JSON.parse(argsJson) as unknown
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return parsed as Record<string, unknown>
    }
  } catch {
    // ignore
  }
  return null
}

function countListDirEntries(output?: string): number | undefined {
  if (!output) return undefined
  const lines = output.split('\n').filter(l => l.trim() && !l.startsWith('Error'))
  return lines.length > 0 ? lines.length : undefined
}

export function classifyToolGroup(name: string): ToolGroupKind {
  if (name === 'read_file' || name === 'list_dir') return 'read'
  if (name === 'grep' || name === 'glob' || name === 'WebSearch' || name === 'WebFetch') return 'search'
  if (name === 'write_file' || name === 'search_replace') return 'edit'
  return 'other'
}

function fileNameFromPath(path: string): string {
  const parts = path.replace(/\\/g, '/').split('/')
  return parts[parts.length - 1] || path
}

function toolTargetHint(tool: ToolActivityLike): string | undefined {
  const input = parseToolArgs(tool.argsJson)
  if (!input) return tool.summary?.trim() || undefined
  const path = pickString(input, PATH_KEYS)
  if (path) return fileNameFromPath(path)
  const text = pickString(input, TEXT_KEYS)
  if (text) return truncate(text, 40)
  return tool.summary?.trim() || undefined
}

function groupLiveHint(tools: ToolActivityLike[]): string | undefined {
  const running = tools.find(t => t.status === 'running')
  if (running) return toolTargetHint(running)
  for (let i = tools.length - 1; i >= 0; i--) {
    const hint = toolTargetHint(tools[i]!)
    if (hint) return hint
  }
  return undefined
}

function groupTitle(kind: ToolGroupKind, tools: ToolActivityLike[]): string {
  const n = tools.length
  const done = tools.filter(t => t.status === 'done').length
  const running = tools.some(t => t.status === 'running')

  if (kind === 'read') {
    const reads = tools.filter(t => t.name === 'read_file').length
    const lists = tools.filter(t => t.name === 'list_dir').length
    const parts: string[] = []
    if (reads > 0) parts.push(`Read ${reads} file${reads === 1 ? '' : 's'}`)
    if (lists > 0) parts.push(`Listed ${lists} dir${lists === 1 ? '' : 's'}`)
    if (parts.length === 0) parts.push(`Explored ${n} path${n === 1 ? '' : 's'}`)
    const suffix = running ? '…' : done === n ? '' : ` (${done}/${n})`
    return parts.join(' · ') + suffix
  }

  if (kind === 'search') {
    return `Searched ${n} time${n === 1 ? '' : 's'}${running ? '…' : done === n ? '' : ` (${done}/${n})`}`
  }

  if (kind === 'edit') {
    const writes = tools.filter(t => t.name === 'write_file').length
    const edits = tools.filter(t => t.name === 'search_replace').length
    const parts: string[] = []
    if (edits > 0) parts.push(`Edited ${edits} file${edits === 1 ? '' : 's'}`)
    if (writes > 0) parts.push(`Wrote ${writes} file${writes === 1 ? '' : 's'}`)
    if (parts.length === 0) parts.push(`Modified ${n} file${n === 1 ? '' : 's'}`)
    const suffix = running ? '…' : done === n ? '' : ` (${done}/${n})`
    return parts.join(' · ') + suffix
  }

  const names = [...new Set(tools.map(t => VERB[t.name] ?? t.name.replace(/_/g, ' ')))]
  if (names.length === 1) {
    return `${names[0]} × ${n}${running ? '…' : ''}`
  }
  return `${n} other step${n === 1 ? '' : 's'}${running ? '…' : ''}`
}

const GROUP_ORDER: ToolGroupKind[] = ['read', 'search', 'edit', 'other']

export function groupToolActivities(tools: ToolActivityLike[]): ToolActivityGroup[] {
  const buckets = new Map<ToolGroupKind, ToolActivityLike[]>()
  for (const tool of tools) {
    const kind = classifyToolGroup(tool.name)
    const list = buckets.get(kind) ?? []
    list.push(tool)
    buckets.set(kind, list)
  }

  return GROUP_ORDER.filter(kind => buckets.has(kind)).map(kind => {
    const groupTools = buckets.get(kind)!
    return {
      kind,
      title: groupTitle(kind, groupTools),
      tools: groupTools,
      liveHint: groupLiveHint(groupTools),
    }
  })
}

export function formatToolLabel(
  name: string,
  argsJson?: string,
  output?: string,
  summary?: string,
): string {
  if (summary?.trim()) return truncate(summary)

  const input = parseToolArgs(argsJson)
  const verb = VERB[name] ?? name.replace(/_/g, ' ')

  if (!input) return verb

  const path = pickString(input, PATH_KEYS)
  if (path) {
    if (name === 'list_dir') {
      const n = countListDirEntries(output)
      return n != null ? `${verb} ${truncate(path)} (${n} entries)` : `${verb} ${truncate(path)}`
    }
    if (name === 'read_file') {
      const offset = typeof input.offset === 'number' ? input.offset : undefined
      const limit = typeof input.limit === 'number' ? input.limit : undefined
      if (offset != null || limit != null) {
        const range = limit != null ? `L${(offset ?? 0) + 1}-${(offset ?? 0) + limit}` : `L${(offset ?? 0) + 1}+`
        return `${verb} ${truncate(path)} ${range}`
      }
    }
    return `${verb} ${truncate(path)}`
  }

  const text = pickString(input, TEXT_KEYS)
  if (text) return `${verb} ${truncate(text)}`

  return verb
}

export function formatToolSummaryLine(tools: { name: string; argsJson?: string; summary?: string }[]): string {
  const groups = groupToolActivities(tools as ToolActivityLike[])
  if (groups.length === 0) return ''
  return groups
    .map(g => (g.liveHint ? `${g.title} → ${g.liveHint}` : g.title))
    .slice(0, 3)
    .join(' · ')
}

export function toolDurationMs(tool: ToolActivityLike): number | undefined {
  if (tool.startedAt == null) return undefined
  const end = tool.endedAt ?? (tool.status === 'running' ? Date.now() : undefined)
  if (end == null) return undefined
  return Math.max(0, end - tool.startedAt)
}

export function formatDurationMs(ms: number): string {
  if (ms < 1000) return ms > 0 ? '<1s' : ''
  const s = Math.floor(ms / 1000)
  if (s < 60) return `${s}s`
  return `${Math.floor(s / 60)}m ${s % 60}s`
}

export function buildAgentRunSummary(tools: ToolActivityLike[]): AgentRunSummary | null {
  if (tools.length === 0) return null

  const groups = groupToolActivities(tools)
  const done = tools.filter(t => t.status === 'done').length
  const errors = tools.filter(t => t.status === 'error').length
  const durations = tools.map(toolDurationMs).filter((d): d is number => d != null)
  const totalDurationMs = durations.reduce((a, b) => a + b, 0)

  const phases = groups.map(g => ({
    label: g.title.replace(/…$/, '').trim(),
    detail: g.liveHint ?? `${g.tools.length} step${g.tools.length === 1 ? '' : 's'}`,
  }))

  let headline = `Agent 完成 ${done} 步工具调用`
  if (errors > 0) headline += `（${errors} 步失败）`
  if (totalDurationMs >= 1000) headline += ` · ${formatDurationMs(totalDurationMs)}`

  return { headline, phases, totalSteps: tools.length, totalDurationMs }
}

const TOOL_ICONS: Record<string, string> = {
  read_file: 'R',
  list_dir: 'D',
  write_file: 'W',
  search_replace: 'E',
  grep: 'S',
  glob: 'G',
  Task: 'A',
}

export function toolIcon(name: string): string {
  return TOOL_ICONS[name] ?? '•'
}

export function groupIcon(kind: ToolGroupKind): string {
  if (kind === 'read') return '📖'
  if (kind === 'search') return '🔍'
  if (kind === 'edit') return '✎'
  return '⚙'
}

/** Auto-expand when live, or when enough steps to be useful. */
export function shouldAutoExpandTools(tools: ToolActivityLike[], live: boolean): boolean {
  if (live) return true
  return tools.length >= 1
}
