export interface LlmSettings {
  enabled: boolean
  provider: string
  baseUrl: string
  apiKey: string
  model: string
  temperature: number
  maxTokens: number
  timeoutSeconds: number
  streamToolTurns: boolean
  streamAnswerTurns: boolean
  configured: boolean
  apiKeySet: boolean
  settingsPath?: string
}

export const API_KEY_MASK = '********'
