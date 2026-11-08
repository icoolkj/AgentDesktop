import { createHash } from 'node:crypto'
import { basename, extname } from 'node:path'

/** 8-char hex line hash — matches Cursor ai_code_hashes.hash format. */
export function hashAiCodeLine(line: string): string {
  return createHash('md5').update(line, 'utf8').digest('hex').slice(0, 8)
}

export function fileExtensionFromPath(filePath: string): string {
  const ext = extname(filePath)
  return ext.startsWith('.') ? ext.slice(1).toLowerCase() : ext.toLowerCase()
}

export function fileNameForTracking(absolutePath: string, gitPath: string | null): string {
  if (gitPath) return gitPath.startsWith('/') ? gitPath : `/${gitPath}`
  const normalized = absolutePath.replace(/\\/g, '/')
  return normalized.startsWith('/') ? normalized : `/${normalized}`
}

export function splitLines(content: string): string[] {
  return content.replace(/\r\n/g, '\n').split('\n')
}

export function collectLineHashes(content: string): string[] {
  const hashes = new Set<string>()
  for (const line of splitLines(content)) {
    if (line.trim().length === 0) continue
    hashes.add(hashAiCodeLine(line))
  }
  return [...hashes]
}

export function basenameOnly(filePath: string): string {
  return basename(filePath.replace(/\\/g, '/'))
}
