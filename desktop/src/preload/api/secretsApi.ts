import { ipcRenderer } from 'electron'

export const secretsApi = {
  isAvailable: (): Promise<boolean> => ipcRenderer.invoke('secrets:isAvailable'),
  get: (key: string): Promise<string | null> => ipcRenderer.invoke('secrets:get', key),
  set: (key: string, value: string): Promise<boolean> => ipcRenderer.invoke('secrets:set', key, value),
  delete: (key: string): Promise<void> => ipcRenderer.invoke('secrets:delete', key),
}

export type SecretsApi = typeof secretsApi
