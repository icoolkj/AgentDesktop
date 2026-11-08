import { contextBridge } from 'electron'
import { agentApi } from './api/agentApi'
import { desktopApi } from './api/desktopApi'
import { terminalApi } from './api/terminalApi'
import { transcriptApi } from './api/transcriptApi'
import { workspaceApi } from './api/workspaceApi'
import { secretsApi } from './api/secretsApi'

contextBridge.exposeInMainWorld('agentApi', agentApi)
contextBridge.exposeInMainWorld('desktopApi', desktopApi)
contextBridge.exposeInMainWorld('terminalApi', terminalApi)
contextBridge.exposeInMainWorld('workspaceApi', workspaceApi)
contextBridge.exposeInMainWorld('transcriptApi', transcriptApi)
contextBridge.exposeInMainWorld('secretsApi', secretsApi)
contextBridge.exposeInMainWorld('desktopEnv', { platform: process.platform })

declare global {
  interface Window {
    agentApi: typeof agentApi
    desktopApi: typeof desktopApi
    terminalApi: typeof terminalApi
    workspaceApi: typeof workspaceApi
    transcriptApi: typeof transcriptApi
    secretsApi: typeof secretsApi
    desktopEnv: { platform: NodeJS.Platform }
  }
}
