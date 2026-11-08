import { extname } from 'node:path'

const IMAGE_EXTENSIONS = new Set(['png', 'jpg', 'jpeg', 'gif', 'webp', 'ico', 'bmp', 'avif'])

const BINARY_EXTENSIONS = new Set([
  'xlsx',
  'xls',
  'xlsm',
  'xlsb',
  'docx',
  'doc',
  'pptx',
  'ppt',
  'zip',
  'tar',
  'gz',
  'tgz',
  '7z',
  'rar',
  'exe',
  'dll',
  'so',
  'dylib',
  'bin',
  'class',
  'jar',
  'war',
  'wasm',
  'mp3',
  'mp4',
  'mov',
  'avi',
  'mkv',
  'woff',
  'woff2',
  'ttf',
  'otf',
  'eot',
  'db',
  'sqlite',
  'pdb',
])

const EXT_TO_MIME: Record<string, string> = {
  png: 'image/png',
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  gif: 'image/gif',
  webp: 'image/webp',
  ico: 'image/x-icon',
  bmp: 'image/bmp',
  avif: 'image/avif',
  pdf: 'application/pdf',
  xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  xls: 'application/vnd.ms-excel',
  docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  zip: 'application/zip',
}

export type FileViewKind = 'text' | 'image' | 'pdf' | 'binary'

export function extensionFromPath(filePath: string): string {
  return extname(filePath).slice(1).toLowerCase()
}

export function viewKindFromExtension(ext: string): FileViewKind {
  if (IMAGE_EXTENSIONS.has(ext)) return 'image'
  if (ext === 'pdf') return 'pdf'
  if (BINARY_EXTENSIONS.has(ext)) return 'binary'
  return 'text'
}

export function viewKindFromPath(filePath: string): FileViewKind {
  return viewKindFromExtension(extensionFromPath(filePath))
}

export function mimeTypeFromPath(filePath: string): string {
  const ext = extensionFromPath(filePath)
  return EXT_TO_MIME[ext] ?? 'application/octet-stream'
}
