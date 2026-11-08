import type { AdTranscriptAttachment, ChatAttachment, UiChatAttachment } from '@agent-desktop/shared-types'

import type { ChatUserAttachment } from '@renderer/stores/chatStore'

export interface PersistUserAttachmentInput {
  id: string
  kind: 'image' | 'pdf' | 'pdf_reference' | 'text' | 'file' | 'folder'
  name: string
  filePath?: string
  base64?: string
  mimeType?: string
}

function basename(path: string): string {
  const parts = path.replace(/\\/g, '/').split('/')
  return parts[parts.length - 1] || path
}

export function buildPersistAttachmentInputs(
  displayAttachments: ChatUserAttachment[],
  allAttachments: ChatAttachment[],
): PersistUserAttachmentInput[] {
  const inputs: PersistUserAttachmentInput[] = []
  const coveredPaths = new Set<string>()

  for (const item of displayAttachments) {
    const upload = allAttachments.find(
      att =>
        (att.kind === 'image' || att.kind === 'pdf' || att.kind === 'pdf_reference') &&
        (att.path === item.filePath || att.label === item.name),
    )
    inputs.push({
      id: item.id,
      kind: item.kind,
      name: item.name,
      filePath: item.filePath,
      base64: upload?.base64,
      mimeType: upload?.mimeType,
    })
    if (item.filePath) coveredPaths.add(item.filePath)
  }

  for (const att of allAttachments) {
    if (att.kind === 'folder') {
      inputs.push({
        id: crypto.randomUUID(),
        kind: 'folder',
        name: att.label || basename(att.path),
        filePath: att.path,
      })
      continue
    }
    if (att.kind === 'file' && !coveredPaths.has(att.path)) {
      inputs.push({
        id: crypto.randomUUID(),
        kind: 'text',
        name: att.label || basename(att.path),
        filePath: att.path,
      })
      coveredPaths.add(att.path)
    }
  }

  return inputs
}

/** Map composer attachment paths to persisted store paths after appendUser. */
export function applyStoredAttachmentPaths(
  attachments: ChatAttachment[],
  persistInputs: PersistUserAttachmentInput[],
  stored: AdTranscriptAttachment[] | undefined,
): ChatAttachment[] {
  if (!stored?.length || !attachments.length) return attachments

  const storedById = new Map(
    stored.filter(item => item.source === 'store').map(item => [item.id, item.path]),
  )
  const originalToStored = new Map<string, string>()
  for (const input of persistInputs) {
    const storedPath = storedById.get(input.id)
    if (!storedPath) continue
    if (input.filePath) originalToStored.set(input.filePath, storedPath)
    originalToStored.set(input.name.toLowerCase(), storedPath)
  }

  return attachments.map(att => {
    const fromPath = att.path ? originalToStored.get(att.path) : undefined
    const fromLabel = att.label ? originalToStored.get(att.label.toLowerCase()) : undefined
    const storedPath = fromPath ?? fromLabel
    return storedPath ? { ...att, path: storedPath } : att
  })
}

export function applyStoredPathsToDisplayAttachments(
  display: ChatUserAttachment[],
  persistInputs: PersistUserAttachmentInput[],
  stored: AdTranscriptAttachment[] | undefined,
): ChatUserAttachment[] {
  if (!stored?.length) return display

  const storedById = new Map(
    stored.filter(item => item.source === 'store').map(item => [item.id, item.path]),
  )

  return display.map(item => {
    const input = persistInputs.find(p => p.id === item.id)
    const storedPath = input ? storedById.get(input.id) : undefined
    return storedPath ? { ...item, filePath: storedPath } : item
  })
}

export function mergeDisplayAttachments(
  displayAttachments: ChatUserAttachment[],
  allAttachments: ChatAttachment[],
): ChatUserAttachment[] {
  const merged = [...displayAttachments]
  const seen = new Set(merged.map(item => `${item.kind}:${item.filePath ?? item.name}`))

  for (const att of allAttachments) {
    if (att.kind === 'folder') {
      const key = `folder:${att.path}`
      if (seen.has(key)) continue
      seen.add(key)
      merged.push({
        id: crypto.randomUUID(),
        kind: 'text',
        name: `📁 ${att.label || basename(att.path)}`,
        filePath: att.path,
      })
      continue
    }
    if (att.kind === 'file') {
      const key = `text:${att.path}`
      if (seen.has(key)) continue
      seen.add(key)
      merged.push({
        id: crypto.randomUUID(),
        kind: 'text',
        name: att.label || basename(att.path),
        filePath: att.path,
      })
    }
  }

  return merged
}

export function uiAttachmentsToChat(
  items: UiChatAttachment[] | undefined,
): ChatUserAttachment[] | undefined {
  if (!items?.length) return undefined
  return items.map(item => ({
    id: item.id,
    kind: item.kind === 'file' || item.kind === 'folder' ? 'text' : item.kind,
    name: item.name,
    filePath: item.filePath,
    source: item.source,
  }))
}
