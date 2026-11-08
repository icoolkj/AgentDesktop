/**
 * Limits aligned with Claude Code `src/constants/apiLimits.ts` and Read tool defaults.
 * @see claude_code_src-master/src/constants/apiLimits.ts
 * @see claude_code_src-master/src/tools/FileReadTool/limits.ts
 */

/** API rejects when base64 string length exceeds this (not raw bytes). */
export const API_IMAGE_MAX_BASE64_SIZE = 5 * 1024 * 1024

/** Target raw image size before base64 encoding (~3.75 MB). */
export const IMAGE_TARGET_RAW_SIZE = (API_IMAGE_MAX_BASE64_SIZE * 3) / 4

export const IMAGE_MAX_WIDTH = 2000
export const IMAGE_MAX_HEIGHT = 2000

/** @deprecated use API_IMAGE_MAX_BASE64_SIZE for validation after encoding */
export const CHAT_IMAGE_MAX_BYTES = API_IMAGE_MAX_BASE64_SIZE

export const CHAT_IMAGE_MAX_DIMENSION = IMAGE_MAX_WIDTH

/** Max raw PDF size before base64 (~27 MB encoded + context headroom). */
export const PDF_TARGET_RAW_SIZE = 20 * 1024 * 1024

/** CC apiLimits — max pages accepted by API. */
export const API_PDF_MAX_PAGES = 100

/** CC — PDFs larger than this use page-extraction path in read_file. */
export const PDF_EXTRACT_SIZE_THRESHOLD = 3 * 1024 * 1024

/** CC — max raw size for pdftoppm extraction path. */
export const PDF_MAX_EXTRACT_SIZE = 100 * 1024 * 1024

/** CC — max pages per read_file pages= request. */
export const PDF_MAX_PAGES_PER_READ = 20

/** CC — @mention/composer inline threshold (pages). */
export const PDF_AT_MENTION_INLINE_THRESHOLD = 10

/** Max media items (images + PDFs) per API request. */
export const API_MAX_MEDIA_PER_REQUEST = 100

/** Text file inline attach cap — Claude Code Read/@mention default (256 KB). */
export const TEXT_ATTACHMENT_MAX_BYTES = 256 * 1024

export const CHAT_IMAGE_MIME_TYPES = [
  'image/png',
  'image/jpeg',
  'image/gif',
  'image/webp',
] as const

/** Claude Code `IMAGE_EXTENSION_REGEX` — png|jpe?g|gif|webp only (bmp converted at paste). */
export const IMAGE_EXTENSION_REGEX = /\.(png|jpe?g|gif|webp)$/i

export type ChatImageMimeType = (typeof CHAT_IMAGE_MIME_TYPES)[number]

export function isChatImageMimeType(mime: string): mime is ChatImageMimeType {
  return (CHAT_IMAGE_MIME_TYPES as readonly string[]).includes(mime)
}

export function isImageFilePath(filePath: string): boolean {
  return IMAGE_EXTENSION_REGEX.test(filePath)
}

export function isPdfFilePath(filePath: string): boolean {
  return /\.pdf$/i.test(filePath)
}

/**
 * Extensions Claude Code treats as binary for text reads (includes Office; PDF handled separately).
 * @see claude_code_src-master/src/constants/files.ts BINARY_EXTENSIONS
 */
export const BINARY_ATTACHMENT_EXTENSIONS = new Set([
  '.doc',
  '.docx',
  '.xls',
  '.xlsx',
  '.ppt',
  '.pptx',
  '.odt',
  '.ods',
  '.odp',
  '.zip',
  '.tar',
  '.gz',
  '.7z',
  '.rar',
  '.exe',
  '.dll',
  '.mp3',
  '.mp4',
  '.mov',
])

export function isBinaryAttachmentPath(filePath: string): boolean {
  const dot = filePath.lastIndexOf('.')
  if (dot < 0) return false
  return BINARY_ATTACHMENT_EXTENSIONS.has(filePath.slice(dot).toLowerCase())
}

export function shouldUsePdfReference(effectivePageCount: number): boolean {
  return effectivePageCount > PDF_AT_MENTION_INLINE_THRESHOLD
}

export type ComposerAttachmentKind = 'image' | 'pdf' | 'pdf_reference' | 'text' | 'unsupported'

export function classifyComposerAttachmentPath(filePath: string): ComposerAttachmentKind {
  if (isImageFilePath(filePath)) return 'image'
  if (isPdfFilePath(filePath)) return 'pdf'
  if (isBinaryAttachmentPath(filePath)) return 'unsupported'
  return 'text'
}

export function unsupportedAttachmentMessage(filePath: string): string {
  const name = filePath.replace(/^.*[/\\]/, '')
  return (
    `「${name}」为 Claude Code 同样不支持的二进制附件（Office/压缩包等）。` +
    ' 请转为 PDF/文本，或使用 @路径 让 Agent 通过 read_file 分段读取文本文件。'
  )
}
