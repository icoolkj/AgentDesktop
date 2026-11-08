import { describe, expect, it } from 'vitest'

import type { ClaudeTranscriptEntry } from './transcript'
import { transcriptToUiMessages } from './transcriptBuild'

describe('transcriptToUiMessages attachments', () => {
  it('maps adAttachments on user messages for history replay', () => {
    const entries: ClaudeTranscriptEntry[] = [
      {
        type: 'user',
        uuid: 'msg-uuid-1',
        timestamp: '2026-06-08T12:00:00.000Z',
        message: { role: 'user', content: 'see screenshot' },
        adAttachments: [
          {
            id: 'att-1',
            kind: 'image',
            name: 'screenshot.png',
            path: 'C:/proj/.agent-desktop/attachments/s1/msg-uuid-1/screenshot.png',
            source: 'store',
          },
        ],
      },
    ]

    const ui = transcriptToUiMessages(entries)
    expect(ui).toHaveLength(1)
    expect(ui[0]?.role).toBe('user')
    expect(ui[0]?.messageUuid).toBe('msg-uuid-1')
    expect(ui[0]?.attachments).toEqual([
      {
        id: 'att-1',
        kind: 'image',
        name: 'screenshot.png',
        filePath: 'C:/proj/.agent-desktop/attachments/s1/msg-uuid-1/screenshot.png',
        source: 'store',
      },
    ])
  })

  it('flushes trailing tool activity when assistant ends without text (CC JSONL shape)', () => {
    const entries: ClaudeTranscriptEntry[] = [
      {
        type: 'user',
        uuid: 'u1',
        message: { role: 'user', content: 'fix auth' },
      },
      {
        type: 'assistant',
        uuid: 'a1',
        message: {
          role: 'assistant',
          content: [{ type: 'tool_use', id: 'call-1', name: 'read_file', input: { path: 'a.java' } }],
        },
      },
      {
        type: 'user',
        uuid: 'u2',
        message: {
          role: 'user',
          content: [{ type: 'tool_result', tool_use_id: 'call-1', content: 'ok' }],
        },
      },
      {
        type: 'assistant',
        uuid: 'a2',
        message: {
          role: 'assistant',
          content: [{ type: 'tool_use', id: 'call-2', name: 'write_file', input: { path: 'b.java' } }],
        },
      },
      {
        type: 'user',
        uuid: 'u3',
        message: {
          role: 'user',
          content: [{ type: 'tool_result', tool_use_id: 'call-2', content: 'wrote' }],
        },
      },
    ]

    const ui = transcriptToUiMessages(entries)
    expect(ui).toHaveLength(2)
    expect(ui[0]?.role).toBe('user')
    expect(ui[1]?.role).toBe('assistant')
    expect(ui[1]?.tools?.length).toBe(2)
    expect(ui[1]?.tools?.[0]?.name).toBe('read_file')
    expect(ui[1]?.tools?.[1]?.name).toBe('write_file')
  })
})
