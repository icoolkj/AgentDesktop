const EXT_TO_LANGUAGE: Record<string, string> = {
  ts: 'typescript',
  tsx: 'typescript',
  js: 'javascript',
  jsx: 'javascript',
  mjs: 'javascript',
  cjs: 'javascript',
  json: 'json',
  vue: 'html',
  md: 'markdown',
  markdown: 'markdown',
  java: 'java',
  xml: 'xml',
  html: 'html',
  htm: 'html',
  css: 'css',
  scss: 'scss',
  less: 'less',
  yml: 'yaml',
  yaml: 'yaml',
  sql: 'sql',
  py: 'python',
  go: 'go',
  rs: 'rust',
  sh: 'shell',
  ps1: 'powershell',
  properties: 'plaintext',
  env: 'plaintext',
}

export function fileNameFromPath(filePath: string): string {
  const parts = filePath.split(/[/\\]/)
  return parts[parts.length - 1] ?? filePath
}

export function languageFromPath(filePath: string): string {
  const dot = filePath.lastIndexOf('.')
  if (dot === -1) return 'plaintext'
  const ext = filePath.slice(dot + 1).toLowerCase()
  return EXT_TO_LANGUAGE[ext] ?? 'plaintext'
}
