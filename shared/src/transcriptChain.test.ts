import { describe, expect, it } from 'vitest'
import { buildConversationChain, computeLeafUuids, entriesToMessageMap, orderEntriesByChain } from './transcriptChain'
import type { ClaudeTranscriptEntry } from './transcript'

describe('transcriptChain (Claude Code parentUuid)', () => {
  const chain: ClaudeTranscriptEntry[] = [
    {
      type: 'user',
      uuid: 'u1',
      parentUuid: null,
      timestamp: '2026-01-01T00:00:00.000Z',
      message: { role: 'user', content: 'hi' },
    },
    {
      type: 'assistant',
      uuid: 'a1',
      parentUuid: 'u1',
      timestamp: '2026-01-01T00:00:01.000Z',
      message: {
        role: 'assistant',
        content: [{ type: 'tool_use', id: 'c1', name: 'read_file', input: {} }],
      },
    },
    {
      type: 'user',
      uuid: 'u2',
      parentUuid: 'a1',
      timestamp: '2026-01-01T00:00:02.000Z',
      message: {
        role: 'user',
        content: [{ type: 'tool_result', tool_use_id: 'c1', content: 'ok' }],
      },
    },
    {
      type: 'assistant',
      uuid: 'a2',
      parentUuid: 'u2',
      timestamp: '2026-01-01T00:00:03.000Z',
      message: { role: 'assistant', content: [{ type: 'text', text: 'done' }] },
    },
  ]

  it('orders entries from leaf via parentUuid chain', () => {
    const shuffled = [chain[2], chain[0], chain[3], chain[1]]
    const ordered = orderEntriesByChain(shuffled)
    expect(ordered.map(e => e.uuid)).toEqual(['u1', 'a1', 'u2', 'a2'])
  })

  it('buildConversationChain walks root to leaf', () => {
    const map = entriesToMessageMap(chain)
    const leaves = computeLeafUuids(map)
    expect(leaves.has('a2')).toBe(true)
    const leaf = map.get('a2')!
    const rebuilt = buildConversationChain(map, leaf)
    expect(rebuilt.map(m => m.uuid)).toEqual(['u1', 'a1', 'u2', 'a2'])
  })
})
