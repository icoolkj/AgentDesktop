import { app, BrowserWindow } from 'electron'
import { configureAppIdentity } from './appIdentity'
import { setupIpcHandlers, notifyAgentDisconnected } from './ipc'
import { closeAiTrackingDb } from './aiTracking'
import { ensureHomeLayout } from './paths/agentDesktopPaths'
import { migrateLegacySessions } from './transcript/migrateLegacySessions'
import { createAppWindow } from './windowManager'
import {
  setAgentDisconnectedHandler,
  startAgentProcess,
  stopAgentProcess,
} from './services/agentProcessService'

configureAppIdentity()

function createWindow(): void {
  createAppWindow({ boot: 'restore' })
}

app.whenReady().then(async () => {
  ensureHomeLayout()
  await migrateLegacySessions()
  setupIpcHandlers()
  setAgentDisconnectedHandler(notifyAgentDisconnected)

  try {
    await startAgentProcess()
  } catch (error) {
    console.error('Failed to connect/start Java agent-server:', error)
  }

  createWindow()

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

app.on('window-all-closed', () => {
  stopAgentProcess()
  if (process.platform !== 'darwin') app.quit()
})

app.on('before-quit', () => {
  stopAgentProcess()
  closeAiTrackingDb()
})
