import type { ChatAttachment } from '@agent-desktop/shared-types'
import {
  API_MAX_MEDIA_PER_REQUEST,
  classifyComposerAttachmentPath,
  TEXT_ATTACHMENT_MAX_BYTES,
  unsupportedAttachmentMessage,
} from '@agent-desktop/shared-types'

import { assertImageBase64WithinLimit, prepareImageFromFile, prepareImageFromPath } from '@renderer/utils/imageAttachment'
import { preparePdfFromPath, type PreparePdfOptions } from '@renderer/utils/pdfAttachment'

export interface PendingComposerAttachment {
  id: string
  name: string
  kind: 'image' | 'text' | 'pdf'
  previewUrl?: string
  filePath?: string
  file?: File
}

export function createPendingId(): string {
  return crypto.randomUUID()
}

export function maxPendingAttachments(): number {
  return API_MAX_MEDIA_PER_REQUEST
}

export function classifyAttachmentPath(filePath: string): {
  kind: PendingComposerAttachment['kind'] | 'unsupported'
  error?: string
} {
  const kind = classifyComposerAttachmentPath(filePath)
  if (kind === 'unsupported') {
    return { kind, error: unsupportedAttachmentMessage(filePath) }
  }
  if (kind === 'text') return { kind: 'text' }
  // pdf_reference 是超大 PDF 的引用形式，pending 阶段按普通 PDF 处理
  if (kind === 'pdf_reference') return { kind: 'pdf' }
  return { kind }
}

export async function pendingToChatAttachments(
  items: PendingComposerAttachment[],
  pdfOptions: PreparePdfOptions = {},
): Promise<ChatAttachment[]> {
  const out: ChatAttachment[] = []
  let mediaCount = 0

  for (const item of items) {
    if (item.kind === 'image') {
      let attachment: ChatAttachment
      if (item.file) {
        attachment = await prepareImageFromFile(item.file, item.name)
      } else if (item.filePath) {
        attachment = await prepareImageFromPath(item.filePath)
      } else {
        continue
      }
      if (attachment.base64) assertImageBase64WithinLimit(attachment.base64)
      out.push(attachment)
      mediaCount++
      continue
    }

    if (item.kind === 'pdf' && item.filePath) {
      const attachment = await preparePdfFromPath(item.filePath, pdfOptions)
      out.push(attachment)
      if (attachment.kind === 'pdf') {
        mediaCount++
      }
      continue
    }

    if (item.kind === 'text' && item.filePath) {
      const content = await window.workspaceApi.readFile(item.filePath, {
        maxBytes: TEXT_ATTACHMENT_MAX_BYTES,
      })
      if (content) {
        out.push({
          kind: 'file',
          path: item.filePath,
          content,
          label: item.name,
        })
      }
    }
  }

  if (mediaCount > API_MAX_MEDIA_PER_REQUEST) {
    throw new Error(`最多 ${API_MAX_MEDIA_PER_REQUEST} 个图片/PDF（Claude Code API_MAX_MEDIA_PER_REQUEST）`)
  }

  return out
}

export function revokePendingPreviews(items: PendingComposerAttachment[]): void {
  for (const item of items) {
    if (item.previewUrl?.startsWith('blob:')) {
      URL.revokeObjectURL(item.previewUrl)
    }
  }
}
