import type {
  AppearancePrefs,
  ColorThemeDefinition,
  ColorThemeId,
  ColorThemeTokens,
  FileIconThemeDefinition,
  FileIconThemeId,
} from './types'
import { harmonizeDarkTokens, harmonizeLightTokens } from './themeHarmonize'

const STORAGE_KEY = 'agent-desktop:appearance'

export const DEFAULT_COLOR_THEME_ID: ColorThemeId = 'default-dark'
export const DEFAULT_FILE_ICON_THEME_ID: FileIconThemeId = 'seti'

function syncNativeTitleBar(theme: ColorThemeDefinition): void {
  if (typeof window === 'undefined') return
  if (window.desktopEnv?.platform !== 'win32') return
  void window.desktopApi.setTitleBarOverlay({
    color: theme.tokens['--ide-titlebar-bg'],
    symbolColor: theme.tokens['--ide-fg'],
  })
}

function darkTokens(overrides: Partial<ColorThemeTokens>): ColorThemeTokens {
  const base: ColorThemeTokens = {
    color: '#cccccc',
    background: '#1e1e1e',
    '--ide-fg': '#cccccc',
    '--ide-muted': '#969696',
    '--ide-border': '#2b2b2b',
    '--ide-accent': '#007acc',
    '--ide-titlebar-bg': '#181818',
    '--ide-activitybar-bg': '#181818',
    '--ide-menubar-bg': '#1f1f1f',
    '--ide-sidebar-bg': '#252526',
    '--ide-editor-bg': '#1e1e1e',
    '--ide-tab-bar-bg': '#2d2d2d',
    '--ide-input-bg': '#3c3c3c',
    '--ide-dropdown-bg': '#252526',
    '--ide-activitybar-icon-idle': '#858585',
    '--ide-activitybar-icon-active': '#ffffff',
    '--ide-list-active-bg': '#094771',
    '--ide-list-active-fg': '#ffffff',
    '--ide-tree-fg': '#c9cdd3',
    '--ide-scrollbar-thumb': 'rgba(121, 121, 121, 0.35)',
    '--ide-surface-bg': '#12151c',
    '--ide-hover-overlay': 'rgba(255, 255, 255, 0.06)',
    '--ide-shadow': '0 8px 24px rgba(0, 0, 0, 0.45)',
    '--ide-on-accent': '#ffffff',
    '--ide-success': '#89d185',
    '--ide-warning': '#cca700',
    '--ide-error': '#f48771',
    '--ide-bubble-user-bg': '#1e3a5f',
    '--ide-bubble-agent-bg': '#1c2230',
    '--ide-bubble-border': '#2a2f3a',
    '--ide-code-bg': '#0f1117',
    '--ide-welcome-fg': '#6b7280',
    '--ide-welcome-title': '#9aa0a6',
    '--ide-button-bg': '#1c2230',
    '--ide-button-border': '#3a4254',
    '--ide-button-primary-bg': '#007acc',
    '--ide-button-primary-fg': '#ffffff',
    '--ide-git-modified': '#e2c08d',
    '--ide-git-untracked': '#73c991',
    '--ide-git-deleted': '#f48771',
    '--ide-terminal-bg': '#1e1e1e',
    '--ide-terminal-fg': '#cccccc',
    '--ide-terminal-cursor': '#cccccc',
    '--ide-terminal-selection': '#094771',
    ...overrides,
  }
  return { ...harmonizeDarkTokens(base), ...overrides }
}

function lightTokens(overrides: Partial<ColorThemeTokens>): ColorThemeTokens {
  const base: ColorThemeTokens = {
    color: '#333333',
    background: '#ffffff',
    '--ide-fg': '#333333',
    '--ide-muted': '#6e7681',
    '--ide-border': '#e5e5e5',
    '--ide-accent': '#006ab1',
    '--ide-titlebar-bg': '#f3f3f3',
    '--ide-menubar-bg': '#f3f3f3',
    '--ide-activitybar-bg': '#f3f3f3',
    '--ide-sidebar-bg': '#f3f3f3',
    '--ide-editor-bg': '#ffffff',
    '--ide-tab-bar-bg': '#ececec',
    '--ide-input-bg': '#ffffff',
    '--ide-dropdown-bg': '#ffffff',
    '--ide-activitybar-icon-idle': '#424242',
    '--ide-activitybar-icon-active': '#007acc',
    '--ide-list-active-bg': '#0060c0',
    '--ide-list-active-fg': '#ffffff',
    '--ide-tree-fg': '#383838',
    '--ide-scrollbar-thumb': 'rgba(0, 0, 0, 0.2)',
    '--ide-surface-bg': '#fafafa',
    '--ide-hover-overlay': 'rgba(0, 0, 0, 0.06)',
    '--ide-shadow': '0 8px 24px rgba(0, 0, 0, 0.12)',
    '--ide-on-accent': '#ffffff',
    '--ide-success': '#1a7f37',
    '--ide-warning': '#9a6700',
    '--ide-error': '#cf222e',
    '--ide-bubble-user-bg': '#dce8f7',
    '--ide-bubble-agent-bg': '#f6f8fa',
    '--ide-bubble-border': '#d0d7de',
    '--ide-code-bg': '#f6f8fa',
    '--ide-welcome-fg': '#6e7681',
    '--ide-welcome-title': '#24292f',
    '--ide-button-bg': '#f6f8fa',
    '--ide-button-border': '#d0d7de',
    '--ide-button-primary-bg': '#006ab1',
    '--ide-button-primary-fg': '#ffffff',
    '--ide-git-modified': '#953800',
    '--ide-git-untracked': '#116329',
    '--ide-git-deleted': '#cf222e',
    '--ide-terminal-bg': '#ffffff',
    '--ide-terminal-fg': '#333333',
    '--ide-terminal-cursor': '#333333',
    '--ide-terminal-selection': '#0060c0',
    ...overrides,
  }
  return { ...harmonizeLightTokens(base), ...overrides }
}

export const COLOR_THEMES: ColorThemeDefinition[] = [
  {
    id: 'default-light',
    label: 'Default Light',
    category: 'light',
    monaco: 'vs',
    tokens: lightTokens({
      background: '#ffffff',
      '--ide-editor-bg': '#ffffff',
      '--ide-sidebar-bg': '#f8f8f8',
      '--ide-accent': '#006ab1',
    }),
  },
  {
    id: 'light-vs',
    label: 'Light (Visual Studio)',
    category: 'light',
    monaco: 'vs',
    tokens: lightTokens({
      background: '#f6f6f6',
      '--ide-editor-bg': '#ffffff',
      '--ide-sidebar-bg': '#f3f3f3',
    }),
  },
  {
    id: 'light-modern',
    label: 'Light Modern',
    category: 'light',
    monaco: 'vs',
    tokens: lightTokens({
      background: '#f3f3f3',
      '--ide-editor-bg': '#ffffff',
      '--ide-sidebar-bg': '#f3f3f3',
      '--ide-tab-bar-bg': '#f3f3f3',
    }),
  },
  {
    id: 'light-plus',
    label: 'Light+',
    category: 'light',
    monaco: 'vs',
    tokens: lightTokens({
      background: '#ffffff',
      '--ide-editor-bg': '#ffffff',
      '--ide-sidebar-bg': '#f3f3f3',
    }),
  },
  {
    id: 'quiet-light',
    label: 'Quiet Light',
    category: 'light',
    monaco: 'vs',
    tokens: lightTokens({
      background: '#f5f5f5',
      '--ide-editor-bg': '#fafafa',
      '--ide-sidebar-bg': '#ececec',
      '--ide-fg': '#333333',
    }),
  },
  {
    id: 'solarized-light',
    label: 'Solarized Light',
    category: 'light',
    monaco: 'vs',
    tokens: lightTokens({
      background: '#fdf6e3',
      '--ide-editor-bg': '#fdf6e3',
      '--ide-sidebar-bg': '#eee8d5',
      '--ide-fg': '#586e75',
      '--ide-muted': '#839496',
      '--ide-border': '#d3cbb7',
      '--ide-accent': '#268bd2',
      '--ide-tree-fg': '#657b83',
    }),
  },
  {
    id: 'abyss',
    label: 'Abyss',
    category: 'dark',
    monaco: 'vs-dark',
    tokens: darkTokens({
      background: '#000c18',
      '--ide-editor-bg': '#000c18',
      '--ide-sidebar-bg': '#001221',
      '--ide-border': '#1a3050',
      '--ide-accent': '#0098ff',
    }),
  },
  {
    id: 'default-dark',
    label: 'Default Dark',
    category: 'dark',
    monaco: 'vs-dark',
    tokens: darkTokens({
      background: '#1e1e1e',
      '--ide-editor-bg': '#1e1e1e',
      '--ide-sidebar-bg': '#252526',
    }),
  },
  {
    id: 'midnight',
    label: 'Midnight',
    category: 'dark',
    monaco: 'vs-dark',
    tokens: darkTokens({
      background: '#0b0d12',
      '--ide-editor-bg': '#0b0d12',
      '--ide-sidebar-bg': '#141820',
      '--ide-titlebar-bg': '#0b0d12',
      '--ide-activitybar-bg': '#0b0d12',
      '--ide-tab-bar-bg': '#141820',
      '--ide-accent': '#4da3ff',
    }),
  },
  {
    id: 'dark-vs',
    label: 'Dark (Visual Studio)',
    category: 'dark',
    monaco: 'vs-dark',
    tokens: darkTokens({
      background: '#1e1e1e',
      '--ide-editor-bg': '#1e1e1e',
      '--ide-sidebar-bg': '#252526',
      '--ide-tab-bar-bg': '#2d2d30',
    }),
  },
  {
    id: 'dark-modern',
    label: 'Dark Modern',
    category: 'dark',
    monaco: 'vs-dark',
    tokens: darkTokens({
      background: '#1f1f1f',
      '--ide-editor-bg': '#1f1f1f',
      '--ide-sidebar-bg': '#252526',
      '--ide-titlebar-bg': '#181818',
    }),
  },
  {
    id: 'dark-plus',
    label: 'Dark+',
    category: 'dark',
    monaco: 'vs-dark',
    tokens: darkTokens({
      background: '#1e1e1e',
      '--ide-editor-bg': '#1e1e1e',
      '--ide-sidebar-bg': '#252526',
    }),
  },
  {
    id: 'kimbie-dark',
    label: 'Kimbie Dark',
    category: 'dark',
    monaco: 'vs-dark',
    tokens: darkTokens({
      background: '#221a0f',
      '--ide-editor-bg': '#221a0f',
      '--ide-sidebar-bg': '#2a2115',
      '--ide-fg': '#d3c8aa',
      '--ide-muted': '#a89774',
      '--ide-border': '#3d3224',
      '--ide-accent': '#dc9656',
      '--ide-tree-fg': '#d3c8aa',
    }),
  },
  {
    id: 'monokai',
    label: 'Monokai',
    category: 'dark',
    monaco: 'vs-dark',
    tokens: darkTokens({
      background: '#272822',
      '--ide-editor-bg': '#272822',
      '--ide-sidebar-bg': '#1e1f1c',
      '--ide-fg': '#f8f8f2',
      '--ide-muted': '#90908a',
      '--ide-border': '#3e3d32',
      '--ide-accent': '#66d9ef',
      '--ide-tree-fg': '#f8f8f2',
    }),
  },
  {
    id: 'monokai-dimmed',
    label: 'Monokai Dimmed',
    category: 'dark',
    monaco: 'vs-dark',
    tokens: darkTokens({
      background: '#1e1e1e',
      '--ide-editor-bg': '#1e1e1e',
      '--ide-sidebar-bg': '#252526',
      '--ide-fg': '#c5c8c6',
      '--ide-muted': '#888888',
      '--ide-accent': '#9a6e3a',
    }),
  },
  {
    id: 'red',
    label: 'Red',
    category: 'dark',
    monaco: 'vs-dark',
    tokens: darkTokens({
      background: '#390000',
      '--ide-editor-bg': '#390000',
      '--ide-sidebar-bg': '#420000',
      '--ide-titlebar-bg': '#350000',
      '--ide-activitybar-bg': '#350000',
      '--ide-tab-bar-bg': '#400000',
      '--ide-fg': '#ffcccc',
      '--ide-muted': '#cc9999',
      '--ide-border': '#5a1515',
      '--ide-accent': '#ff6666',
      '--ide-tree-fg': '#ffcccc',
      '--ide-list-active-bg': '#7a2020',
      '--ide-on-accent': '#ffffff',
    }),
  },
]

export const FILE_ICON_THEMES: FileIconThemeDefinition[] = [
  { id: 'none', label: '无', description: '不显示文件图标' },
  { id: 'minimal', label: 'Minimal', description: '简约图标' },
  { id: 'seti', label: 'Seti', description: '彩色文件图标（默认）' },
]

const colorThemeMap = new Map(COLOR_THEMES.map(theme => [theme.id, theme]))

/** Map legacy persisted theme ids to current ids. */
const LEGACY_COLOR_THEME_IDS: Record<string, ColorThemeId> = {
  'cursor-light': 'default-light',
  'cursor-dark': 'default-dark',
  'cursor-dark-midnight': 'midnight',
}

export function normalizeColorThemeId(id: string | undefined): ColorThemeId {
  if (!id) return DEFAULT_COLOR_THEME_ID
  const legacy = LEGACY_COLOR_THEME_IDS[id]
  if (legacy) return legacy
  if (colorThemeMap.has(id as ColorThemeId)) return id as ColorThemeId
  return DEFAULT_COLOR_THEME_ID
}

export function getColorTheme(id: ColorThemeId): ColorThemeDefinition {
  return colorThemeMap.get(id) ?? colorThemeMap.get(DEFAULT_COLOR_THEME_ID)!
}

export function loadAppearancePrefs(): AppearancePrefs {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return {
        colorThemeId: DEFAULT_COLOR_THEME_ID,
        fileIconThemeId: DEFAULT_FILE_ICON_THEME_ID,
      }
    }
    const parsed = JSON.parse(raw) as Partial<AppearancePrefs>
    const colorThemeId = normalizeColorThemeId(parsed.colorThemeId)
    const fileIconThemeId =
      parsed.fileIconThemeId === 'none' ||
      parsed.fileIconThemeId === 'minimal' ||
      parsed.fileIconThemeId === 'seti'
        ? parsed.fileIconThemeId
        : DEFAULT_FILE_ICON_THEME_ID
    return { colorThemeId, fileIconThemeId }
  } catch {
    return {
      colorThemeId: DEFAULT_COLOR_THEME_ID,
      fileIconThemeId: DEFAULT_FILE_ICON_THEME_ID,
    }
  }
}

export function saveAppearancePrefs(prefs: AppearancePrefs): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(prefs))
}

export function applyColorTheme(theme: ColorThemeDefinition): void {
  const root = document.documentElement
  root.dataset.colorTheme = theme.id
  root.style.colorScheme = theme.category
  root.style.color = theme.tokens.color
  root.style.background = theme.tokens.background

  for (const [key, value] of Object.entries(theme.tokens)) {
    if (key === 'color' || key === 'background') continue
    root.style.setProperty(key, value)
  }

  syncNativeTitleBar(theme)
}

export function applyColorThemeById(id: ColorThemeId): ColorThemeDefinition {
  const theme = getColorTheme(id)
  applyColorTheme(theme)
  return theme
}

export type { AppearancePrefs }