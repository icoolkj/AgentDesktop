/** Canonical file paths for editor tabs and Monaco bridge (Windows → backslashes). */

export function normalizeEditorPath(path: string): string {
  const trimmed = path.trim()
  if (!trimmed) return trimmed
  if (typeof window !== 'undefined' && window.desktopEnv?.platform === 'win32') {
    return trimmed.replace(/\//g, '\\')
  }
  return trimmed.replace(/\\/g, '/')
}

export function editorPathKey(path: string): string {
  return normalizeEditorPath(path).toLowerCase()
}

export function editorPathsEqual(a: string, b: string): boolean {
  return editorPathKey(a) === editorPathKey(b)
}
