export type TabViewKind = 'text' | 'image' | 'binary' | 'large-prompt' | 'git-diff'

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
  'pdf',
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

export function extensionFromPath(filePath: string): string {
  const dot = filePath.lastIndexOf('.')
  if (dot === -1) return ''
  return filePath.slice(dot + 1).toLowerCase()
}

export function viewKindFromPath(filePath: string): TabViewKind {
  const ext = extensionFromPath(filePath)
  if (IMAGE_EXTENSIONS.has(ext)) return 'image'
  if (BINARY_EXTENSIONS.has(ext)) return 'binary'
  return 'text'
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
