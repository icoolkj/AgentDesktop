import { join } from 'node:path'
import { BrowserWindow, dialog } from 'electron'
import { formatWorkspaceWindowTitle } from './appIdentity'

export type WindowBootMode = 'restore' | 'blank' | 'workspace'

export interface WindowBootConfig {
  mode: WindowBootMode
  workspacePath?: string
}

export interface CreateAppWindowOptions {
  boot: WindowBootMode
  workspacePath?: string
}

const workspaceByWindowId = new Map<number, string>()
const bootByWindowId = new Map<number, WindowBootConfig>()

function normalizeWorkspaceKey(path: string): string {
  return path.replace(/\\/g, '/').replace(/\/+$/, '').toLowerCase()
}

function resolveCreateOptions(
  options?: CreateAppWindowOptions | string,
): CreateAppWindowOptions {
  if (typeof options === 'string') {
    return { boot: 'workspace', workspacePath: options }
  }
  return options ?? { boot: 'blank' }
}

function buildRendererTarget(boot: WindowBootConfig): {
  url?: string
  file?: string
  query?: Record<string, string>
} {
  const rendererUrl = process.env['ELECTRON_RENDERER_URL']
  const query: Record<string, string> = {}

  if (boot.mode === 'workspace' && boot.workspacePath) {
    query.workspace = boot.workspacePath
  } else if (boot.mode === 'blank') {
    query.blank = '1'
  } else if (boot.mode === 'restore') {
    query.restore = '1'
  }

  if (rendererUrl) {
    const url = new URL(rendererUrl)
    for (const [key, value] of Object.entries(query)) {
      url.searchParams.set(key, value)
    }
    return { url: url.toString() }
  }

  const file = join(__dirname, '../renderer/index.html')
  return Object.keys(query).length > 0 ? { file, query } : { file }
}

export function getWindowBootConfig(windowId: number): WindowBootConfig {
  return bootByWindowId.get(windowId) ?? { mode: 'blank' }
}

export function createAppWindow(options?: CreateAppWindowOptions | string): BrowserWindow {
  const resolved = resolveCreateOptions(options)
  const workspacePath = resolved.boot === 'workspace' ? resolved.workspacePath : undefined
  const isWin = process.platform === 'win32'

  const win = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 960,
    minHeight: 600,
    show: false,
    autoHideMenuBar: true,
    title: formatWorkspaceWindowTitle(workspacePath),
    ...(isWin
      ? {
          titleBarStyle: 'hidden' as const,
          titleBarOverlay: {
            color: '#181818',
            symbolColor: '#cccccc',
            height: 35,
          },
        }
      : {}),
    webPreferences: {
      preload: join(__dirname, '../preload/index.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
    },
  })

  bootByWindowId.set(win.id, {
    mode: resolved.boot,
    workspacePath: workspacePath?.trim() || undefined,
  })

  trackWindow(win, workspacePath)

  win.on('closed', () => {
    bootByWindowId.delete(win.id)
    unregisterWindow(win.id)
  })

  const target = buildRendererTarget({
    mode: resolved.boot,
    workspacePath: workspacePath?.trim() || undefined,
  })
  if (target.url) {
    void win.loadURL(target.url)
  } else if (target.file) {
    void win.loadFile(target.file, target.query ? { query: target.query } : undefined)
  }

  win.on('ready-to-show', () => {
    win.show()
  })

  return win
}

function unregisterWindow(windowId: number): void {
  workspaceByWindowId.delete(windowId)
}

function trackWindow(win: BrowserWindow, workspacePath?: string): void {
  registerWindowWorkspace(win, workspacePath)
}

/** Keep main-process window ↔ workspace map in sync when renderer switches project in-place. */
export function registerWindowWorkspace(win: BrowserWindow, workspacePath?: string | null): void {
  if (!workspacePath?.trim()) {
    unregisterWindow(win.id)
    return
  }

  workspaceByWindowId.set(win.id, normalizeWorkspaceKey(workspacePath))
}

export interface OpenWorkspaceWindowOptions {
  /** Always create a new window, even if the project is already open elsewhere. */
  forceNew?: boolean
  /** When set, do not treat a match on this window as "already open" (IDE: open in new window). */
  excludeWindowId?: number
}

export function focusWorkspaceWindow(
  workspacePath: string,
  options?: Pick<OpenWorkspaceWindowOptions, 'excludeWindowId'>,
): boolean {
  const key = normalizeWorkspaceKey(workspacePath)

  for (const win of BrowserWindow.getAllWindows()) {
    if (win.isDestroyed()) continue
    if (options?.excludeWindowId != null && win.id === options.excludeWindowId) continue
    if (workspaceByWindowId.get(win.id) !== key) continue

    if (win.isMinimized()) win.restore()
    win.focus()
    return true
  }

  return false
}

export async function openWorkspaceWindow(
  workspacePath?: string,
  options?: OpenWorkspaceWindowOptions,
): Promise<string | null> {
  let path = workspacePath?.trim()

  if (!path) {
    const parent = BrowserWindow.getFocusedWindow() ?? BrowserWindow.getAllWindows()[0]
    const result = await dialog.showOpenDialog(parent ?? undefined, {
      properties: ['openDirectory'],
    })
    if (result.canceled || result.filePaths.length === 0) return null
    path = result.filePaths[0]!
  }

  if (!options?.forceNew && focusWorkspaceWindow(path, options)) return path

  createAppWindow({ boot: 'workspace', workspacePath: path })
  return path
}

export function createEmptyWindow(): BrowserWindow {
  return createAppWindow({ boot: 'blank' })
}
