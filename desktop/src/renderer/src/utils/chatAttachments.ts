import type { ChatAttachment } from '@agent-desktop/shared-types'
import { TEXT_ATTACHMENT_MAX_BYTES } from '@agent-desktop/shared-types'

import type { EditorSelectionSnapshot } from '@renderer/utils/editorSearchBridge'
import { relativePathFromRoot } from '@renderer/utils/lineDiff'

const MAX_ATTACHMENT_BYTES = TEXT_ATTACHMENT_MAX_BYTES
const SELECTION_TOKENS = new Set(['selection', 'sel'])

export interface ParsedComposerInput {
  message: string
  attachments: ChatAttachment[]
}

function formatSelectionContent(
  selection: EditorSelectionSnapshot,
  workspaceRoot: string | undefined,
): string {
  const rel = workspaceRoot ? relativePathFromRoot(workspaceRoot, selection.path) : selection.path
  const range =
    selection.startLine === selection.endLine
      ? `${selection.startLine}:${selection.startColumn}-${selection.endColumn}`
      : `${selection.startLine}:${selection.startColumn}-${selection.endLine}:${selection.endColumn}`
  const header = `// ${rel} (${range})`
  const body =
    selection.text.length > MAX_ATTACHMENT_BYTES
      ? selection.text.slice(0, MAX_ATTACHMENT_BYTES)
      : selection.text
  return `${header}\n${body}`
}

function selectionLabel(selection: EditorSelectionSnapshot, workspaceRoot: string | undefined): string {
  const rel = workspaceRoot ? relativePathFromRoot(workspaceRoot, selection.path) : selection.path
  return `${rel}:${selection.startLine}-${selection.endLine}`
}

/** Parse `@relative/path` and `@selection` tokens; load file contents and editor selection. */
export async function buildChatAttachments(
  message: string,
  workspaceRoot: string | undefined,
  selection?: EditorSelectionSnapshot | null,
): Promise<ParsedComposerInput> {
  const pattern = /@([^\s@]+)/g
  const seen = new Set<string>()
  const attachments: ChatAttachment[] = []
  let match: RegExpExecArray | null

  while ((match = pattern.exec(message)) !== null) {
    const token = match[1]?.trim()
    if (!token || seen.has(token.toLowerCase())) continue
    seen.add(token.toLowerCase())

    if (SELECTION_TOKENS.has(token.toLowerCase())) {
      if (selection?.text) {
        attachments.push({
          kind: 'selection',
          path: selection.path,
          content: formatSelectionContent(selection, workspaceRoot),
          label: selectionLabel(selection, workspaceRoot),
        })
      }
      continue
    }

    if (!workspaceRoot) continue

    const abs = resolveWorkspacePath(workspaceRoot, token)
    if (!abs) continue

    try {
      const kind = await window.workspaceApi.getPathKind(abs)
      if (kind === 'directory') {
        attachments.push({ kind: 'folder', path: abs, label: token })
        continue
      }
      if (kind !== 'file') continue

      const content = await window.workspaceApi.readFile(abs, { maxBytes: MAX_ATTACHMENT_BYTES })
      if (!content) continue
      attachments.push({ kind: 'file', path: abs, content, label: token })
    } catch {
      // skip unreadable attachment
    }
  }

  return { message, attachments }
}

function resolveWorkspacePath(root: string, relative: string): string | null {
  const clean = relative.replace(/^[/\\]+/, '').replace(/\.\./g, '')
  if (!clean) return null
  const sep = root.includes('\\') ? '\\' : '/'
  return `${root.replace(/[/\\]+$/, '')}${sep}${clean.replace(/\//g, sep)}`
}

export function extractAtQuery(text: string, cursor: number): string | null {
  const before = text.slice(0, cursor)
  const at = before.lastIndexOf('@')
  if (at < 0) return null
  const fragment = before.slice(at + 1)
  if (fragment.includes('\n') || fragment.includes(' ')) return null
  return fragment
}

export function insertAtPath(text: string, cursor: number, relativePath: string): { text: string; cursor: number } {
  const before = text.slice(0, cursor)
  const at = before.lastIndexOf('@')
  if (at < 0) {
    const insert = `@${relativePath} `
    return { text: text.slice(0, cursor) + insert + text.slice(cursor), cursor: cursor + insert.length }
  }
  const next = `${text.slice(0, at)}@${relativePath} ${text.slice(cursor)}`
  const newCursor = at + relativePath.length + 2
  return { text: next, cursor: newCursor }
}
