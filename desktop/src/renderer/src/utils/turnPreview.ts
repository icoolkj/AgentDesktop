/** Preview text for a conversation turn in history list. */
import type { ChatMessage } from '@renderer/stores/chatStore'

export interface HistoryTurn {
  id: string
  user: ChatMessage | null
  assistants: ChatMessage[]
}

export interface TurnPreview {
  title: string
  subtitle: string
  attachCount: number
  isSlash: boolean
  slashLabel?: string
}

function firstLine(text: string, max = 80): string {
  const line = text.split(/\r?\n/)[0]?.trim() ?? text.trim()
  if (line.length <= max) return line
  return `${line.slice(0, max - 1)}…`
}

function stripMarkdown(text: string): string {
  return text
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/`[^`]+`/g, ' ')
    .replace(/[#>*_[\]()!-]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

export function buildTurnPreview(
  user: ChatMessage | null,
  assistants: ChatMessage[],
): TurnPreview {
  const userText = user?.content?.trim() ?? ''
  const assistantText = assistants[assistants.length - 1]?.content?.trim() ?? ''
  const attachCount = user?.attachments?.length ?? 0

  let title = ''
  let slashLabel: string | undefined
  const isSlash = userText.startsWith('/')

  if (userText.startsWith('/context')) {
    title = '查看 Context 占用'
    slashLabel = '/context'
  } else if (userText.startsWith('/compact')) {
    title = '压缩上下文'
    slashLabel = '/compact'
  } else if (userText.startsWith('/rewind')) {
    title = '回滚对话'
    slashLabel = '/rewind'
  } else if (userText) {
    title = firstLine(userText, 72)
  } else if (attachCount > 0) {
    title = attachCount === 1 ? '发送了 1 个附件' : `发送了 ${attachCount} 个附件`
  } else {
    title = '（空消息）'
  }

  let subtitle = ''
  if (assistantText) {
    subtitle = stripMarkdown(assistantText)
    if (subtitle.length > 96) subtitle = `${subtitle.slice(0, 95)}…`
  }

  return { title, subtitle, attachCount, isSlash, slashLabel }
}

export const HISTORY_PAGE_SIZE = 5
