import { ipcMain } from 'electron'
import { deleteSecret, getSecret, isSecretsAvailable, setSecret } from '../services/secretsService'

export function setupSecretsIpcHandlers(): void {
  ipcMain.handle('secrets:isAvailable', async () => isSecretsAvailable())

  ipcMain.handle('secrets:get', async (_, key: string) => {
    if (!key || typeof key !== 'string') throw new Error('Invalid key')
    return getSecret(key)
  })

  ipcMain.handle('secrets:set', async (_, key: string, value: string) => {
    if (!key || typeof key !== 'string') throw new Error('Invalid key')
    if (typeof value !== 'string') throw new Error('Invalid value')
    return setSecret(key, value)
  })

  ipcMain.handle('secrets:delete', async (_, key: string) => {
    if (!key || typeof key !== 'string') throw new Error('Invalid key')
    deleteSecret(key)
  })
}
