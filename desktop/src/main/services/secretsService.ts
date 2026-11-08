import { safeStorage } from 'electron'
import { mkdirSync, readFileSync, writeFileSync, existsSync } from 'node:fs'
import { join } from 'node:path'
import { app } from 'electron'

const SECRETS_FILE = 'secrets.enc'

function secretsPath(): string {
  return join(app.getPath('userData'), SECRETS_FILE)
}

function readStore(): Record<string, string> {
  const path = secretsPath()
  if (!existsSync(path)) return {}
  try {
    const raw = readFileSync(path, 'utf8')
    return JSON.parse(raw) as Record<string, string>
  } catch {
    return {}
  }
}

function writeStore(store: Record<string, string>): void {
  mkdirSync(app.getPath('userData'), { recursive: true })
  writeFileSync(secretsPath(), JSON.stringify(store), 'utf8')
}

export function isSecretsAvailable(): boolean {
  return safeStorage.isEncryptionAvailable()
}

export function getSecret(key: string): string | null {
  const store = readStore()
  const encoded = store[key]
  if (!encoded) return null
  if (!safeStorage.isEncryptionAvailable()) return null
  try {
    return safeStorage.decryptString(Buffer.from(encoded, 'base64'))
  } catch {
    return null
  }
}

export function setSecret(key: string, value: string): boolean {
  if (!safeStorage.isEncryptionAvailable()) return false
  const store = readStore()
  store[key] = safeStorage.encryptString(value).toString('base64')
  writeStore(store)
  return true
}

export function deleteSecret(key: string): void {
  const store = readStore()
  delete store[key]
  writeStore(store)
}
