import { readdir } from 'node:fs/promises'
import { join } from 'node:path'

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

function shouldSkipEntry(name: string, isDirectory: boolean): boolean {
  if (name.startsWith('.')) return true
  if (isDirectory && IGNORED_DIR_NAMES.has(name.toLowerCase())) return true
  return false
}

/** True when the directory has at least one visible file or non-empty subdirectory. */
export async function directoryHasVisibleChildren(dirPath: string): Promise<boolean> {
  let entries
  try {
    entries = await readdir(dirPath, { withFileTypes: true })
  } catch {
    return false
  }

  for (const entry of entries) {
    if (shouldSkipEntry(entry.name, entry.isDirectory())) continue
    if (entry.isFile()) return true
    if (entry.isDirectory()) {
      if (await directoryHasVisibleChildren(join(dirPath, entry.name))) return true
    }
  }

  return false
}

export function shouldSkipExplorerEntry(name: string, isDirectory: boolean): boolean {
  return shouldSkipEntry(name, isDirectory)
}
