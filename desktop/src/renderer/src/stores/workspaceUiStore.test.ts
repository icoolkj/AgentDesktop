import { describe, expect, it } from 'vitest'

import { DEFAULT_WORKSPACE_UI_PREFS } from '@renderer/stores/workspaceUiStore'

describe('DEFAULT_WORKSPACE_UI_PREFS', () => {
  it('does not auto-scan problems on open by default', () => {
    expect(DEFAULT_WORKSPACE_UI_PREFS.autoScanProblemsOnOpen).toBe(false)
  })

  it('shows terminal tab by default when bottom panel opens', () => {
    expect(DEFAULT_WORKSPACE_UI_PREFS.bottomPanelTabOnStartup).toBe('terminal')
  })
})
