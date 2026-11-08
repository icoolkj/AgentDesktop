import { spawn, type ChildProcess } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.join(__dirname, '..', '..', '..')

function resolveJar(): string {
  const envJar = process.env.AGENT_SERVER_JAR?.trim()
  if (envJar && fs.existsSync(envJar)) return envJar
  const defaultJar = path.join(repoRoot, 'server', 'target', 'agent-server-0.1.0.jar')
  if (!fs.existsSync(defaultJar)) {
    throw new Error(`Missing agent-server JAR. Run: mvn -f server package -DskipTests (${defaultJar})`)
  }
  return defaultJar
}

export async function startRealAgentServer(): Promise<{ wsUrl: string; close: () => Promise<void> }> {
  const jar = resolveJar()
  const child: ChildProcess = spawn(
    process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', 'java') : 'java',
    ['-jar', jar],
    {
      cwd: repoRoot,
      env: {
        ...process.env,
        AGENT_SERVER_PORT: '0',
      },
      stdio: ['ignore', 'pipe', 'pipe'],
    },
  )

  const wsUrl = await new Promise<string>((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('Timed out waiting for AGENT_READY')), 60_000)
    child.stdout?.on('data', chunk => {
      const text = chunk.toString()
      for (const line of text.split('\n')) {
        const trimmed = line.trim()
        if (!trimmed.startsWith('AGENT_READY')) continue
        try {
          const payload = JSON.parse(trimmed.slice('AGENT_READY'.length).trim()) as { wsUrl?: string }
          if (payload.wsUrl) {
            clearTimeout(timeout)
            resolve(payload.wsUrl)
          }
        } catch {
          // ignore malformed lines
        }
      }
    })
    child.on('exit', code => {
      clearTimeout(timeout)
      reject(new Error(`agent-server exited before ready: ${code}`))
    })
  })

  return {
    wsUrl,
    close: async () => {
      child.kill('SIGTERM')
      await new Promise<void>(res => child.once('exit', () => res()))
    },
  }
}
