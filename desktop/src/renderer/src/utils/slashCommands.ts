export interface SlashCommand {
  name: string
  description: string
  /** Server-side only — client handles /rewind locally */
  clientSide?: boolean
}

export const SLASH_COMMANDS: SlashCommand[] = [
  { name: '/compact', description: '压缩对话上下文（LLM 摘要）' },
  { name: '/context', description: '显示当前上下文占用' },
  { name: '/rewind', description: '回滚到检查点（打开对话框）', clientSide: true },
]

export function extractSlashQuery(text: string, cursor: number): string | null {
  const before = text.slice(0, cursor)
  const match = before.match(/(?:^|\s)(\/[^\s]*)$/)
  if (!match?.[1]) return null
  return match[1].slice(1).toLowerCase()
}

export function filterSlashCommands(query: string): SlashCommand[] {
  const q = query.toLowerCase()
  if (!q) return SLASH_COMMANDS
  return SLASH_COMMANDS.filter(
    cmd => cmd.name.slice(1).startsWith(q) || cmd.name.includes(q),
  )
}

export function insertSlashCommand(text: string, cursor: number, commandName: string): { text: string; cursor: number } {
  const before = text.slice(0, cursor)
  const after = text.slice(cursor)
  const match = before.match(/(?:^|\s)(\/[^\s]*)$/)
  if (!match) {
    const prefix = before.endsWith(' ') || before.length === 0 ? '' : ' '
    const insertion = `${prefix}${commandName} `
    return { text: before + insertion + after, cursor: before.length + insertion.length }
  }
  const start = before.length - match[1].length
  const next = before.slice(0, start) + commandName + ' ' + after
  return { text: next, cursor: start + commandName.length + 1 }
}
