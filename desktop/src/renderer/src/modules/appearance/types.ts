export type ColorThemeCategory = 'light' | 'dark'

export type FileIconThemeId = 'none' | 'minimal' | 'seti'

export type ColorThemeId =
  | 'default-light'
  | 'light-vs'
  | 'light-modern'
  | 'light-plus'
  | 'quiet-light'
  | 'solarized-light'
  | 'abyss'
  | 'default-dark'
  | 'midnight'
  | 'dark-vs'
  | 'dark-modern'
  | 'dark-plus'
  | 'kimbie-dark'
  | 'monokai'
  | 'monokai-dimmed'
  | 'red'

/** Full design-token contract — all UI chrome must use these, not hardcoded hex. */
export interface ColorThemeTokens {
  color: string
  background: string
  '--ide-fg': string
  '--ide-muted': string
  '--ide-border': string
  '--ide-accent': string
  '--ide-titlebar-bg': string
  '--ide-menubar-bg': string
  '--ide-activitybar-bg': string
  '--ide-sidebar-bg': string
  '--ide-editor-bg': string
  '--ide-tab-bar-bg': string
  '--ide-input-bg': string
  '--ide-dropdown-bg': string
  '--ide-activitybar-icon-idle': string
  '--ide-activitybar-icon-active': string
  '--ide-list-active-bg': string
  '--ide-list-active-fg': string
  '--ide-tree-fg': string
  '--ide-scrollbar-thumb': string
  '--ide-surface-bg': string
  '--ide-hover-overlay': string
  '--ide-shadow': string
  '--ide-on-accent': string
  '--ide-success': string
  '--ide-warning': string
  '--ide-error': string
  '--ide-bubble-user-bg': string
  '--ide-bubble-agent-bg': string
  '--ide-bubble-border': string
  '--ide-code-bg': string
  '--ide-welcome-fg': string
  '--ide-welcome-title': string
  '--ide-button-bg': string
  '--ide-button-border': string
  '--ide-button-primary-bg': string
  '--ide-button-primary-fg': string
  '--ide-git-modified': string
  '--ide-git-untracked': string
  '--ide-git-deleted': string
  '--ide-terminal-bg': string
  '--ide-terminal-fg': string
  '--ide-terminal-cursor': string
  '--ide-terminal-selection': string
}

export interface ColorThemeDefinition {
  id: ColorThemeId
  label: string
  category: ColorThemeCategory
  monaco: 'vs' | 'vs-dark'
  tokens: ColorThemeTokens
}

export interface AppearancePrefs {
  colorThemeId: ColorThemeId
  fileIconThemeId: FileIconThemeId
}

export interface FileIconThemeDefinition {
  id: FileIconThemeId
  label: string
  description: string
}
