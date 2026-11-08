import { spawn, spawnSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { join } from 'node:path'
import { runFallbackGrep } from './grepFallback'
import type { GrepMatchDto, GrepRequestDto, GrepResultDto } from './grepTypes'

export type { GrepMatchDto, GrepRequestDto, GrepResultDto } from './grepTypes'

const DEFAULT_HEAD_LIMIT = 250
const RG_TIMEOUT_MS = 20_000

function resolveRgCommand(): string | null {
  const pathEnv = process.env.PATH ?? ''
  const sep = process.platform === 'win32' ? ';' : ':'
  const executable = process.platform === 'win32' ? 'rg.exe' : 'rg'

  for (const dir of pathEnv.split(sep)) {
    if (!dir || dir.includes('://')) continue
    const candidate = join(dir.trim(), executable)
    if (existsSync(candidate)) return candidate
  }

  if (process.platform === 'win32') {
    const localAppData = process.env.LOCALAPPDATA
    if (localAppData) {
      const candidate = join(localAppData, 'Programs', 'ripgrep', 'rg.exe')
      if (existsSync(candidate)) return candidate
    }

    try {
      const where = spawnSync('where.exe', ['rg'], { encoding: 'utf8', windowsHide: true })
      if (where.status === 0 && where.stdout.trim()) {
        const first = where.stdout.trim().split(/\r?\n/)[0]?.trim()
        if (first && existsSync(first)) return first
      }
    } catch {
      // ignore
    }
  }

  return null
}

function parseMatchLine(rootPath: string, line: string): GrepMatchDto | null {
  const match = line.match(/^(.+?):(\d+):(.*)$/)
  if (!match) return null

  const filePart = match[1]!
  const lineNum = Number.parseInt(match[2]!, 10)
  const text = match[3]!
  if (!Number.isFinite(lineNum)) return null

  const file = filePart.replace(/\\/g, '/').includes(':')
    ? filePart.replace(/\\/g, '/')
    : join(rootPath, filePart).replace(/\\/g, '/')

  return {
    file,
    line: lineNum,
    column: 1,
    text,
  }
}

export async function runWorkspaceGrep(request: GrepRequestDto): Promise<GrepResultDto> {
  const { rootPath, pattern, caseInsensitive, wholeWord, glob } = request
  const headLimit = request.headLimit ?? DEFAULT_HEAD_LIMIT

  if (!pattern.trim()) {
    return { matches: [], truncated: false, error: '请输入搜索内容' }
  }

  const rg = resolveRgCommand()
  if (!rg) {
    return runFallbackGrep(request)
  }

  const args = [
    '--hidden',
    '--max-columns',
    '500',
    '--glob',
    '!.git',
    '--glob',
    '!node_modules',
    '--glob',
    '!target',
    '--glob',
    '!.idea',
  ]

  if (caseInsensitive) args.push('-i')
  if (wholeWord) args.push('-w')
  args.push('-n', '--no-heading')

  if (pattern.startsWith('-')) {
    args.push('-e', pattern)
  } else {
    args.push(pattern)
  }

  if (glob?.trim()) {
    for (const part of glob.split(/[,\s]+/)) {
      if (part.trim()) {
        args.push('--glob', part.trim())
      }
    }
  }

  args.push('.')

  return new Promise(resolve => {
    const child = spawn(rg, args, { cwd: rootPath, windowsHide: true })
    const stdout: string[] = []
    let stderr = ''
    let settled = false

    const timer = setTimeout(() => {
      if (settled) return
      settled = true
      child.kill('SIGKILL')
      resolve({ matches: [], truncated: false, error: '搜索超时，请缩小范围' })
    }, RG_TIMEOUT_MS)

    child.stdout.on('data', (chunk: Buffer) => {
      stdout.push(chunk.toString('utf8'))
    })

    child.stderr.on('data', (chunk: Buffer) => {
      stderr += chunk.toString('utf8')
    })

    child.on('error', err => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      resolve({ matches: [], truncated: false, error: err.message })
    })

    child.on('close', code => {
      if (settled) return
      settled = true
      clearTimeout(timer)

      if (code !== 0 && code !== 1) {
        resolve({
          matches: [],
          truncated: false,
          error: stderr.trim() || `ripgrep 退出码 ${code}`,
        })
        return
      }

      const combined = stdout.join('')
      const lines = combined.split(/\r?\n/).filter(Boolean)
      const matches: GrepMatchDto[] = []
      let truncated = false

      for (const line of lines) {
        if (matches.length >= headLimit) {
          truncated = true
          break
        }
        const parsed = parseMatchLine(rootPath, line)
        if (parsed) matches.push(parsed)
      }

      resolve({ matches, truncated, engine: 'ripgrep' })
    })
  })
}
