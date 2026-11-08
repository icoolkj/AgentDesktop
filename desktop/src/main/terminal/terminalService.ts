import { spawn, type ChildProcessWithoutNullStreams } from 'node:child_process'
import { ipcMain, type IpcMainInvokeEvent, type WebContents } from 'electron'
import pty from 'node-pty'

type IPty = pty.IPty
type Backend = 'pty' | 'pipe'

interface TerminalSession {
  backend: Backend | null
  ptyProcess: IPty | null
  pipeProcess: ChildProcessWithoutNullStreams | null
  intentionalKillGeneration: number
  activeKillGeneration: number
  cwd: string
  webContents: WebContents
}

const sessions = new Map<number, TerminalSession>()

function shellCommand(): { file: string; args: string[] } {
  if (process.platform === 'win32') {
    return { file: 'powershell.exe', args: ['-NoLogo'] }
  }
  return { file: process.env.SHELL || '/bin/bash', args: ['-l'] }
}

function normalizeCwd(cwd: string | null | undefined): string {
  return (cwd || process.cwd()).replace(/\\/g, '/').replace(/\/+$/, '').toLowerCase()
}

function isPtyAlive(session: TerminalSession): boolean {
  return session.ptyProcess !== null
}

function isPipeAlive(session: TerminalSession): boolean {
  return (
    session.pipeProcess != null &&
    session.pipeProcess.exitCode == null &&
    !session.pipeProcess.killed
  )
}

function emitData(session: TerminalSession, data: string): void {
  if (session.webContents.isDestroyed()) return
  session.webContents.send('terminal:data', data)
}

function emitExit(session: TerminalSession, killGeneration: number): void {
  if (killGeneration !== session.activeKillGeneration) return
  if (session.webContents.isDestroyed()) return
  session.webContents.send('terminal:exit')
}

function killSession(session: TerminalSession): void {
  const killGeneration = ++session.intentionalKillGeneration
  session.activeKillGeneration = killGeneration

  if (session.ptyProcess) {
    session.ptyProcess.kill()
    session.ptyProcess = null
  }
  if (session.pipeProcess) {
    session.pipeProcess.kill()
    session.pipeProcess = null
  }
  session.backend = null
}

function removeSession(webContentsId: number): void {
  const session = sessions.get(webContentsId)
  if (!session) return
  killSession(session)
  sessions.delete(webContentsId)
}

function getSession(event: IpcMainInvokeEvent): TerminalSession | null {
  return sessions.get(event.sender.id) ?? null
}

function ensureWebContentsCleanup(webContents: WebContents): void {
  webContents.once('destroyed', () => {
    removeSession(webContents.id)
  })
}

function createPty(
  session: TerminalSession,
  cwd: string | null | undefined,
  killGeneration: number,
): boolean {
  try {
    const shell = shellCommand()
    const resolvedCwd = cwd || process.cwd()
    session.ptyProcess = pty.spawn(shell.file, shell.args, {
      name: 'xterm-256color',
      cols: 120,
      rows: 30,
      cwd: resolvedCwd,
      env: {
        ...process.env,
        TERM: 'xterm-256color',
        COLORTERM: 'truecolor',
      } as Record<string, string>,
    })

    session.cwd = normalizeCwd(resolvedCwd)
    session.ptyProcess.onData(data => emitData(session, data))
    session.ptyProcess.onExit(() => {
      session.ptyProcess = null
      session.backend = null
      emitExit(session, killGeneration)
    })

    session.backend = 'pty'
    return true
  } catch (err) {
    console.warn('[terminal] PTY spawn failed, falling back to pipe mode:', err)
    session.ptyProcess = null
    return false
  }
}

function createPipe(
  session: TerminalSession,
  cwd: string | null | undefined,
  killGeneration: number,
): void {
  const shell = shellCommand()
  const resolvedCwd = cwd || process.cwd()
  session.pipeProcess = spawn(shell.file, shell.args, {
    cwd: resolvedCwd,
    env: process.env,
    stdio: ['pipe', 'pipe', 'pipe'],
    windowsHide: false,
  })

  session.cwd = normalizeCwd(resolvedCwd)
  session.pipeProcess.stdout.on('data', chunk => emitData(session, chunk.toString('utf8')))
  session.pipeProcess.stderr.on('data', chunk => emitData(session, chunk.toString('utf8')))
  session.pipeProcess.on('exit', () => {
    session.pipeProcess = null
    session.backend = null
    emitExit(session, killGeneration)
  })
  session.pipeProcess.on('error', err => {
    emitData(session, `\r\n[terminal error] ${err.message}\r\n`)
  })

  emitData(
    session,
    '\r\n\x1b[33m[pipe mode]\x1b[0m vim/彩色 TUI 受限。见 docs/windows-terminal-setup.md 启用 PTY。\r\n',
  )
  session.backend = 'pipe'
}

function createSession(webContents: WebContents, cwd: string | null | undefined): TerminalSession {
  const existing = sessions.get(webContents.id)
  if (existing) {
    killSession(existing)
  } else {
    ensureWebContentsCleanup(webContents)
  }

  const session: TerminalSession = {
    backend: null,
    ptyProcess: null,
    pipeProcess: null,
    intentionalKillGeneration: 0,
    activeKillGeneration: 0,
    cwd: normalizeCwd(cwd),
    webContents,
  }
  sessions.set(webContents.id, session)

  const killGeneration = ++session.intentionalKillGeneration
  session.activeKillGeneration = killGeneration

  if (!createPty(session, cwd, killGeneration)) {
    createPipe(session, cwd, killGeneration)
  }

  return session
}

export function registerTerminalIpc(): void {
  ipcMain.handle('terminal:create', async (event, cwd?: string | null) => {
    const webContents = event.sender
    if (webContents.isDestroyed()) {
      return { ok: false, backend: null, reused: false }
    }

    const targetCwd = normalizeCwd(cwd)
    const existing = sessions.get(webContents.id)
    if (
      existing &&
      (isPtyAlive(existing) || isPipeAlive(existing)) &&
      existing.cwd === targetCwd
    ) {
      return { ok: true, backend: existing.backend ?? 'pipe', reused: true }
    }

    const session = createSession(webContents, cwd)
    return { ok: true, backend: session.backend ?? 'pipe', reused: false }
  })

  ipcMain.handle('terminal:write', async (event, data: string) => {
    const session = getSession(event)
    if (!session) return { ok: false }

    if (session.ptyProcess) {
      session.ptyProcess.write(data)
    } else if (session.pipeProcess) {
      session.pipeProcess.stdin.write(data)
    }
    return { ok: true }
  })

  ipcMain.handle('terminal:resize', async (event, cols: number, rows: number) => {
    const session = getSession(event)
    if (session?.ptyProcess && cols > 0 && rows > 0) {
      session.ptyProcess.resize(cols, rows)
    }
    return { ok: true }
  })

  ipcMain.handle('terminal:kill', async event => {
    removeSession(event.sender.id)
    return { ok: true }
  })

  ipcMain.handle('terminal:isAlive', async event => {
    const session = getSession(event)
    return {
      ok: true,
      alive: session ? isPtyAlive(session) || isPipeAlive(session) : false,
      backend: session?.backend ?? null,
      cwd: session?.cwd ?? null,
    }
  })
}

/** @deprecated use registerTerminalIpc */
export function setupTerminalIpcHandlers(): void {
  registerTerminalIpc()
}
