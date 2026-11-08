import { ipcRenderer } from 'electron'
import type { AgentConnectionInfo } from '@agent-desktop/shared-types'

export const agentApi = {
  getConnection: (): Promise<AgentConnectionInfo> => ipcRenderer.invoke('agent:getConnection'),
  restart: (): Promise<AgentConnectionInfo> => ipcRenderer.invoke('agent:restart'),
  onDisconnected: (callback: () => void): (() => void) => {
    const listener = () => callback()
    ipcRenderer.on('agent:disconnected', listener)
    return () => ipcRenderer.removeListener('agent:disconnected', listener)
  },
}

export type AgentApi = typeof agentApi
