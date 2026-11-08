/** Fast local checks — avoids IPC for common build/IDE output dirs. */
const IGNORED_DIR_NAMES = new Set([
  'target',
  'node_modules',
  'dist',
  'build',
  'out',
  '.git',
  '.idea',
  '.vscode',
  '__pycache__',
  '.gradle',
  'coverage',
  '.next',
  '.nuxt',
  '.turbo',
  '.cache',
  'vendor',
])

export function isLikelyIgnoredPath(absolutePath: string): boolean {
  const parts = absolutePath.replace(/\\/g, '/').toLowerCase().split('/')
  return parts.some(part => IGNORED_DIR_NAMES.has(part))
}
