import Database from 'better-sqlite3'
import { mkdirSync } from 'node:fs'
import { dirname } from 'node:path'

import { getAiTrackingDbPath } from '../paths/agentDesktopPaths'
import { AI_TRACKING_SCHEMA_SQL, TRACKING_START_KEY } from './schema'

let db: Database.Database | null = null

export function getAiTrackingDb(): Database.Database {
  if (db) return db

  const dbPath = getAiTrackingDbPath()
  mkdirSync(dirname(dbPath), { recursive: true })

  db = new Database(dbPath)
  db.pragma('journal_mode = WAL')
  db.exec(AI_TRACKING_SCHEMA_SQL)

  const now = Date.now()
  db.prepare(
    `INSERT OR IGNORE INTO tracking_state (key, value) VALUES (?, ?)`,
  ).run(TRACKING_START_KEY, JSON.stringify({ timestamp: now }))

  return db
}

export function closeAiTrackingDb(): void {
  db?.close()
  db = null
}
