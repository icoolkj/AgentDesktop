export const TEXT_SOFT_BYTES = 1 * 1024 * 1024
export const TEXT_CONFIRM_BYTES = 50 * 1024 * 1024
export const TEXT_MAX_BYTES = 100 * 1024 * 1024
export const IMAGE_MAX_BYTES = 10 * 1024 * 1024
/** Claude Code PDF_TARGET_RAW_SIZE — raw bytes before base64. */
export const PDF_MAX_BYTES = 20 * 1024 * 1024

export type TextLoadPolicy = 'normal' | 'large' | 'confirm-required' | 'blocked'

export function textLoadPolicy(size: number): TextLoadPolicy {
  if (size > TEXT_MAX_BYTES) return 'blocked'
  if (size > TEXT_CONFIRM_BYTES) return 'confirm-required'
  if (size > TEXT_SOFT_BYTES) return 'large'
  return 'normal'
}

export function formatLimit(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${bytes / (1024 * 1024)}MB`
  if (bytes >= 1024) return `${bytes / 1024}KB`
  return `${bytes}B`
}

export class FileSizeError extends Error {
  constructor(
    message: string,
    readonly code: 'TEXT_TOO_LARGE' | 'IMAGE_TOO_LARGE' | 'PDF_TOO_LARGE',
    readonly size: number,
    readonly limit: number,
  ) {
    super(message)
    this.name = 'FileSizeError'
  }
}
