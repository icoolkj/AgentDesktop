import {
  PDF_AT_MENTION_INLINE_THRESHOLD,
  PDF_TARGET_RAW_SIZE,
  shouldUsePdfReference,
} from '@agent-desktop/shared-types'

function basename(path: string): string {
  const parts = path.replace(/\\/g, '/').split('/')
  return parts[parts.length - 1] || path
}

export interface PreparePdfOptions {
  /** @deprecated model routing is handled by CPA Payload rules; kept for call-site compat */
  model?: string | null
  /** @deprecated */
  baseUrl?: string | null
}

export async function preparePdfFromPath(
  absPath: string,
  _options: PreparePdfOptions = {},
): Promise<import('@agent-desktop/shared-types').ChatAttachment> {
  const info = await window.workspaceApi.inspectPdf(absPath)
  if (!info.valid) {
    throw new Error(info.error ?? 'Invalid PDF')
  }

  if (shouldUsePdfReference(info.effectivePageCount)) {
    return {
      kind: 'pdf_reference',
      path: absPath,
      label: basename(absPath),
      mimeType: 'application/pdf',
      pageCount: info.effectivePageCount,
      fileSize: info.size,
    }
  }

  const { base64, mimeType, size } = await window.workspaceApi.readFileBase64(absPath, {
    maxBytes: PDF_TARGET_RAW_SIZE,
  })
  const encodedLimit = Math.floor(PDF_TARGET_RAW_SIZE * 4 / 3)
  if (base64.length > encodedLimit) {
    throw new Error(
      `PDF base64 exceeds Claude Code encoded limit (~${Math.round(encodedLimit / 1024 / 1024)}MB)`,
    )
  }
  if (size > PDF_TARGET_RAW_SIZE) {
    throw new Error(`PDF exceeds ${PDF_TARGET_RAW_SIZE / (1024 * 1024)}MB raw limit`)
  }
  return {
    kind: 'pdf',
    path: absPath,
    label: basename(absPath),
    mimeType: mimeType || 'application/pdf',
    base64,
  }
}

export { PDF_AT_MENTION_INLINE_THRESHOLD, PDF_TARGET_RAW_SIZE, shouldUsePdfReference }
