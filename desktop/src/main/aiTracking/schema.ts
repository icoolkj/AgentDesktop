/** Cursor ai-code-tracking.db schema (reverse-engineered from ~/.cursor/ai-tracking). */

export const AI_TRACKING_SCHEMA_SQL = `
CREATE TABLE IF NOT EXISTS ai_code_hashes (
  hash TEXT PRIMARY KEY,
  source TEXT NOT NULL,
  fileExtension TEXT,
  fileName TEXT,
  requestId TEXT,
  conversationId TEXT,
  timestamp INTEGER,
  model TEXT,
  createdAt INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ai_code_hashes_createdAt ON ai_code_hashes (createdAt);

CREATE TABLE IF NOT EXISTS ai_deleted_files (
  gitPath TEXT NOT NULL,
  composerId TEXT,
  conversationId TEXT,
  model TEXT,
  deletedAt INTEGER NOT NULL,
  PRIMARY KEY (gitPath, deletedAt)
);

CREATE INDEX IF NOT EXISTS idx_ai_deleted_files_deletedAt ON ai_deleted_files (deletedAt);

CREATE TABLE IF NOT EXISTS conversation_summaries (
  conversationId TEXT PRIMARY KEY,
  title TEXT,
  tldr TEXT,
  overview TEXT,
  summaryBullets TEXT,
  model TEXT,
  mode TEXT,
  updatedAt INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_conversation_summaries_updatedAt ON conversation_summaries (updatedAt);

CREATE TABLE IF NOT EXISTS scored_commits (
  commitHash TEXT NOT NULL,
  branchName TEXT NOT NULL,
  scoredAt INTEGER NOT NULL,
  linesAdded INTEGER,
  linesDeleted INTEGER,
  tabLinesAdded INTEGER,
  tabLinesDeleted INTEGER,
  composerLinesAdded INTEGER,
  composerLinesDeleted INTEGER,
  humanLinesAdded INTEGER,
  humanLinesDeleted INTEGER,
  blankLinesAdded INTEGER,
  blankLinesDeleted INTEGER,
  commitMessage TEXT,
  commitDate TEXT,
  v1AiPercentage TEXT,
  v2AiPercentage TEXT,
  PRIMARY KEY (commitHash, branchName)
);

CREATE INDEX IF NOT EXISTS idx_scored_commits_scoredAt ON scored_commits (scoredAt);

CREATE TABLE IF NOT EXISTS tracked_file_content (
  gitPath TEXT PRIMARY KEY,
  content TEXT NOT NULL,
  conversationId TEXT,
  model TEXT,
  fileExtension TEXT,
  createdAt INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_tracked_file_content_createdAt ON tracked_file_content (createdAt);

CREATE TABLE IF NOT EXISTS tracking_state (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
);
`

export const TRACKING_START_KEY = 'trackingStartTime'
