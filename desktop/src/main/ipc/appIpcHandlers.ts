import { ipcMain, BrowserWindow, shell } from 'electron'
import {
  openWorkspaceWindow,
  createEmptyWindow,
  focusWorkspaceWindow,
  registerWindowWorkspace,
  getWindowBootConfig,
} from '../windowManager'
import { ensureWorkspaceReady } from '../paths/ensureWorkspaceReady'
import { getDevContext } from '../utils/selfWorkspace'
import { formatWorkspaceWindowTitle } from '../appIdentity'

export function setupAppIpcHandlers(): void {
  ipcMain.handle(
    'app:openWorkspaceWindow',
    async (event, workspacePath?: string, options?: { forceNew?: boolean; openInNewWindow?: boolean }) => {
      if (workspacePath != null && typeof workspacePath !== 'string') {
        throw new Error('Invalid workspace path')
      }
      const win = BrowserWindow.fromWebContents(event.sender)
      const openInNewWindow = options?.openInNewWindow === true
      return openWorkspaceWindow(workspacePath, {
        forceNew: options?.forceNew === true,
        excludeWindowId: openInNewWindow && win && !win.isDestroyed() ? win.id : undefined,
      })
    },
  )

  ipcMain.handle('app:newWindow', async () => {
    createEmptyWindow()
  })

  ipcMain.handle('app:getWindowBoot', async event => {
    const win = BrowserWindow.fromWebContents(event.sender)
    if (!win || win.isDestroyed()) return { mode: 'blank' as const }
    return getWindowBootConfig(win.id)
  })

  ipcMain.handle('app:focusWorkspaceWindow', async (_, workspacePath: string) => {
    if (!workspacePath || typeof workspacePath !== 'string') {
      throw new Error('Invalid workspace path')
    }
    return focusWorkspaceWindow(workspacePath)
  })

  ipcMain.handle('app:syncWindowWorkspace', async (event, workspacePath?: string | null) => {
    const win = BrowserWindow.fromWebContents(event.sender)
    if (!win || win.isDestroyed()) return
    registerWindowWorkspace(win, workspacePath)
    win.setTitle(formatWorkspaceWindowTitle(workspacePath))
  })

  ipcMain.handle('app:ensureWorkspaceReady', async (_, workspaceRoot: string) => {
    if (!workspaceRoot || typeof workspaceRoot !== 'string') {
      throw new Error('Invalid workspace root')
    }
    return ensureWorkspaceReady(workspaceRoot)
  })

  ipcMain.handle('app:setWindowTitle', async (event, workspaceRoot?: string | null) => {
    const win = BrowserWindow.fromWebContents(event.sender)
    if (!win || win.isDestroyed()) return
    win.setTitle(formatWorkspaceWindowTitle(workspaceRoot))
  })

  ipcMain.handle('app:getDevContext', async (_, workspaceRoot?: string | null) => {
    if (workspaceRoot != null && typeof workspaceRoot !== 'string') {
      throw new Error('Invalid workspace root')
    }
    return getDevContext(workspaceRoot)
  })

  ipcMain.handle(
    'app:setTitleBarOverlay',
    async (event, overlay: { color: string; symbolColor: string }) => {
      if (process.platform !== 'win32') return
      if (!overlay || typeof overlay.color !== 'string' || typeof overlay.symbolColor !== 'string') {
        return
      }
      const win = BrowserWindow.fromWebContents(event.sender)
      if (!win || win.isDestroyed()) return
      win.setTitleBarOverlay({
        color: overlay.color,
        symbolColor: overlay.symbolColor,
        height: 35,
      })
    },
  )

  ipcMain.handle('app:openExternal', async (_, url: string) => {
    if (!url || typeof url !== 'string') {
      throw new Error('Invalid URL')
    }
    const parsed = new URL(url)
    if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') {
      throw new Error('Only http(s) URLs are allowed')
    }
    await shell.openExternal(url)
  })
}
