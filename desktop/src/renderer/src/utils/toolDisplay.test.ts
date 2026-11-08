import { describe, expect, it } from 'vitest'
import { buildAgentRunSummary, classifyToolGroup, groupToolActivities } from './toolDisplay'

describe('toolDisplay grouping', () => {
  it('classifies read/search/edit tools', () => {
    expect(classifyToolGroup('read_file')).toBe('read')
    expect(classifyToolGroup('grep')).toBe('search')
    expect(classifyToolGroup('search_replace')).toBe('edit')
    expect(classifyToolGroup('Task')).toBe('other')
  })

  it('groups mixed tool activity with live hints', () => {
    const groups = groupToolActivities([
      {
        toolCallId: '1',
        name: 'read_file',
        status: 'done',
        argsJson: '{"path":"src/App.java"}',
        startedAt: 0,
        endedAt: 1200,
      },
      {
        toolCallId: '2',
        name: 'grep',
        status: 'running',
        argsJson: '{"pattern":"register"}',
        startedAt: 1300,
      },
      {
        toolCallId: '3',
        name: 'search_replace',
        status: 'done',
        argsJson: '{"path":"src/UserService.java"}',
        startedAt: 2000,
        endedAt: 3500,
      },
    ])

    expect(groups.map(g => g.kind)).toEqual(['read', 'search', 'edit'])
    expect(groups[1]?.liveHint).toBe('register')
    const summary = buildAgentRunSummary([
      {
        toolCallId: '1',
        name: 'read_file',
        status: 'done',
        startedAt: 0,
        endedAt: 1000,
      },
    ])
    expect(summary?.totalSteps).toBe(1)
    expect(summary?.headline).toContain('1 步')
  })
})
