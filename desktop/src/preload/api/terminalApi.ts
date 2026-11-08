import { ipcRenderer } from 'electron'

export const terminalApi = {
  create: (cwd?: string | null): Promise<{ ok: boolean }> =>
    ipcRenderer.invoke('terminal:create', cwd),

  write: (data: string): Promise<{ ok: boolean }> => ipcRenderer.invoke('terminal:write', data),

  resize: (cols: number, rows: number): Promise<{ ok: boolean }> =>
    ipcRenderer.invoke('terminal:resize', cols, rows),

  kill: (): Promise<{ ok: boolean }> => ipcRenderer.invoke('terminal:kill'),

  onData: (callback: (data: string) => void): (() => void) => {
    const listener = (_: unknown, data: string) => callback(data)
    ipcRenderer.on('terminal:data', listener)
    return () => ipcRenderer.removeListener('terminal:data', listener)
  },

  onExit: (callback: () => void): (() => void) => {
    const listener = () => callback()
    ipcRenderer.on('terminal:exit', listener)
    return () => ipcRenderer.removeListener('terminal:exit', listener)
  },
}

export type TerminalApi = typeof terminalApi
