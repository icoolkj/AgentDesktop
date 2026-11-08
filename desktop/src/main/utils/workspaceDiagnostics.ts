import { spawn } from 'node:child_process'
import { existsSync } from 'node:fs'
import { readdir } from 'node:fs/promises'
import { dirname, join, resolve } from 'node:path'

import {
  dedupeDiagnostics,
  parseEslintJsonDiagnostics,
  parseTscDiagnostics,
  type DiagnosticProblemDto,
} from './workspaceDiagnosticsParse'

export interface WorkspaceDiagnosticsResultDto {
  problems: DiagnosticProblemDto[]
  engines: string[]
  error?: string
  truncated?: boolean
}

const SCAN_TIMEOUT_MS = 120_000
const MAX_PROBLEMS = 2000

export async function runWorkspaceDiagnostics(rootPath: string): Promise<WorkspaceDiagnosticsResultDto> {
  const root = resolve(rootPath)
  if (!root || !existsSync(root)) {
    return { problems: [], engines: [], error: '未打开工作区' }
  }

  const engines: string[] = []
  const collected: DiagnosticProblemDto[] = []
  const errors: string[] = []

  const tsConfigs = await discoverTsConfigs(root)
  for (const configPath of tsConfigs) {
    const result = await runTscProject(root, configPath)
    if (result.engine) engines.push(result.engine)
    if (result.error) errors.push(result.error)
    collected.push(...result.problems)
  }

  const eslintConfig = discoverEslintConfig(root)
  if (eslintConfig) {
    const result = await runEslint(root)
    engines.push(result.engine)
    if (result.error) errors.push(result.error)
    collected.push(...result.problems)
  }

  if (engines.length === 0) {
    return {
      problems: [],
      engines: [],
      error: '未找到 tsconfig 或 ESLint 配置，无法扫描整个项目',
    }
  }

  let problems = dedupeDiagnostics(collected)
  let truncated = false
  if (problems.length > MAX_PROBLEMS) {
    truncated = true
    problems = problems.slice(0, MAX_PROBLEMS)
  }

  return {
    problems,
    engines,
    truncated,
    error: errors.length ? errors.join(' · ') : undefined,
  }
}

async function discoverTsConfigs(root: string): Promise<string[]> {
  const candidates = [
    join(root, 'tsconfig.json'),
    join(root, 'desktop', 'tsconfig.web.json'),
    join(root, 'desktop', 'tsconfig.json'),
    join(root, 'shared', 'tsconfig.json'),
  ]

  const found = new Set<string>()
  for (const path of candidates) {
    if (existsSync(path)) found.add(path)
  }

  try {
    const entries = await readdir(root, { withFileTypes: true })
    for (const entry of entries) {
      if (!entry.isDirectory() || entry.name.startsWith('.') || entry.name === 'node_modules') continue
      const nested = join(root, entry.name, 'tsconfig.json')
      if (existsSync(nested)) found.add(nested)
    }
  } catch {
    // ignore unreadable root
  }

  return [...found]
}

function discoverEslintConfig(root: string): string | null {
  const names = ['eslint.config.js', 'eslint.config.mjs', 'eslint.config.cjs', '.eslintrc.cjs', '.eslintrc.json']
  for (const name of names) {
    const path = join(root, name)
    if (existsSync(path)) return path
  }
  return null
}

async function runTscProject(
  root: string,
  configPath: string,
): Promise<{ problems: DiagnosticProblemDto[]; engine?: string; error?: string }> {
  const tsc = resolveLocalBin([dirname(configPath), root], 'tsc')
  const args = ['-p', configPath, '--noEmit', '--pretty', 'false']
  const label = `typescript (${configPath.replace(root, '.').replace(/^\\/, '')})`
  const { stdout, stderr, error, timedOut } = await spawnCommand(tsc, args, root)

  if (timedOut) {
    return { problems: [], engine: label, error: `${label} 扫描超时` }
  }
  if (error && !stdout && !stderr) {
    return { problems: [], engine: label, error: `${label}: ${error}` }
  }

  const problems = parseTscDiagnostics(`${stdout}\n${stderr}`, root)
  return { problems, engine: label }
}

async function runEslint(root: string): Promise<{ problems: DiagnosticProblemDto[]; engine: string; error?: string }> {
  const eslint = resolveLocalBin([root], 'eslint')
  const args = ['.', '--format', 'json', '--max-warnings', '99999']
  const label = 'eslint'
  const { stdout, stderr, error, timedOut } = await spawnCommand(eslint, args, root)

  if (timedOut) {
    return { problems: [], engine: label, error: 'eslint 扫描超时' }
  }

  const problems = parseEslintJsonDiagnostics(stdout, root)
  if (!problems.length && (error || stderr.trim())) {
    const detail = (stderr || error || '').trim()
    if (detail && !detail.includes('0 problems')) {
      return { problems: [], engine: label, error: detail.slice(0, 300) }
    }
  }

  return { problems, engine: label }
}

function resolveLocalBin(searchDirs: string[], name: string): string {
  const win = process.platform === 'win32'
  for (const dir of searchDirs) {
    const local = join(dir, 'node_modules', '.bin', win ? `${name}.cmd` : name)
    if (existsSync(local)) return local
  }
  return win ? 'npx.cmd' : 'npx'
}

interface SpawnResult {
  stdout: string
  stderr: string
  error?: string
  timedOut?: boolean
}

function spawnCommand(command: string, args: string[], cwd: string): Promise<SpawnResult> {
  return new Promise(resolvePromise => {
    const useNpx = command.endsWith('npx.cmd') || command === 'npx'
    const finalArgs = useNpx ? [(args[0] === '-p' ? 'tsc' : 'eslint'), ...args] : args
    const needsShell =
      process.platform === 'win32' && (useNpx || command.endsWith('.cmd') || command.endsWith('.bat'))
    const child = spawn(command, finalArgs, {
      cwd,
      windowsHide: true,
      shell: needsShell,
      env: process.env,
    })

    let stdout = ''
    let stderr = ''
    let settled = false

    const timer = setTimeout(() => {
      if (settled) return
      settled = true
      child.kill('SIGKILL')
      resolvePromise({ stdout, stderr, timedOut: true })
    }, SCAN_TIMEOUT_MS)

    child.stdout.on('data', (chunk: Buffer) => {
      stdout += chunk.toString('utf8')
    })
    child.stderr.on('data', (chunk: Buffer) => {
      stderr += chunk.toString('utf8')
    })
    child.on('error', err => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      resolvePromise({ stdout, stderr, error: err.message })
    })
    child.on('close', () => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      resolvePromise({ stdout, stderr })
    })
  })
}
