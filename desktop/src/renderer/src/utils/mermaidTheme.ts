import type { ColorThemeDefinition } from '@renderer/modules/appearance/types'

export interface MermaidThemeOptions {
  /** Inline preview fits container; lightbox uses native diagram size. */
  useMaxWidth?: boolean
}

/** Map IDE tokens → Mermaid `base` themeVariables for chat diagrams. */
export function buildMermaidThemeConfig(
  theme: ColorThemeDefinition,
  options: MermaidThemeOptions = {},
) {
  const useMaxWidth = options.useMaxWidth ?? true
  const t = theme.tokens
  const isDark = theme.category === 'dark'

  return {
    startOnLoad: false,
    theme: 'base' as const,
    fontSize: 15,
    themeVariables: {
      darkMode: isDark,
      fontSize: '15px',
      background: t['--ide-code-bg'],
      mainBkg: t['--ide-code-bg'],
      secondBkg: t['--ide-surface-bg'],
      tertiaryColor: t['--ide-surface-bg'],
      primaryColor: t['--ide-accent'],
      primaryTextColor: t['--ide-on-accent'],
      primaryBorderColor: t['--ide-border'],
      secondaryColor: t['--ide-surface-bg'],
      secondaryTextColor: t['--ide-fg'],
      secondaryBorderColor: t['--ide-border'],
      tertiaryTextColor: t['--ide-fg'],
      lineColor: t['--ide-muted'],
      textColor: t['--ide-fg'],
      nodeBorder: t['--ide-border'],
      clusterBkg: t['--ide-code-bg'],
      clusterBorder: t['--ide-border'],
      titleColor: t['--ide-fg'],
      edgeLabelBackground: t['--ide-code-bg'],
      nodeTextColor: t['--ide-fg'],
    },
    securityLevel: 'strict' as const,
    suppressErrorRendering: true,
    flowchart: {
      useMaxWidth,
      htmlLabels: true,
      diagramPadding: 12,
    },
    sequence: {
      useMaxWidth,
      wrap: true,
      width: 180,
      height: 52,
    },
    er: {
      useMaxWidth,
    },
    gantt: {
      useMaxWidth,
    },
  }
}
