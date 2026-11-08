import { describe, expect, it } from 'vitest'
import {
  autoAcknowledgeWritesOnTurnComplete,
  blocksSendOnPendingReview,
  toCcPermissionMode,
  usesPostTurnReviewQueue,
} from './permissionModePolicy'

describe('permissionModePolicy (CC alignment)', () => {
  it('maps AD modes to CC permission modes', () => {
    expect(toCcPermissionMode('ask')).toBe('default')
    expect(toCcPermissionMode('auto')).toBe('acceptEdits')
    expect(toCcPermissionMode('plan')).toBe('plan')
  })

  it('only Ask uses post-turn review queue and send block', () => {
    expect(usesPostTurnReviewQueue('ask')).toBe(true)
    expect(usesPostTurnReviewQueue('auto')).toBe(false)
    expect(blocksSendOnPendingReview('ask')).toBe(true)
    expect(blocksSendOnPendingReview('auto')).toBe(false)
    expect(autoAcknowledgeWritesOnTurnComplete('auto')).toBe(true)
    expect(autoAcknowledgeWritesOnTurnComplete('ask')).toBe(false)
  })
})
