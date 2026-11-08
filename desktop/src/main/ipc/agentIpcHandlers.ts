import { ipcMain, BrowserWindow } from 'electron'
import {
  getAgentConnection,
  restartAgentProcess,
  startAgentProcess,
} from '../services/agentProcessService'

export function setupAgentIpcHandlers(): void {
  ipcMain.handle('agent:getConnection', async () => {
    if (!getAgentConnection()) {
      return startAgentProcess()
    }
    return getAgentConnection()
  })

  ipcMain.handle('agent:restart', async () => restartAgentProcess())
}

export function notifyAgentDisconnected(): void {
  for (const win of BrowserWindow.getAllWindows()) {
    win.webContents.send('agent:disconnected')
  }
}
