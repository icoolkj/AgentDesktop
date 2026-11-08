/** Normalize Mermaid SVG for crisp zoom / scroll (remove width caps). */
export function normalizeMermaidSvg(svg: string): string {
  return svg
    .replace(/\sstyle="([^"]*)"/gi, (_match, style: string) => {
      const cleaned = style
        .replace(/max-width\s*:\s*100%\s*;?/gi, '')
        .replace(/width\s*:\s*100%\s*;?/gi, '')
        .trim()
      return cleaned ? ` style="${cleaned}"` : ''
    })
    .replace(/\swidth="100%"/gi, '')
}
