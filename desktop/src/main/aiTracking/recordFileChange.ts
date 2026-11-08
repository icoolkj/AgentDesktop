import type Database from 'better-sqlite3'

import { getAiTrackingDb } from './database'
import { resolveGitFileContext } from './gitContext'
import {
  collectLineHashes,
  fileExtensionFromPath,
  fileNameForTracking,
  hashAiCodeLine,
  splitLines,
} from './hash'

export interface RecordFileChangeInput {
  absolutePath: string
  workspaceRoot?: string | null
  content: string
  previousContent?: string | null
  changeKind: 'modified' | 'created'
  conversationId?: string | null
  requestId?: string | null
  model?: string | null
  source?: 'composer' | 'tab'
  /** false = only sync tracked_file_content (manual editor save, counted as human on commit). */
  recordHashes?: boolean
}

const insertHash = `
INSERT OR IGNORE INTO ai_code_hashes (
  hash, source, fileExtension, fileName, requestId, conversationId, timestamp, model, createdAt
) VALUES (@hash, @source, @fileExtension, @fileName, @requestId, @conversationId, @timestamp, @model, @createdAt)
`

const upsertTrackedContent = `
INSERT INTO tracked_file_content (gitPath, content, conversationId, model, fileExtension, createdAt)
VALUES (@gitPath, @content, @conversationId, @model, @fileExtension, @createdAt)
ON CONFLICT(gitPath) DO UPDATE SET
  content = excluded.content,
  conversationId = excluded.conversationId,
  model = excluded.model,
  fileExtension = excluded.fileExtension,
  createdAt = excluded.createdAt
`

function linesToHash(content: string | null | undefined): Set<string> {
  if (!content) return new Set()
  return new Set(collectLineHashes(content))
}

function newLineHashes(previousContent: string | null | undefined, content: string): string[] {
  const prev = linesToHash(previousContent)
  const added: string[] = []
  for (const line of splitLines(content)) {
    if (line.trim().length === 0) continue
    const hash = hashAiCodeLine(line)
    if (!prev.has(hash)) added.push(hash)
  }
  return added
}

export async function recordFileChange(input: RecordFileChangeInput): Promise<void> {
  const db = getAiTrackingDb()
  const git = await resolveGitFileContext(input.absolutePath, input.workspaceRoot ?? undefined)
  const source = input.source ?? 'composer'
  const recordHashes = input.recordHashes ?? true

  // Manual editor saves only participate in git-tracked workspaces.
  if (source === 'tab' && !git.gitRoot) return

  const now = Date.now()
  const model = input.model ?? 'default'
  const fileName = fileNameForTracking(input.absolutePath, git.gitPath)
  const fileExtension = fileExtensionFromPath(input.absolutePath)
  const gitPath = git.gitPath ?? fileName.replace(/^\//, '')

  const tx = db.transaction(() => {
    db.prepare(upsertTrackedContent).run({
      gitPath,
      content: input.content,
      conversationId: input.conversationId ?? null,
      model,
      fileExtension,
      createdAt: now,
    })

    if (!recordHashes) return

    const hashes =
      input.changeKind === 'created'
        ? collectLineHashes(input.content)
        : newLineHashes(input.previousContent, input.content)

    const stmt = db.prepare(insertHash)
    for (const hash of hashes) {
      stmt.run({
        hash,
        source,
        fileExtension,
        fileName,
        requestId: input.requestId ?? null,
        conversationId: input.conversationId ?? null,
        timestamp: now,
        model,
        createdAt: now,
      })
    }
  })

  tx()
}

export interface RecordFileDeleteInput {
  absolutePath: string
  workspaceRoot?: string | null
  conversationId?: string | null
  composerId?: string | null
  model?: string | null
}

export async function recordFileDelete(input: RecordFileDeleteInput): Promise<void> {
  const db = getAiTrackingDb()
  const git = await resolveGitFileContext(input.absolutePath, input.workspaceRoot ?? undefined)
  const gitPath = git.gitPath ?? fileNameForTracking(input.absolutePath, null).replace(/^\//, '')
  const now = Date.now()

  db.prepare(
    `INSERT INTO ai_deleted_files (gitPath, composerId, conversationId, model, deletedAt)
     VALUES (?, ?, ?, ?, ?)`,
  ).run(gitPath, input.composerId ?? null, input.conversationId ?? null, input.model ?? 'default', now)

  db.prepare(`DELETE FROM tracked_file_content WHERE gitPath = ?`).run(gitPath)
}

export interface ConversationSummaryInput {
  conversationId: string
  title?: string | null
  tldr?: string | null
  overview?: string | null
  summaryBullets?: string | null
  model?: string | null
  mode?: string | null
}

export function upsertConversationSummary(input: ConversationSummaryInput): void {
  const db = getAiTrackingDb()
  db.prepare(
    `INSERT INTO conversation_summaries (
      conversationId, title, tldr, overview, summaryBullets, model, mode, updatedAt
    ) VALUES (@conversationId, @title, @tldr, @overview, @summaryBullets, @model, @mode, @updatedAt)
    ON CONFLICT(conversationId) DO UPDATE SET
      title = COALESCE(excluded.title, conversation_summaries.title),
      tldr = COALESCE(excluded.tldr, conversation_summaries.tldr),
      overview = COALESCE(excluded.overview, conversation_summaries.overview),
      summaryBullets = COALESCE(excluded.summaryBullets, conversation_summaries.summaryBullets),
      model = COALESCE(excluded.model, conversation_summaries.model),
      mode = COALESCE(excluded.mode, conversation_summaries.mode),
      updatedAt = excluded.updatedAt`,
  ).run({
    conversationId: input.conversationId,
    title: input.title ?? null,
    tldr: input.tldr ?? null,
    overview: input.overview ?? null,
    summaryBullets: input.summaryBullets ?? null,
    model: input.model ?? null,
    mode: input.mode ?? null,
    updatedAt: Date.now(),
  })
}

export function lookupHashSource(
  db: Database.Database,
  hash: string,
): 'composer' | 'tab' | null {
  const row = db.prepare(`SELECT source FROM ai_code_hashes WHERE hash = ? LIMIT 1`).get(hash) as
    | { source?: string }
    | undefined
  if (!row?.source) return null
  if (row.source === 'tab') return 'tab'
  if (row.source === 'composer') return 'composer'
  return 'composer'
}

export function isKnownAiHash(db: Database.Database, hash: string): boolean {
  return lookupHashSource(db, hash) != null
}
