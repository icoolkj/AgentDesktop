/**
 * Sync tail read for reAppendSessionMetadata (Claude sessionStorage.ts).
 */
import { closeSync, fstatSync, openSync, readSync } from 'node:fs'
import { LITE_READ_BUF_SIZE } from './claudeSessionPortable'

export function readFileTailSync(filePath: string): string {
  let fd: number | undefined
  try {
    fd = openSync(filePath, 'r')
    const { size } = fstatSync(fd)
    if (size === 0) return ''
    const readLen = Math.min(size, LITE_READ_BUF_SIZE)
    const buf = Buffer.allocUnsafe(readLen)
    readSync(fd, buf, 0, readLen, size - readLen)
    return buf.toString('utf8', 0, readLen)
  } catch {
    return ''
  } finally {
    if (fd !== undefined) closeSync(fd)
  }
}
