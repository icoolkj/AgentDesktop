import { describe, expect, it } from 'vitest'
import { createSessionId, formatNdjson, parseNdjsonLine } from './index'

describe('parseNdjsonLine', () => {
  it('parses text_delta event', () => {
    expect(parseNdjsonLine('{"type":"text_delta","delta":"hi"}')).toEqual({
      type: 'text_delta',
      delta: 'hi',
    })
  })

  it('parses tool events', () => {
    expect(
      parseNdjsonLine('{"type":"tool_start","toolCallId":"c1","name":"read_file"}'),
    ).toEqual({
      type: 'tool_start',
      toolCallId: 'c1',
      name: 'read_file',
    })
  })

  it('parses file_changed event', () => {
    expect(
      parseNdjsonLine(
        '{"type":"file_changed","path":"/proj/a.ts","changeKind":"modified","content":"x"}',
      ),
    ).toEqual({
      type: 'file_changed',
      path: '/proj/a.ts',
      changeKind: 'modified',
      content: 'x',
    })
  })

  it('parses done event with sessionId', () => {
    expect(parseNdjsonLine('{"type":"done","sessionId":"hello"}')).toEqual({
      type: 'done',
      sessionId: 'hello',
    })
  })

  it('returns null for empty or invalid lines', () => {
    expect(parseNdjsonLine('')).toBeNull()
    expect(parseNdjsonLine('   ')).toBeNull()
    expect(parseNdjsonLine('not-json')).toBeNull()
  })
})

describe('formatNdjson', () => {
  it('appends newline', () => {
    expect(formatNdjson({ type: 'error', message: 'fail' })).toBe(
      '{"type":"error","message":"fail"}\n',
    )
  })
})

describe('createSessionId', () => {
  it('returns non-empty id', () => {
    expect(createSessionId().length).toBeGreaterThan(8)
  })
})
