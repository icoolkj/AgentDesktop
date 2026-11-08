import { describe, expect, it, vi } from 'vitest'

import type { ChatAttachment } from '@agent-desktop/shared-types'

import type { ChatUserAttachment } from '@renderer/stores/chatStore'

import {
  applyStoredAttachmentPaths,
  buildPersistAttachmentInputs,
  mergeDisplayAttachments,
  uiAttachmentsToChat,
} from './persistUserAttachments'

describe('persistUserAttachments helpers', () => {
  it('buildPersistAttachmentInputs merges display chips with upload base64', () => {
    const display: ChatUserAttachment[] = [
      { id: 'd1', kind: 'image', name: 'shot.png', filePath: 'C:/ws/shot.png' },
    ]
    const uploads: ChatAttachment[] = [
      {
        kind: 'image',
        path: 'C:/ws/shot.png',
        label: 'shot.png',
        mimeType: 'image/png',
        base64: 'abc123',
      },
    ]

    const inputs = buildPersistAttachmentInputs(display, uploads)
    expect(inputs).toHaveLength(1)
    expect(inputs[0]).toMatchObject({
      id: 'd1',
      kind: 'image',
      name: 'shot.png',
      base64: 'abc123',
      mimeType: 'image/png',
    })
  })

  it('mergeDisplayAttachments adds folder chips from @folder attachments', () => {
    vi.stubGlobal('crypto', { randomUUID: () => 'uuid-folder' })
    const uploads: ChatAttachment[] = [
      { kind: 'folder', path: 'C:/ws/src', label: 'src' },
    ]
    const merged = mergeDisplayAttachments([], uploads)
    expect(merged).toHaveLength(1)
    expect(merged[0]?.name).toContain('src')
  })

  it('applyStoredAttachmentPaths rewrites external paths to store paths', () => {
    const display: ChatUserAttachment[] = [
      { id: 'd1', kind: 'pdf', name: 'doc.pdf', filePath: 'D:/Downloads/doc.pdf' },
    ]
    const uploads: ChatAttachment[] = [
      {
        kind: 'pdf',
        path: 'D:/Downloads/doc.pdf',
        label: 'doc.pdf',
        mimeType: 'application/pdf',
        base64: 'JVBERi0',
      },
    ]
    const inputs = buildPersistAttachmentInputs(display, uploads)
    const stored = [
      {
        id: 'd1',
        kind: 'pdf' as const,
        name: 'doc.pdf',
        path: 'C:/Users/admin/.agent-desktop/projects/test/attachments/s1/u1/doc.pdf',
        source: 'store' as const,
      },
    ]
    const send = applyStoredAttachmentPaths(uploads, inputs, stored)
    expect(send[0]?.path).toBe(stored[0]!.path)
  })

  it('uiAttachmentsToChat maps transcript attachments for chat store', () => {
    const mapped = uiAttachmentsToChat([
      {
        id: 'a1',
        kind: 'pdf',
        name: 'doc.pdf',
        filePath: 'C:/store/doc.pdf',
        source: 'store',
      },
    ])
    expect(mapped?.[0]).toMatchObject({ kind: 'pdf', filePath: 'C:/store/doc.pdf', source: 'store' })
  })
})
