export interface LlmModelInfo {
  id: string
  ownedBy?: string
}

export interface LlmModelsResponse {
  models: LlmModelInfo[]
  defaultModel?: string
  error?: string
}

export type ChatModelSelection = 'auto' | string

export interface ModelPreferences {
  /** Composer selection: auto uses server default from settings. */
  chatSelection: ChatModelSelection
  /** Last manually picked model when Auto is off. */
  lastManualSelection?: string
  /** Which models appear in composer dropdown. */
  enabled: Record<string, boolean>
  /** Manually added model ids not returned by /v1/models. */
  custom: string[]
}

export const MODEL_PREFS_STORAGE_KEY = 'agent-desktop.model-preferences'

export const DEFAULT_MODEL_PREFS: ModelPreferences = {
  chatSelection: 'auto',
  enabled: {},
  custom: [],
}
