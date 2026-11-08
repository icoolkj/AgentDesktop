import { copyFile, mkdir, writeFile } from 'node:fs/promises'
import { extname, join } from 'node:path'

import type { AdTranscriptAttachment } from '@agent-desktop/shared-types'

import { getProjectDir } from './claudeSessionPortable'

export interface PersistUserAttachmentInput {
  id: string
  kind: 'image' | 'pdf' | 'pdf_reference' | 'text' | 'file' | 'folder'
  name: string
  filePath?: string
  base64?: string
  mimeType?: string
}

function sanitizeFileName(name: string): string {
  // 控制字符 \x00-\x1f 必须被替换，否则会写出非法文件名
  // eslint-disable-next-line no-control-regex
  const cleaned = name.replace(/[<>:"/\\|?*\x00-\x1f]/g, '_').trim()
  return cleaned || 'attachment'
}

function guessExtension(kind: string, mimeType?: string, filePath?: string): string {
  const fromPath = filePath ? extname(filePath) : ''
  if (fromPath) return fromPath
  if (mimeType?.includes('png')) return '.png'
  if (mimeType?.includes('jpeg') || mimeType?.includes('jpg')) return '.jpg'
  if (mimeType?.includes('webp')) return '.webp'
  if (mimeType?.includes('gif')) return '.gif'
  if (mimeType?.includes('pdf')) return '.pdf'
  if (kind === 'pdf') return '.pdf'
  if (kind === 'image') return '.png'
  if (kind === 'text' || kind === 'file') return '.txt'
  return ''
}

export interface AppendUserResult {
  messageUuid: string
  storedAttachments?: AdTranscriptAttachment[]
}

/**
 * CC parity: ~/.claude/image-cache/{sessionId}/ for pasted images.
 * AD extension (per project + message, JSONL adAttachments replay):
 * ~/.agent-desktop/projects/{sanitized-cwd}/attachments/{sessionId}/{messageUuid}/
 */
export function attachmentStoreDir(sessionId: string, cwd: string, messageUuid: string): string {
  return join(getProjectDir(cwd), 'attachments', sessionId, messageUuid)
}

export async function persistUserAttachments(
  sessionId: string,
  cwd: string,
  messageUuid: string,
  inputs: PersistUserAttachmentInput[],
): Promise<AdTranscriptAttachment[]> {
  if (inputs.length === 0) return []

  const dir = attachmentStoreDir(sessionId, cwd, messageUuid)
  await mkdir(dir, { recursive: true })

  const results: AdTranscriptAttachment[] = []
  const usedNames = new Set<string>()

  for (const input of inputs) {
    const safeBase = sanitizeFileName(input.name)
    let fileName = safeBase
    let n = 1
    while (usedNames.has(fileName.toLowerCase())) {
      const ext = extname(safeBase)
      const stem = ext ? safeBase.slice(0, -ext.length) : safeBase
      fileName = `${stem}-${n}${ext || guessExtension(input.kind, input.mimeType, input.filePath)}`
      n++
    }
    usedNames.add(fileName.toLowerCase())

    if (input.base64) {
      const ext = extname(fileName) || guessExtension(input.kind, input.mimeType, input.filePath)
      const finalName = extname(fileName) ? fileName : `${fileName}${ext}`
      const dest = join(dir, finalName)
      await writeFile(dest, Buffer.from(input.base64, 'base64'))
      results.push({
        id: input.id,
        kind: input.kind === 'file' ? 'text' : input.kind === 'pdf_reference' ? 'pdf' : input.kind,
        name: input.name,
        path: dest,
        source: 'store',
      })
      continue
    }

    if (!input.filePath) continue

    if (input.kind === 'image' || input.kind === 'pdf' || input.kind === 'pdf_reference') {
      const ext = extname(input.filePath) || guessExtension(input.kind, input.mimeType, input.filePath)
      const finalName = extname(fileName) ? fileName : `${fileName}${ext}`
      const dest = join(dir, finalName)
      await copyFile(input.filePath, dest)
      results.push({
        id: input.id,
        kind: input.kind === 'pdf_reference' ? 'pdf' : input.kind,
        name: input.name,
        path: dest,
        source: 'store',
      })
      continue
    }

    results.push({
      id: input.id,
      kind: input.kind,
      name: input.name,
      path: input.filePath,
      source: 'workspace',
    })
  }

  return results
}
