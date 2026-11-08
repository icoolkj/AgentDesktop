import { describe, expect, it } from 'vitest'
import {
  SYNTHETIC_TOOL_RESULT_PLACEHOLDER,
  ensureToolResultPairing,
  normalizeTranscriptForAPI,
  transcriptToLlmHistory,
} from './transcriptNormalize'
import { transcriptToUiMessages } from './transcriptBuild'
import type { ClaudeTranscriptEntry } from './transcript'

describe('transcriptNormalize (Claude Code parity)', () => {
  it('merges parallel assistant rows by message.id', () => {
    const entries: ClaudeTranscriptEntry[] = [
      {
        type: 'user',
        uuid: 'u1',
        parentUuid: null,
        timestamp: '2026-01-01T00:00:00.000Z',
        message: { role: 'user', content: 'scan' },
      },
      {
        type: 'assistant',
        uuid: 'a1',
        parentUuid: 'u1',
        timestamp: '2026-01-01T00:00:01.000Z',
        message: {
          id: 'msg1',
          role: 'assistant',
          content: [{ type: 'tool_use', id: 'c1', name: 'list_dir', input: {} }],
        },
      },
      {
        type: 'assistant',
        uuid: 'a2',
        parentUuid: 'u1',
        timestamp: '2026-01-01T00:00:02.000Z',
        message: {
          id: 'msg1',
          role: 'assistant',
          content: [{ type: 'tool_use', id: 'c2', name: 'read_file', input: {} }],
        },
      },
    ]

    const normalized = normalizeTranscriptForAPI(entries)
    expect(normalized).toHaveLength(2)
    expect(normalized[1]?.type).toBe('assistant')
    expect(normalized[1]?.content.filter(b => b.type === 'tool_use')).toHaveLength(2)
  })

  it('ensureToolResultPairing inserts synthetic tool_result for missing ids (Claude)', () => {
    const messages = normalizeTranscriptForAPI([
      {
        type: 'user',
        uuid: 'u1',
        message: { role: 'user', content: 'hi' },
      },
      {
        type: 'assistant',
        uuid: 'a1',
        message: {
          role: 'assistant',
          content: [{ type: 'tool_use', id: 'call_00_x', name: 'list_dir', input: {} }],
        },
      },
      {
        type: 'user',
        uuid: 'u2',
        message: { role: 'user', content: '功能开发' },
      },
    ])

    const paired = ensureToolResultPairing(messages)
    const toolResults = paired.flatMap(m =>
      m.content.filter(b => b.type === 'tool_result'),
    )
    expect(toolResults).toHaveLength(1)
    expect(toolResults[0]).toMatchObject({
      tool_use_id: 'call_00_x',
      content: SYNTHETIC_TOOL_RESULT_PLACEHOLDER,
      is_error: true,
    })
  })

  it('builds OpenAI history with synthetic tool when result missing', () => {
    const entries: ClaudeTranscriptEntry[] = [
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
          content: [{ type: 'tool_use', id: 'call_00_x', name: 'list_dir', input: {} }],
        },
      },
      {
        type: 'user',
        uuid: 'u2',
        parentUuid: 'a1',
        timestamp: '2026-01-01T00:00:02.000Z',
        message: { role: 'user', content: '功能开发' },
      },
    ]

    const history = transcriptToLlmHistory(entries)
    expect(history.map(h => h.role)).toEqual(['user', 'assistant', 'tool', 'user'])
    expect(history[2].toolCallId).toBe('call_00_x')
    expect(history[2].content).toBe(SYNTHETIC_TOOL_RESULT_PLACEHOLDER)
  })

  it('builds LLM history with complete tool turns', () => {
    const entries: ClaudeTranscriptEntry[] = [
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
          content: [{ type: 'tool_result', tool_use_id: 'c1', content: 'file' }],
        },
      },
      {
        type: 'assistant',
        uuid: 'a2',
        parentUuid: 'u2',
        timestamp: '2026-01-01T00:00:03.000Z',
        message: { role: 'assistant', content: [{ type: 'text', text: 'ok' }] },
      },
    ]

    const history = transcriptToLlmHistory(entries)
    expect(history.map(h => h.role)).toEqual(['user', 'assistant', 'tool', 'assistant'])
  })
})

describe('transcriptBuild UI', () => {
  it('builds UI from assistant tool_use + user tool_result blocks', () => {
    const entries: ClaudeTranscriptEntry[] = [
      {
        type: 'user',
        uuid: 'u1',
        timestamp: '2026-01-01T00:00:00.000Z',
        message: { role: 'user', content: 'analyze' },
      },
      {
        type: 'assistant',
        uuid: 'a1',
        parentUuid: 'u1',
        timestamp: '2026-01-01T00:00:01.000Z',
        message: {
          role: 'assistant',
          content: [{ type: 'tool_use', id: 'c1', name: 'list_dir', input: { path: '.' } }],
        },
      },
      {
        type: 'user',
        uuid: 'u2',
        parentUuid: 'a1',
        timestamp: '2026-01-01T00:00:02.000Z',
        message: {
          role: 'user',
          content: [{ type: 'tool_result', tool_use_id: 'c1', content: 'a\nb' }],
        },
      },
      {
        type: 'assistant',
        uuid: 'a2',
        parentUuid: 'u2',
        timestamp: '2026-01-01T00:00:03.000Z',
        message: { role: 'assistant', content: [{ type: 'text', text: 'Done' }] },
      },
    ]

    const ui = transcriptToUiMessages(entries)
    expect(ui).toHaveLength(2)
    expect(ui[1].tools?.[0]?.name).toBe('list_dir')
    expect(ui[1].content).toBe('Done')
  })
})
