import type { ITheme } from '@xterm/xterm'

import type { ColorThemeCategory, ColorThemeTokens } from '@renderer/modules/appearance/types'
import { mixColor } from '@renderer/modules/appearance/themeHarmonize'

/** Map design tokens → xterm.js palette (follows active color theme). */
export function buildXtermTheme(
  tokens: ColorThemeTokens,
  category: ColorThemeCategory,
): ITheme {
  const background = tokens['--ide-terminal-bg']
  const foreground = tokens['--ide-terminal-fg']
  const accent = tokens['--ide-accent']
  const blackBase = category === 'dark' ? '#000000' : '#808080'
  const whiteBase = category === 'dark' ? '#ffffff' : foreground

  return {
    background,
    foreground,
    cursor: tokens['--ide-terminal-cursor'],
    cursorAccent: background,
    selectionBackground: tokens['--ide-terminal-selection'],
    selectionForeground: tokens['--ide-list-active-fg'],
    black: mixColor(background, blackBase, category === 'dark' ? 0.18 : 0.12),
    red: tokens['--ide-error'],
    green: tokens['--ide-success'],
    yellow: tokens['--ide-warning'],
    blue: accent,
    magenta: mixColor(tokens['--ide-error'], accent, 0.45),
    cyan: mixColor(accent, tokens['--ide-success'], 0.4),
    white: foreground,
    brightBlack: mixColor(foreground, background, 0.55),
    brightRed: mixColor(tokens['--ide-error'], whiteBase, category === 'dark' ? 0.2 : 0.15),
    brightGreen: mixColor(tokens['--ide-success'], whiteBase, category === 'dark' ? 0.22 : 0.18),
    brightYellow: mixColor(tokens['--ide-warning'], whiteBase, category === 'dark' ? 0.2 : 0.15),
    brightBlue: mixColor(accent, whiteBase, category === 'dark' ? 0.25 : 0.2),
    brightMagenta: mixColor(tokens['--ide-error'], accent, 0.3),
    brightCyan: mixColor(accent, tokens['--ide-success'], 0.25),
    brightWhite: mixColor(foreground, whiteBase, category === 'dark' ? 0.15 : 0.08),
  }
}
