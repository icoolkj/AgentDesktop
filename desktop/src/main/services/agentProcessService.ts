import { spawn, spawnSync, type ChildProcess } from 'node:child_process'
import { existsSync, openSync, readSync, closeSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { app } from 'electron'
import type { AgentConnectionInfo, AgentReadyMessage } from '@agent-desktop/shared-types'

let agentProcess: ChildProcess | null = null
let agentConnection: AgentConnectionInfo | null = null
/** true = 后端由 IDEA / 外部进程启动，Electron 不应 kill */
let externalAgent = false

function repoRootCandidates(): string[] {
  const roots: string[] = []
  const add = (root?: string) => {
    if (root && !roots.includes(root)) roots.push(root)
  }

  add(process.env['AGENT_REPO_ROOT'])

  let dir = __dirname
  for (let i = 0; i < 6; i++) {
    add(dir)
    const parent = join(dir, '..')
    if (parent === dir) break
    dir = parent
  }

  add(join(process.cwd(), '..'))
  add(process.cwd())
  add(app.getAppPath())

  return roots
}

/** IDEA 调试时设置 AGENT_WS_URL=ws://127.0.0.1:5566/ws，Electron 不再 spawn Java */
function getExternalAgentConnection(): AgentConnectionInfo | null {
  const wsUrl = process.env['AGENT_WS_URL']?.trim()
  if (!wsUrl) return null

  const portMatch = wsUrl.match(/:(\d+)\//) ?? wsUrl.match(/:(\d+)$/)
  const port = portMatch ? Number.parseInt(portMatch[1]!, 10) : 8080

  console.log('[agent] using external backend (AGENT_WS_URL):', wsUrl)
  externalAgent = true
  return { port, wsUrl }
}

function assertRunnableSpringBootJar(jarPath: string): void {
  const size = statSync(jarPath).size
  if (size < 5_000_000) {
    throw new Error(
      [
        `Java agent-server JAR looks incomplete (${Math.round(size / 1024)} KB).`,
        'Run:  pnpm build:java',
        'If build fails with "file in use", stop AgentServerApplication in IDEA first.',
      ].join('\n'),
    )
  }

  const scanBytes = Math.min(size, 512 * 1024)
  const fd = openSync(jarPath, 'r')
  try {
    const buffer = Buffer.alloc(scanBytes)
    const bytes = readSync(fd, buffer, 0, scanBytes, 0)
    const sample = buffer.subarray(0, bytes).toString('latin1')
    const looksLikeSpringBoot =
      sample.includes('JarLauncher') ||
      sample.includes('Start-Class:') ||
      sample.includes('BOOT-INF/')
    if (!looksLikeSpringBoot) {
      throw new Error(
        [
          'Java agent-server JAR is not a Spring Boot executable (run pnpm build:java).',
          'If IDEA is running the backend, set AGENT_WS_URL=ws://127.0.0.1:PORT/ws',
        ].join('\n'),
      )
    }
  } finally {
    closeSync(fd)
  }
}

function resolveJavaAgentJar(): string {
  const customJar = process.env['AGENT_SERVER_JAR']?.trim()
  if (customJar) {
    if (!existsSync(customJar)) {
      throw new Error(`AGENT_SERVER_JAR not found: ${customJar}`)
    }
    assertRunnableSpringBootJar(customJar)
    return customJar
  }

  const bundled = join(process.resourcesPath, 'agent-server.jar')
  if (existsSync(bundled)) {
    assertRunnableSpringBootJar(bundled)
    return bundled
  }

  const tried: string[] = []
  for (const root of repoRootCandidates()) {
    const jar = join(root, 'server/target/agent-server-0.1.0.jar')
    tried.push(jar)
    if (existsSync(jar)) {
      assertRunnableSpringBootJar(jar)
      console.log('[agent] using jar:', jar)
      return jar
    }
  }

  throw new Error(
    [
      'Java agent-server JAR not found.',
      'Run from repo root:  pnpm build:java',
      'Or IDEA backend:     set AGENT_WS_URL=ws://127.0.0.1:5566/ws  then  pnpm dev:ui',
      'Searched:',
      ...tried.slice(0, 4).map(path => `  - ${path}`),
    ].join('\n'),
  )
}

const MIN_JAVA_MAJOR = 17
/** Prefer LTS / well-tested versions when multiple JDKs are installed. */
const PREFERRED_JAVA_MAJORS = [21, 17, 23, 22, 24, 25]
const AUTO_RESTART_MAX_ATTEMPTS = 5
const DEFAULT_AGENT_JAVA_OPTS = '-Xms128m -Xmx768m -XX:+UseSerialGC'

function parseJavaMajor(versionText: string): number | null {
  const modern = versionText.match(/version "(\d+)\./)
  if (modern) {
    const major = Number.parseInt(modern[1]!, 10)
    if (major >= 9) return major
  }
  const legacy = versionText.match(/version "1\.(\d+)/)
  if (legacy) return Number.parseInt(legacy[1]!, 10)
  return null
}

function javaMajorVersion(javaBin: string): number | null {
  try {
    const result = spawnSync(javaBin, ['-version'], { encoding: 'utf8' })
    const text = `${result.stdout ?? ''}${result.stderr ?? ''}`
    return parseJavaMajor(text)
  } catch {
    return null
  }
}

function collectJavaHomeCandidates(): string[] {
  const candidates: string[] = []
  const add = (home?: string) => {
    if (home && !candidates.includes(home)) candidates.push(home)
  }

  add(process.env['AGENT_JAVA_HOME'])

  for (const javaRoot of [
    join(process.env['ProgramFiles'] ?? 'C:\\Program Files', 'Java'),
    'D:\\Program Files\\Java',
  ]) {
    if (!existsSync(javaRoot)) continue
    for (const name of readdirSync(javaRoot)) {
      if (/^jdk-/i.test(name)) add(join(javaRoot, name))
    }
  }

  add('D:\\Program Files\\Java\\jdk-21.0.6')
  add(process.env['JAVA_HOME'])

  return candidates
}

function scoreJavaMajor(major: number): number {
  const idx = PREFERRED_JAVA_MAJORS.indexOf(major)
  return idx >= 0 ? idx : 100 + major
}

function resolveJavaRuntime(): { javaBin: string; javaHome: string } {
  const runtimes: Array<{ javaBin: string; javaHome: string; major: number }> = []

  for (const home of collectJavaHomeCandidates()) {
    const javaBin = join(home, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
    if (!existsSync(javaBin)) continue

    const major = javaMajorVersion(javaBin)
    if (major !== null && major >= MIN_JAVA_MAJOR) {
      if (!runtimes.some(item => item.javaHome === home)) {
        runtimes.push({ javaBin, javaHome: home, major })
      }
    }
  }

  if (runtimes.length === 0) {
    throw new Error(
      [
        'Java 17+ required to run agent-server (Spring Boot 3).',
        'Your PATH/JAVA_HOME may still point to Java 8.',
        'Fix: set AGENT_JAVA_HOME=D:\\Program Files\\Java\\jdk-21.0.6',
        'Or use IDEA backend: set AGENT_WS_URL=ws://127.0.0.1:PORT/ws then pnpm dev:ui',
      ].join('\n'),
    )
  }

  runtimes.sort((a, b) => scoreJavaMajor(a.major) - scoreJavaMajor(b.major))
  const chosen = runtimes[0]!
  console.log(`[agent] using Java ${chosen.major}: ${chosen.javaBin}`)
  return { javaBin: chosen.javaBin, javaHome: chosen.javaHome }
}

function buildJavaSpawnArgs(jarPath: string): string[] {
  const custom = process.env['AGENT_JAVA_OPTS']?.trim()
  const jvmOpts = (custom || DEFAULT_AGENT_JAVA_OPTS).split(/\s+/).filter(Boolean)
  return [...jvmOpts, '-jar', jarPath]
}

function attachReadyHandlers(
  proc: ChildProcess,
  onReady: (info: AgentConnectionInfo) => void,
  onFail: (error: Error) => void,
  timeout: NodeJS.Timeout,
): void {
  const handleReady = (port: number, wsUrl?: string) => {
    clearTimeout(timeout)
    onReady({
      port,
      wsUrl: wsUrl ?? `ws://127.0.0.1:${port}/ws`,
    })
  }

  proc.stdout?.on('data', chunk => {
    const text = chunk.toString() as string
    for (const line of text.split('\n')) {
      if (!line.startsWith('AGENT_READY ')) continue
      try {
        const payload = JSON.parse(line.slice('AGENT_READY '.length)) as AgentReadyMessage
        if (payload.type === 'agent-ready' && typeof payload.port === 'number') {
          handleReady(payload.port, payload.wsUrl)
        }
      } catch {
        // ignore malformed line
      }
    }
  })

  proc.stderr?.on('data', chunk => {
    console.error('[agent stderr]', chunk.toString())
  })

  proc.on('error', error => {
    clearTimeout(timeout)
    onFail(error)
  })

  proc.on('exit', (code, signal) => {
    agentProcess = null
    agentConnection = null
    console.error(`[agent] exited code=${code} signal=${signal}`)
    onAgentDisconnected?.()
    if (!externalAgent) {
      scheduleAgentAutoRestart()
    }
  })
}

let onAgentDisconnected: (() => void) | null = null
let autoRestartTimer: ReturnType<typeof setTimeout> | null = null
let autoRestartAttempts = 0

const AUTO_RESTART_BASE_MS = 2_000
const AUTO_RESTART_MAX_MS = 30_000

function clearAutoRestartTimer(): void {
  if (autoRestartTimer) {
    clearTimeout(autoRestartTimer)
    autoRestartTimer = null
  }
}

function scheduleAgentAutoRestart(): void {
  if (externalAgent || autoRestartTimer) return

  if (autoRestartAttempts >= AUTO_RESTART_MAX_ATTEMPTS) {
    console.error(
      `[agent] auto-restart stopped after ${AUTO_RESTART_MAX_ATTEMPTS} failures.`,
      'Check system memory / paging file, set AGENT_JAVA_HOME to JDK 21, or use AGENT_WS_URL.',
    )
    return
  }

  const delay = Math.min(AUTO_RESTART_BASE_MS * 2 ** autoRestartAttempts, AUTO_RESTART_MAX_MS)
  autoRestartAttempts += 1
  console.log(`[agent] scheduling auto-restart in ${Math.round(delay / 1000)}s (attempt ${autoRestartAttempts})`)

  autoRestartTimer = setTimeout(() => {
    autoRestartTimer = null
    void startAgentProcess()
      .then(() => {
        autoRestartAttempts = 0
        console.log('[agent] auto-restart succeeded')
      })
      .catch(error => {
        console.error('[agent] auto-restart failed:', error)
        scheduleAgentAutoRestart()
      })
  }, delay)
}

export function setAgentDisconnectedHandler(handler: () => void): void {
  onAgentDisconnected = handler
}

export function getAgentConnection(): AgentConnectionInfo | null {
  return agentConnection
}

export function startAgentProcess(): Promise<AgentConnectionInfo> {
  const external = getExternalAgentConnection()
  if (external) {
    agentConnection = external
    return Promise.resolve(external)
  }

  return new Promise((resolve, reject) => {
    if (agentProcess && agentConnection) {
      resolve(agentConnection)
      return
    }

    const timeout = setTimeout(() => {
      reject(new Error('Java agent-server startup timed out (60s). Check MySQL/Redis are running.'))
    }, 60_000)

    const onReady = (info: AgentConnectionInfo) => {
      clearAutoRestartTimer()
      autoRestartAttempts = 0
      agentConnection = info
      externalAgent = false
      console.log('[agent] ready:', info.wsUrl)
      resolve(info)
    }

    let jarPath: string
    try {
      jarPath = resolveJavaAgentJar()
    } catch (error) {
      clearTimeout(timeout)
      reject(error)
      return
    }

    const { javaBin, javaHome } = resolveJavaRuntime()
    const javaArgs = buildJavaSpawnArgs(jarPath)
    console.log('[agent] starting Spring Boot:', jarPath)
    console.log('[agent] JVM opts:', javaArgs.slice(0, -2).join(' '))

    agentProcess = spawn(javaBin, javaArgs, {
      stdio: ['ignore', 'pipe', 'pipe'],
      env: { ...process.env, JAVA_HOME: javaHome },
    })

    attachReadyHandlers(agentProcess, onReady, reject, timeout)
  })
}

export function stopAgentProcess(): void {
  clearAutoRestartTimer()
  if (externalAgent) {
    agentConnection = null
    return
  }
  if (agentProcess) {
    agentProcess.kill('SIGTERM')
    agentProcess = null
    agentConnection = null
  }
}

export async function restartAgentProcess(): Promise<AgentConnectionInfo> {
  if (externalAgent) {
    agentConnection = null
    return startAgentProcess()
  }
  stopAgentProcess()
  return startAgentProcess()
}
