import type { ColorThemeTokens } from './types'

type Rgb = { r: number; g: number; b: number }

function parseColor(input: string): Rgb | null {
  const value = input.trim()
  if (value.startsWith('#')) {
    const hex = value.slice(1)
    if (hex.length === 3) {
      return {
        r: parseInt(hex[0]! + hex[0], 16),
        g: parseInt(hex[1]! + hex[1], 16),
        b: parseInt(hex[2]! + hex[2], 16),
      }
    }
    if (hex.length === 6) {
      return {
        r: parseInt(hex.slice(0, 2), 16),
        g: parseInt(hex.slice(2, 4), 16),
        b: parseInt(hex.slice(4, 6), 16),
      }
    }
  }
  const rgba = /^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)/.exec(value)
  if (rgba) {
    return { r: +rgba[1]!, g: +rgba[2]!, b: +rgba[3]! }
  }
  return null
}

function toHex({ r, g, b }: Rgb): string {
  const channel = (n: number) =>
    Math.round(Math.max(0, Math.min(255, n)))
      .toString(16)
      .padStart(2, '0')
  return `#${channel(r)}${channel(g)}${channel(b)}`
}

export function mixColor(a: string, b: string, weightB: number): string {
  const ca = parseColor(a)
  const cb = parseColor(b)
  if (!ca || !cb) return a
  const w = Math.max(0, Math.min(1, weightB))
  return toHex({
    r: ca.r * (1 - w) + cb.r * w,
    g: ca.g * (1 - w) + cb.g * w,
    b: ca.b * (1 - w) + cb.b * w,
  })
}

export function rgbaColor(color: string, alpha: number): string {
  const c = parseColor(color)
  if (!c) return color
  return `rgba(${c.r}, ${c.g}, ${c.b}, ${alpha})`
}

/** Derive secondary UI tokens from primary palette so tinted themes stay cohesive. */
export function harmonizeDarkTokens(tokens: ColorThemeTokens): ColorThemeTokens {
  const editor = tokens['--ide-editor-bg']
  const sidebar = tokens['--ide-sidebar-bg']
  const border = tokens['--ide-border']
  const accent = tokens['--ide-accent']
  const fg = tokens['--ide-fg']
  const muted = tokens['--ide-muted']
  const black = '#000000'
  const white = '#ffffff'

  return {
    ...tokens,
    color: tokens.color ?? fg,
    background: tokens.background ?? editor,
    '--ide-titlebar-bg': mixColor(sidebar, editor, 0.35),
    '--ide-activitybar-bg': mixColor(sidebar, black, 0.1),
    '--ide-menubar-bg': mixColor(sidebar, editor, 0.4),
    '--ide-tab-bar-bg': mixColor(sidebar, editor, 0.55),
    '--ide-input-bg': mixColor(editor, white, 0.07),
    '--ide-dropdown-bg': sidebar,
    '--ide-surface-bg': mixColor(editor, sidebar, 0.52),
    '--ide-bubble-user-bg': mixColor(accent, editor, 0.42),
    '--ide-bubble-agent-bg': mixColor(sidebar, editor, 0.68),
    '--ide-bubble-border': border,
    '--ide-code-bg': mixColor(editor, black, 0.22),
    '--ide-button-bg': mixColor(sidebar, editor, 0.58),
    '--ide-button-border': border,
    '--ide-button-primary-bg': accent,
    '--ide-button-primary-fg': tokens['--ide-on-accent'],
    '--ide-list-active-bg': mixColor(accent, editor, 0.58),
    '--ide-list-active-fg': tokens['--ide-on-accent'],
    '--ide-welcome-fg': muted,
    '--ide-welcome-title': mixColor(fg, muted, 0.35),
    '--ide-tree-fg': tokens['--ide-tree-fg'] ?? fg,
    '--ide-scrollbar-thumb': rgbaColor(muted, 0.45),
    '--ide-hover-overlay': rgbaColor(white, 0.06),
    '--ide-shadow': `0 8px 24px ${rgbaColor(black, 0.42)}`,
    '--ide-activitybar-icon-idle': muted,
    '--ide-activitybar-icon-active': fg,
    '--ide-terminal-bg': mixColor(editor, tokens['--ide-code-bg'], 0.42),
    '--ide-terminal-fg': fg,
    '--ide-terminal-cursor': fg,
    '--ide-terminal-selection': mixColor(accent, editor, 0.55),
  }
}

export function harmonizeLightTokens(tokens: ColorThemeTokens): ColorThemeTokens {
  const editor = tokens['--ide-editor-bg']
  const sidebar = tokens['--ide-sidebar-bg']
  const border = tokens['--ide-border']
  const accent = tokens['--ide-accent']
  const fg = tokens['--ide-fg']
  const muted = tokens['--ide-muted']
  const black = '#000000'
  const white = '#ffffff'

  return {
    ...tokens,
    color: tokens.color ?? fg,
    background: tokens.background ?? editor,
    '--ide-titlebar-bg': mixColor(sidebar, white, 0.35),
    '--ide-activitybar-bg': mixColor(sidebar, white, 0.2),
    '--ide-menubar-bg': mixColor(sidebar, white, 0.25),
    '--ide-tab-bar-bg': mixColor(sidebar, editor, 0.45),
    '--ide-input-bg': white,
    '--ide-dropdown-bg': white,
    '--ide-surface-bg': mixColor(editor, sidebar, 0.4),
    '--ide-bubble-user-bg': mixColor(accent, white, 0.82),
    '--ide-bubble-agent-bg': mixColor(sidebar, white, 0.55),
    '--ide-bubble-border': border,
    '--ide-code-bg': mixColor(sidebar, editor, 0.35),
    '--ide-button-bg': mixColor(sidebar, white, 0.5),
    '--ide-button-border': border,
    '--ide-button-primary-bg': accent,
    '--ide-button-primary-fg': tokens['--ide-on-accent'],
    '--ide-list-active-bg': accent,
    '--ide-list-active-fg': tokens['--ide-on-accent'],
    '--ide-welcome-fg': muted,
    '--ide-welcome-title': mixColor(fg, black, 0.15),
    '--ide-tree-fg': tokens['--ide-tree-fg'] ?? fg,
    '--ide-scrollbar-thumb': rgbaColor(black, 0.2),
    '--ide-hover-overlay': rgbaColor(black, 0.06),
    '--ide-shadow': `0 8px 24px ${rgbaColor(black, 0.12)}`,
    '--ide-activitybar-icon-idle': muted,
    '--ide-activitybar-icon-active': accent,
    '--ide-terminal-bg': mixColor(editor, tokens['--ide-code-bg'], 0.38),
    '--ide-terminal-fg': fg,
    '--ide-terminal-cursor': fg,
    '--ide-terminal-selection': mixColor(accent, white, 0.72),
  }
}
