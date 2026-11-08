import { describe, expect, it } from 'vitest'

import {
  PDF_AT_MENTION_INLINE_THRESHOLD,
  shouldUsePdfReference,
} from '@agent-desktop/shared-types'

describe('pdf attachment policy', () => {
  it('uses reference when page count exceeds CC inline threshold', () => {
    expect(shouldUsePdfReference(PDF_AT_MENTION_INLINE_THRESHOLD + 1)).toBe(true)
    expect(shouldUsePdfReference(PDF_AT_MENTION_INLINE_THRESHOLD)).toBe(false)
  })

  it('allows inline document for small PDFs regardless of model', () => {
    expect(shouldUsePdfReference(5)).toBe(false)
  })
})
