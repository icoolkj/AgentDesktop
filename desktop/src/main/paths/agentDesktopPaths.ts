import { existsSync, mkdirSync, writeFileSync } from 'node:fs'
import { homedir } from 'node:os'
import { join } from 'node:path'

export const MAX_SANITIZED_LENGTH = 200

const HOME_GITIGNORE = `# Agent Desktop local data — selective tracking (Cursor-style)
*
!projects/
projects/*
!projects/*/
projects/*/*
!projects/*/agent-transcripts/
!projects/*/agent-transcripts/**
!projects/*/agent-tools/
!projects/*/agent-tools/**
!projects/*/terminals/
!projects/*/terminals/**
!projects/*/agent-notes/
!projects/*/agent-notes/**
!projects/*/assets/
!projects/*/assets/**
!skills/
!skills/**
!skills-agent-desktop/
!skills-agent-desktop/**
!rules/
!rules/**
`

export function getConfigHomeDir(): string {
  return join(homedir(), '.agent-desktop')
}

export function getProjectsDir(): string {
  return join(getConfigHomeDir(), 'projects')
}

export function getAiTrackingDir(): string {
  return join(getConfigHomeDir(), 'ai-tracking')
}

export function getAiTrackingDbPath(): string {
  return join(getAiTrackingDir(), 'ai-code-tracking.db')
}

export function getUserStatePath(name: string): string {
  return join(getConfigHomeDir(), name)
}

function djb2Hash(str: string): number {
  let hash = 5381
  for (let i = 0; i < str.length; i++) {
    hash = (hash * 33) ^ str.charCodeAt(i)
  }
  return hash
}

function simpleHash(str: string): string {
  return Math.abs(djb2Hash(str)).toString(36)
}

/** Claude Code / Cursor — non-alphanumeric → hyphen, truncate + hash suffix. */
export function sanitizePath(name: string): string {
  const sanitized = name.replace(/[^a-zA-Z0-9]/g, '-')
  if (sanitized.length <= MAX_SANITIZED_LENGTH) {
    return sanitized
  }
  return `${sanitized.slice(0, MAX_SANITIZED_LENGTH)}-${simpleHash(name)}`
}

export function getProjectDir(projectPath: string): string {
  return join(getProjectsDir(), sanitizePath(projectPath))
}

const PROJECT_SUBDIRS = [
  'agent-transcripts',
  'agent-tools',
  'terminals',
  'assets',
  'mcps',
  'canvases',
  'agent-notes',
  'sessions',
] as const

export function ensureHomeLayout(): void {
  const home = getConfigHomeDir()
  mkdirSync(home, { recursive: true })
  mkdirSync(getProjectsDir(), { recursive: true })
  mkdirSync(getAiTrackingDir(), { recursive: true })
  mkdirSync(join(home, 'skills'), { recursive: true })
  mkdirSync(join(home, 'skills-agent-desktop'), { recursive: true })
  mkdirSync(join(home, 'rules'), { recursive: true })
  mkdirSync(join(home, 'logs'), { recursive: true })

  const gitignorePath = join(home, '.gitignore')
  if (!existsSync(gitignorePath)) {
    writeFileSync(gitignorePath, HOME_GITIGNORE, 'utf8')
  }
}

export function ensureProjectLayout(workspaceRoot: string): string {
  ensureHomeLayout()
  const projectDir = getProjectDir(workspaceRoot)
  mkdirSync(projectDir, { recursive: true })
  for (const sub of PROJECT_SUBDIRS) {
    mkdirSync(join(projectDir, sub), { recursive: true })
  }
  return projectDir
}
