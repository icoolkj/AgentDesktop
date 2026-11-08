const ERROR_MESSAGES: Record<string, string> = {
  'File already exists': '文件已存在',
  'Target already exists': '目标已存在',
  '文件已存在': '文件已存在',
  '目标已存在': '目标已存在',
  'Invalid file path': '无效的文件路径',
  'Invalid directory path': '无效的文件夹路径',
  'Invalid path': '无效的路径',
  'Not a file': '不是文件',
}

function extractNestedMessage(message: string): string {
  const match = message.match(/Error:\s*([^:]+)$/)
  return match?.[1]?.trim() ?? message
}

export function formatWorkspaceError(err: unknown): string {
  if (!(err instanceof Error)) return '操作失败'

  const raw = err.message
  const nested = extractNestedMessage(raw)

  for (const [en, zh] of Object.entries(ERROR_MESSAGES)) {
    if (raw.includes(en) || nested.includes(en)) return zh
  }

  if (/[\u4e00-\u9fff]/.test(nested)) return nested
  return nested || '操作失败'
}
