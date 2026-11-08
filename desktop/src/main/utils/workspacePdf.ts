import { readFile, stat } from 'node:fs/promises'
import { spawn } from 'node:child_process'

import {
  PDF_AT_MENTION_INLINE_THRESHOLD,
  PDF_TARGET_RAW_SIZE,
  shouldUsePdfReference,
} from '@agent-desktop/shared-types'

export interface PdfInfoDto {
  size: number
  pageCount: number | null
  effectivePageCount: number
  valid: boolean
  error?: string
}

async function pageCountViaPdfInfo(filePath: string): Promise<number | null> {
  return new Promise(resolve => {
    const child = spawn('pdfinfo', [filePath], { windowsHide: true })
    let out = ''
    child.stdout.on('data', chunk => {
      out += String(chunk)
    })
    child.on('close', code => {
      if (code !== 0) {
        resolve(null)
        return
      }
      const match = /^Pages:\s+(\d+)/m.exec(out)
      resolve(match ? Number.parseInt(match[1]!, 10) : null)
    })
    child.on('error', () => resolve(null))
  })
}

async function validatePdfHeader(filePath: string): Promise<string | null> {
  const buf = await readFile(filePath, { encoding: null, flag: 'r' })
  if (buf.length < 5) return 'PDF file is empty'
  const magic = buf.subarray(0, 5).toString('ascii')
  if (!magic.startsWith('%PDF-')) return 'File is not a valid PDF (missing %PDF- header)'
  return null
}

/** CC tryGetPDFReference + readPDF header checks. */
export async function inspectPdfFile(filePath: string): Promise<PdfInfoDto> {
  try {
    const info = await stat(filePath)
    if (!info.isFile()) {
      return { size: 0, pageCount: null, effectivePageCount: 0, valid: false, error: 'Not a file' }
    }
    if (info.size === 0) {
      return { size: 0, pageCount: null, effectivePageCount: 0, valid: false, error: 'PDF file is empty' }
    }
    if (info.size > PDF_TARGET_RAW_SIZE) {
      return {
        size: info.size,
        pageCount: null,
        effectivePageCount: Math.ceil(info.size / (100 * 1024)),
        valid: false,
        error: `PDF exceeds ${PDF_TARGET_RAW_SIZE / (1024 * 1024)}MB limit`,
      }
    }
    const headerError = await validatePdfHeader(filePath)
    if (headerError) {
      return { size: info.size, pageCount: null, effectivePageCount: 0, valid: false, error: headerError }
    }
    const pageCount = await pageCountViaPdfInfo(filePath)
    const effectivePageCount = pageCount ?? Math.ceil(info.size / (100 * 1024))
    return { size: info.size, pageCount, effectivePageCount, valid: true }
  } catch (e) {
    return {
      size: 0,
      pageCount: null,
      effectivePageCount: 0,
      valid: false,
      error: e instanceof Error ? e.message : String(e),
    }
  }
}

export { shouldUsePdfReference, PDF_AT_MENTION_INLINE_THRESHOLD }
