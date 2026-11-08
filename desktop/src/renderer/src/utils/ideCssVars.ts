/** Read a CSS custom property from :root (for Monaco overview ruler, Canvas, etc.). */
export function cssVar(name: string, fallback = ''): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}
