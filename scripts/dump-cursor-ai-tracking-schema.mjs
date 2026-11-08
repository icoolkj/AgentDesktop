import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const require = createRequire(join(dirname(fileURLToPath(import.meta.url)), '../desktop/package.json'))
const Database = require('better-sqlite3')

const dbPath = process.argv[2] ?? 'C:/Users/admin/.cursor/ai-tracking/ai-code-tracking.db'
const db = new Database(dbPath, { readonly: true })

const tables = db
  .prepare("SELECT name, sql FROM sqlite_master WHERE type='table' ORDER BY name")
  .all()

for (const t of tables) {
  console.log(`-- TABLE: ${t.name}`)
  console.log(t.sql)
  console.log('')
  const cols = db.prepare(`PRAGMA table_info(${t.name})`).all()
  console.log('COLUMNS:', JSON.stringify(cols, null, 2))
  const indexes = db.prepare(`PRAGMA index_list(${t.name})`).all()
  if (indexes.length) console.log('INDEXES:', JSON.stringify(indexes, null, 2))
  console.log('\n---\n')
}

const sample = db.prepare('SELECT name FROM sqlite_master WHERE type=\'table\'').all()
for (const { name } of sample) {
  try {
    const count = db.prepare(`SELECT COUNT(*) as c FROM "${name}"`).get()
    console.log(`${name}: ${count.c} rows`)
    const row = db.prepare(`SELECT * FROM "${name}" LIMIT 1`).get()
    if (row) console.log('SAMPLE:', JSON.stringify(row).slice(0, 500))
  } catch {
    /* ignore */
  }
}

db.close()
