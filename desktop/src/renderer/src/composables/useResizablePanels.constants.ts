export const PANEL_DEFAULTS = {
  sidebar: 260,
  chat: 360,
} as const

export const PANEL_LIMITS = {
  sidebar: { min: 180, max: 520 },
  chat: { min: 300, max: 860 },
  editor: { min: 300 },
} as const
