import { ipcRenderer } from 'electron'

export interface DirectoryEntry {
  name: string
  path: string
  kind: 'file' | 'directory'
}

export type TextLoadPolicy = 'normal' | 'large' | 'confirm-required' | 'blocked'

export interface FileInfo {
  path: string
  size: number
  viewKind: 'text' | 'image' | 'pdf' | 'binary'
  mimeType: string
  textLoadPolicy?: TextLoadPolicy
}

export interface FileDataUrl {
  mimeType: string
  size: number
  dataUrl: string
}

export interface FileBase64Payload {
  mimeType: string
  size: number
  base64: string
}

export interface ReadFileOptions {
  maxBytes?: number
  optional?: boolean
}

export interface GrepMatch {
  file: string
  line: number
  column: number
  text: string
}

export interface GrepRequest {
  rootPath: string
  pattern: string
  caseInsensitive?: boolean
  wholeWord?: boolean
  glob?: string
  headLimit?: number
}

export interface GrepResult {
  matches: GrepMatch[]
  truncated: boolean
  error?: string
  engine?: 'ripgrep' | 'fallback'
}

export interface WorkspaceReplaceRequest {
  rootPath: string
  pattern: string
  replacement: string
  caseInsensitive?: boolean
  wholeWord?: boolean
  glob?: string
}

export interface WorkspaceReplaceResult {
  fileCount: number
  replacementCount: number
  truncated: boolean
  error?: string
}

export interface GitRemote {
  name: string
  fetchUrl: string
  pushUrl?: string
}

export interface GitChange {
  status: string
  path: string
  kind: 'file' | 'directory'
  staged: boolean
  unstaged: boolean
}

export interface GitSyncStatus {
  trackingBranch?: string
  ahead: number
  behind: number
}

export interface GitInfo {
  isRepo: boolean
  gitRoot?: string
  branch?: string
  remotes: GitRemote[]
  changes: GitChange[]
  syncStatus?: GitSyncStatus
  error?: string
}

export type GitInfoLoadScope = 'full' | 'changes' | 'sync'

export interface GitFileBaseline {
  status: string | null
  baseline: string | null
}

export type GitDiscardScope = 'head' | 'worktree'

export interface GitOpResult {
  ok: boolean
  stdout: string
  stderr: string
  error?: string
}

export interface GitLogEntry {
  hash: string
  shortHash: string
  subject: string
  author: string
  authorEmail: string
  date: number
  refs: string
}

export interface GitLogResult {
  entries: GitLogEntry[]
  hasMore: boolean
  error?: string
}

export interface GitLogRequest {
  limit?: number
  skip?: number
}

export interface WorkspacePathMatch {
  name: string
  path: string
  relativePath: string
  kind: 'file' | 'directory'
}

export interface WorkspaceDiagnosticsResult {
  problems: Array<{
    path: string
    line: number
    column: number
    endLine: number
    endColumn: number
    message: string
    severity: 'error' | 'warning' | 'info'
    source?: string
    code?: string
  }>
  engines: string[]
  error?: string
  truncated?: boolean
}

export const workspaceApi = {
  openDirectory: (): Promise<string | null> => ipcRenderer.invoke('workspace:openDirectory'),
  openChatFiles: (): Promise<string[]> => ipcRenderer.invoke('workspace:openChatFiles'),
  inspectPdf: (filePath: string): Promise<import('../../main/utils/workspacePdf').PdfInfoDto> =>
    ipcRenderer.invoke('workspace:inspectPdf', filePath),
  readDirectory: (dirPath: string): Promise<DirectoryEntry[]> =>
    ipcRenderer.invoke('workspace:readDirectory', dirPath),
  searchFiles: (rootPath: string, query: string, limit?: number): Promise<WorkspacePathMatch[]> =>
    ipcRenderer.invoke('workspace:searchFiles', rootPath, query, limit),
  getFileInfo: (filePath: string): Promise<FileInfo> =>
    ipcRenderer.invoke('workspace:getFileInfo', filePath),
  getPathKind: (filePath: string): Promise<'file' | 'directory' | 'missing'> =>
    ipcRenderer.invoke('workspace:getPathKind', filePath),
  readFile: (filePath: string, options?: ReadFileOptions): Promise<string | null> =>
    ipcRenderer.invoke('workspace:readFile', filePath, options),
  readFileDataUrl: (filePath: string): Promise<FileDataUrl> =>
    ipcRenderer.invoke('workspace:readFileDataUrl', filePath),
  readFileBase64: (filePath: string, options?: ReadFileOptions): Promise<FileBase64Payload> =>
    ipcRenderer.invoke('workspace:readFileBase64', filePath, options),
  writeFile: (filePath: string, content: string): Promise<void> =>
    ipcRenderer.invoke('workspace:writeFile', filePath, content),
  writeFilesBatch: (entries: { path: string; content: string }[]): Promise<{ ok: true; written: number }> =>
    ipcRenderer.invoke('workspace:writeFilesBatch', entries),
  deleteFile: (filePath: string): Promise<void> => ipcRenderer.invoke('workspace:deleteFile', filePath),
  deleteDirectory: (dirPath: string): Promise<void> => ipcRenderer.invoke('workspace:deleteDirectory', dirPath),
  deleteFilesBatch: (filePaths: string[]): Promise<{ ok: true; deleted: number }> =>
    ipcRenderer.invoke('workspace:deleteFilesBatch', filePaths),
  openInSystem: (filePath: string): Promise<void> => ipcRenderer.invoke('workspace:openInSystem', filePath),
  grep: (request: GrepRequest): Promise<GrepResult> => ipcRenderer.invoke('workspace:grep', request),
  scanDiagnostics: (rootPath: string): Promise<WorkspaceDiagnosticsResult> =>
    ipcRenderer.invoke('workspace:scanDiagnostics', rootPath),
  replaceInFiles: (request: WorkspaceReplaceRequest): Promise<WorkspaceReplaceResult> =>
    ipcRenderer.invoke('workspace:replaceInFiles', request),
  gitInfo: (rootPath: string, scope?: GitInfoLoadScope): Promise<GitInfo> =>
    ipcRenderer.invoke('workspace:gitInfo', rootPath, scope),
  gitStage: (rootPath: string, absPaths: string[]): Promise<GitOpResult> =>
    ipcRenderer.invoke('workspace:gitStage', rootPath, absPaths),
  gitStageAll: (rootPath: string): Promise<GitOpResult> =>
    ipcRenderer.invoke('workspace:gitStageAll', rootPath),
  gitUnstage: (rootPath: string, absPaths: string[]): Promise<GitOpResult> =>
    ipcRenderer.invoke('workspace:gitUnstage', rootPath, absPaths),
  gitUnstageAll: (rootPath: string): Promise<GitOpResult> =>
    ipcRenderer.invoke('workspace:gitUnstageAll', rootPath),
  gitCommit: (rootPath: string, message: string): Promise<GitOpResult> =>
    ipcRenderer.invoke('workspace:gitCommit', rootPath, message),
  gitFetch: (rootPath: string, remote?: string): Promise<GitOpResult> =>
    ipcRenderer.invoke('workspace:gitFetch', rootPath, remote),
  gitPull: (rootPath: string, remote?: string, branch?: string): Promise<GitOpResult> =>
    ipcRenderer.invoke('workspace:gitPull', rootPath, remote, branch),
  gitPush: (rootPath: string, remote?: string, branch?: string): Promise<GitOpResult> =>
    ipcRenderer.invoke('workspace:gitPush', rootPath, remote, branch),
  gitDiscard: (rootPath: string, absPaths: string[], scope?: GitDiscardScope): Promise<GitOpResult> =>
    ipcRenderer.invoke('workspace:gitDiscard', rootPath, absPaths, scope),
  gitFileBaseline: (rootPath: string, filePath: string): Promise<GitFileBaseline> =>
    ipcRenderer.invoke('workspace:gitFileBaseline', rootPath, filePath),
  gitCheckIgnored: (rootPath: string, filePath: string): Promise<boolean> =>
    ipcRenderer.invoke('workspace:gitCheckIgnored', rootPath, filePath),
  gitCheckIgnoredBatch: (rootPath: string, filePaths: string[]): Promise<Record<string, boolean>> =>
    ipcRenderer.invoke('workspace:gitCheckIgnoredBatch', rootPath, filePaths),
  gitLog: (rootPath: string, request?: GitLogRequest): Promise<GitLogResult> =>
    ipcRenderer.invoke('workspace:gitLog', rootPath, request),
  createFile: (filePath: string): Promise<string> => ipcRenderer.invoke('workspace:createFile', filePath),
  createDirectory: (dirPath: string): Promise<string> =>
    ipcRenderer.invoke('workspace:createDirectory', dirPath),
  rename: (oldPath: string, newPath: string): Promise<string> =>
    ipcRenderer.invoke('workspace:rename', oldPath, newPath),
}

export type WorkspaceApi = typeof workspaceApi
