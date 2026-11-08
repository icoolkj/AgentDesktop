import { readFile, writeFile, stat, mkdir, rename, readdir, unlink, rm } from 'node:fs/promises'
import { ipcMain, dialog, BrowserWindow, shell } from 'electron'
import { join } from 'node:path'

import { directoryHasVisibleChildren, shouldSkipExplorerEntry } from '../utils/explorerDirs'

import { mimeTypeFromPath, viewKindFromPath } from '../utils/fileKind'
import { loadGitInfo, type GitInfoDto, type GitInfoLoadScope } from '../utils/gitInfo'
import {
  gitCommit,
  gitDiscard,
  gitFetch,
  gitPull,
  gitPush,
  gitStage,
  gitStageAll,
  gitUnstage,
  gitUnstageAll,
} from '../utils/gitOperations'
import type { GitOpResult } from '../utils/gitRunner'
import { loadGitFileBaseline, type GitFileBaselineDto } from '../utils/gitFileBaseline'
import { loadGitLog, type GitLogResultDto } from '../utils/gitLog'
import { isGitIgnored, batchGitIgnored } from '../utils/gitIgnore'
import { runWorkspaceGrep, type GrepRequestDto, type GrepResultDto } from '../utils/ripgrep'
import {
  runWorkspaceReplace,
  type WorkspaceReplaceRequest,
  type WorkspaceReplaceResult,
} from '../utils/workspaceReplace'
import {
  FileSizeError,
  IMAGE_MAX_BYTES,
  PDF_MAX_BYTES,
  TEXT_MAX_BYTES,
  textLoadPolicy,
  type TextLoadPolicy,
} from '../utils/fileSizeLimits'
import { searchWorkspacePaths } from '../utils/workspaceFileSearch'
import { inspectPdfFile, type PdfInfoDto } from '../utils/workspacePdf'
import {
  runWorkspaceDiagnostics,
  type WorkspaceDiagnosticsResultDto,
} from '../utils/workspaceDiagnostics'

export interface DirectoryEntryDto {
  name: string
  path: string
  kind: 'file' | 'directory'
}

export interface FileInfoDto {
  path: string
  size: number
  viewKind: 'text' | 'image' | 'pdf' | 'binary'
  mimeType: string
  textLoadPolicy?: TextLoadPolicy
}

export interface FileDataUrlDto {
  mimeType: string
  size: number
  dataUrl: string
}

export interface FileBase64Dto {
  mimeType: string
  size: number
  base64: string
}

export interface ReadFileOptions {
  maxBytes?: number
  /** Missing file → null, no IPC error (Claude Code CLAUDE.md ENOENT parity). */
  optional?: boolean
}

export function setupWorkspaceIpcHandlers(): void {
  ipcMain.handle('workspace:openDirectory', async () => {
    const win = BrowserWindow.getFocusedWindow() ?? BrowserWindow.getAllWindows()[0]
    const result = await dialog.showOpenDialog(win!, {
      properties: ['openDirectory'],
    })
    if (result.canceled || result.filePaths.length === 0) return null
    return result.filePaths[0]!
  })

  ipcMain.handle('workspace:inspectPdf', async (_, filePath: string): Promise<PdfInfoDto> => {
    if (!filePath || typeof filePath !== 'string') {
      throw new Error('Invalid file path')
    }
    return inspectPdfFile(filePath)
  })

  ipcMain.handle('workspace:openChatFiles', async () => {
    const win = BrowserWindow.getFocusedWindow() ?? BrowserWindow.getAllWindows()[0]
    const result = await dialog.showOpenDialog(win!, {
      properties: ['openFile', 'multiSelections'],
      filters: [
        { name: 'Images (Claude Code)', extensions: ['png', 'jpg', 'jpeg', 'gif', 'webp'] },
        { name: 'PDF', extensions: ['pdf'] },
        {
          name: 'Text files',
          extensions: [
            'txt',
            'md',
            'json',
            'ts',
            'tsx',
            'js',
            'jsx',
            'java',
            'xml',
            'yaml',
            'yml',
            'css',
            'html',
            'vue',
            'py',
            'go',
            'rs',
            'sql',
            'sh',
            'bat',
            'properties',
          ],
        },
        { name: 'All Files', extensions: ['*'] },
      ],
    })
    if (result.canceled) return []
    return result.filePaths
  })

  ipcMain.handle('workspace:readDirectory', async (_, dirPath: string) => {
    if (!dirPath || typeof dirPath !== 'string') return []

    const entries = await readdir(dirPath, { withFileTypes: true })
    const items: DirectoryEntryDto[] = []

    for (const entry of entries) {
      if (shouldSkipExplorerEntry(entry.name, entry.isDirectory())) continue

      if (entry.isDirectory()) {
        const childPath = join(dirPath, entry.name)
        if (!(await directoryHasVisibleChildren(childPath))) continue
      }

      items.push({
        name: entry.name,
        path: join(dirPath, entry.name),
        kind: entry.isDirectory() ? 'directory' : 'file',
      })
    }

    items.sort((a, b) => {
      if (a.kind !== b.kind) return a.kind === 'directory' ? -1 : 1
      return a.name.localeCompare(b.name)
    })

    return items
  })

  ipcMain.handle(
    'workspace:searchFiles',
    async (_, rootPath: string, query: string, limit?: number) => {
      if (!rootPath || typeof rootPath !== 'string') return []
      if (!query || typeof query !== 'string') return []
      return searchWorkspacePaths(rootPath, query, typeof limit === 'number' ? limit : 20)
    },
  )

  ipcMain.handle('workspace:getFileInfo', async (_, filePath: string): Promise<FileInfoDto> => {
    if (!filePath || typeof filePath !== 'string') {
      throw new Error('Invalid file path')
    }

    const info = await stat(filePath)
    if (!info.isFile()) {
      throw new Error('Not a file')
    }

    const viewKind = viewKindFromPath(filePath)

    return {
      path: filePath,
      size: info.size,
      viewKind,
      mimeType: mimeTypeFromPath(filePath),
      textLoadPolicy: viewKind === 'text' ? textLoadPolicy(info.size) : undefined,
    }
  })

  ipcMain.handle(
    'workspace:getPathKind',
    async (_, filePath: string): Promise<'file' | 'directory' | 'missing'> => {
      if (!filePath || typeof filePath !== 'string') return 'missing'
      try {
        const info = await stat(filePath)
        if (info.isDirectory()) return 'directory'
        if (info.isFile()) return 'file'
        return 'missing'
      } catch {
        return 'missing'
      }
    },
  )

  ipcMain.handle(
    'workspace:readFile',
    async (_, filePath: string, options?: ReadFileOptions): Promise<string | null> => {
      if (!filePath || typeof filePath !== 'string') {
        throw new Error('Invalid file path')
      }

      const maxBytes = options?.maxBytes ?? TEXT_MAX_BYTES
      let info
      try {
        info = await stat(filePath)
      } catch (err: unknown) {
        const code = (err as NodeJS.ErrnoException)?.code
        if (options?.optional && code === 'ENOENT') {
          return null
        }
        throw err
      }
      if (!info.isFile()) {
        if (options?.optional) return null
        throw new Error('Not a file')
      }
      if (info.size > maxBytes) {
        throw new FileSizeError(
          `File exceeds ${maxBytes / 1024 / 1024}MB limit`,
          'TEXT_TOO_LARGE',
          info.size,
          maxBytes,
        )
      }

      return readFile(filePath, 'utf-8')
    },
  )

  ipcMain.handle('workspace:readFileDataUrl', async (_, filePath: string): Promise<FileDataUrlDto> => {
    if (!filePath || typeof filePath !== 'string') {
      throw new Error('Invalid file path')
    }

    const info = await stat(filePath)
    if (!info.isFile()) {
      throw new Error('Not a file')
    }
    if (info.size > IMAGE_MAX_BYTES) {
      throw new FileSizeError(
        `Image exceeds ${IMAGE_MAX_BYTES / 1024 / 1024}MB preview limit`,
        'IMAGE_TOO_LARGE',
        info.size,
        IMAGE_MAX_BYTES,
      )
    }

    const mimeType = mimeTypeFromPath(filePath)
    const buffer = await readFile(filePath)
    return {
      mimeType,
      size: info.size,
      dataUrl: `data:${mimeType};base64,${buffer.toString('base64')}`,
    }
  })

  ipcMain.handle(
    'workspace:readFileBase64',
    async (_, filePath: string, options?: ReadFileOptions): Promise<FileBase64Dto> => {
      if (!filePath || typeof filePath !== 'string') {
        throw new Error('Invalid file path')
      }

      const maxBytes = options?.maxBytes ?? PDF_MAX_BYTES
      const info = await stat(filePath)
      if (!info.isFile()) {
        throw new Error('Not a file')
      }
      if (info.size > maxBytes) {
        throw new FileSizeError(
          `File exceeds ${maxBytes / 1024 / 1024}MB limit`,
          'PDF_TOO_LARGE',
          info.size,
          maxBytes,
        )
      }

      const mimeType = mimeTypeFromPath(filePath)
      const buffer = await readFile(filePath)
      return {
        mimeType,
        size: info.size,
        base64: buffer.toString('base64'),
      }
    },
  )

  ipcMain.handle('workspace:writeFile', async (_, filePath: string, content: string) => {
    if (!filePath || typeof filePath !== 'string') {
      throw new Error('Invalid file path')
    }
    if (typeof content !== 'string') {
      throw new Error('Invalid file content')
    }

    await writeFile(filePath, content, 'utf-8')
  })

  ipcMain.handle(
    'workspace:writeFilesBatch',
    async (_, entries: { path: string; content: string }[]) => {
      if (!Array.isArray(entries)) {
        throw new Error('Invalid batch payload')
      }
      for (const entry of entries) {
        if (!entry?.path || typeof entry.path !== 'string') {
          throw new Error('Invalid file path in batch')
        }
        if (typeof entry.content !== 'string') {
          throw new Error('Invalid file content in batch')
        }
        await writeFile(entry.path, entry.content, 'utf-8')
      }
      return { ok: true, written: entries.length }
    },
  )

  ipcMain.handle('workspace:deleteFile', async (_, filePath: string) => {
    if (!filePath || typeof filePath !== 'string') {
      throw new Error('Invalid file path')
    }
    await unlink(filePath)
  })

  ipcMain.handle('workspace:deleteDirectory', async (_, dirPath: string) => {
    if (!dirPath || typeof dirPath !== 'string') {
      throw new Error('Invalid directory path')
    }
    const info = await stat(dirPath)
    if (!info.isDirectory()) {
      throw new Error('Not a directory')
    }
    await rm(dirPath, { recursive: true, force: true })
  })

  ipcMain.handle('workspace:deleteFilesBatch', async (_, filePaths: string[]) => {
    if (!Array.isArray(filePaths)) {
      throw new Error('Invalid batch payload')
    }
    for (const filePath of filePaths) {
      if (!filePath || typeof filePath !== 'string') {
        throw new Error('Invalid file path in batch')
      }
      await unlink(filePath)
    }
    return { ok: true, deleted: filePaths.length }
  })

  ipcMain.handle('workspace:openInSystem', async (_, filePath: string) => {
    if (!filePath || typeof filePath !== 'string') {
      throw new Error('Invalid file path')
    }

    const result = await shell.openPath(filePath)
    if (result) {
      throw new Error(result)
    }
  })

  ipcMain.handle('workspace:grep', async (_, request: GrepRequestDto): Promise<GrepResultDto> => {
    if (!request?.rootPath || typeof request.rootPath !== 'string') {
      throw new Error('Invalid workspace root')
    }
    if (!request.pattern || typeof request.pattern !== 'string') {
      throw new Error('Invalid search pattern')
    }
    return runWorkspaceGrep(request)
  })

  ipcMain.handle(
    'workspace:scanDiagnostics',
    async (_, rootPath: string): Promise<WorkspaceDiagnosticsResultDto> => {
      if (!rootPath || typeof rootPath !== 'string') {
        throw new Error('Invalid workspace root')
      }
      return runWorkspaceDiagnostics(rootPath)
    },
  )

  ipcMain.handle(
    'workspace:replaceInFiles',
    async (_, request: WorkspaceReplaceRequest): Promise<WorkspaceReplaceResult> => {
      if (!request?.rootPath || typeof request.rootPath !== 'string') {
        throw new Error('Invalid workspace root')
      }
      if (!request.pattern || typeof request.pattern !== 'string') {
        throw new Error('Invalid search pattern')
      }
      if (typeof request.replacement !== 'string') {
        throw new Error('Invalid replacement text')
      }
      return runWorkspaceReplace(request)
    },
  )

  ipcMain.handle(
    'workspace:gitInfo',
    async (_, rootPath: string, scope?: GitInfoLoadScope): Promise<GitInfoDto> => {
      if (!rootPath || typeof rootPath !== 'string') {
        throw new Error('Invalid workspace root')
      }
      const loadScope: GitInfoLoadScope =
        scope === 'changes' || scope === 'sync' || scope === 'full' ? scope : 'full'
      return loadGitInfo(rootPath, loadScope)
    },
  )

  ipcMain.handle(
    'workspace:gitStage',
    async (_, rootPath: string, absPaths: string[]): Promise<GitOpResult> => {
      if (!rootPath || typeof rootPath !== 'string') return { ok: false, stdout: '', stderr: '', error: '无效的工作区' }
      return gitStage(rootPath, Array.isArray(absPaths) ? absPaths : [])
    },
  )

  ipcMain.handle('workspace:gitStageAll', async (_, rootPath: string): Promise<GitOpResult> => {
    if (!rootPath || typeof rootPath !== 'string') return { ok: false, stdout: '', stderr: '', error: '无效的工作区' }
    return gitStageAll(rootPath)
  })

  ipcMain.handle(
    'workspace:gitUnstage',
    async (_, rootPath: string, absPaths: string[]): Promise<GitOpResult> => {
      if (!rootPath || typeof rootPath !== 'string') return { ok: false, stdout: '', stderr: '', error: '无效的工作区' }
      return gitUnstage(rootPath, Array.isArray(absPaths) ? absPaths : [])
    },
  )

  ipcMain.handle('workspace:gitUnstageAll', async (_, rootPath: string): Promise<GitOpResult> => {
    if (!rootPath || typeof rootPath !== 'string') return { ok: false, stdout: '', stderr: '', error: '无效的工作区' }
    return gitUnstageAll(rootPath)
  })

  ipcMain.handle(
    'workspace:gitCommit',
    async (_, rootPath: string, message: string): Promise<GitOpResult> => {
      if (!rootPath || typeof rootPath !== 'string') return { ok: false, stdout: '', stderr: '', error: '无效的工作区' }
      return gitCommit(rootPath, typeof message === 'string' ? message : '')
    },
  )

  ipcMain.handle(
    'workspace:gitFetch',
    async (_, rootPath: string, remote?: string): Promise<GitOpResult> => {
      if (!rootPath || typeof rootPath !== 'string') return { ok: false, stdout: '', stderr: '', error: '无效的工作区' }
      return gitFetch(rootPath, remote)
    },
  )

  ipcMain.handle(
    'workspace:gitPull',
    async (_, rootPath: string, remote?: string, branch?: string): Promise<GitOpResult> => {
      if (!rootPath || typeof rootPath !== 'string') return { ok: false, stdout: '', stderr: '', error: '无效的工作区' }
      return gitPull(rootPath, remote, branch)
    },
  )

  ipcMain.handle(
    'workspace:gitPush',
    async (_, rootPath: string, remote?: string, branch?: string): Promise<GitOpResult> => {
      if (!rootPath || typeof rootPath !== 'string') return { ok: false, stdout: '', stderr: '', error: '无效的工作区' }
      return gitPush(rootPath, remote, branch)
    },
  )

  ipcMain.handle(
    'workspace:gitDiscard',
    async (_, rootPath: string, absPaths: string[], scope?: 'head' | 'worktree'): Promise<GitOpResult> => {
      if (!rootPath || typeof rootPath !== 'string') return { ok: false, stdout: '', stderr: '', error: '无效的工作区' }
      return gitDiscard(rootPath, Array.isArray(absPaths) ? absPaths : [], scope === 'worktree' ? 'worktree' : 'head')
    },
  )

  ipcMain.handle(
    'workspace:gitFileBaseline',
    async (_, rootPath: string, filePath: string): Promise<GitFileBaselineDto> => {
      if (!rootPath || typeof rootPath !== 'string' || !filePath || typeof filePath !== 'string') {
        return { status: null, baseline: null }
      }
      if (await isGitIgnored(rootPath, filePath)) {
        return { status: null, baseline: null }
      }
      return loadGitFileBaseline(rootPath, filePath)
    },
  )

  ipcMain.handle(
    'workspace:gitCheckIgnored',
    async (_, rootPath: string, filePath: string): Promise<boolean> => {
      if (!rootPath || typeof rootPath !== 'string' || !filePath || typeof filePath !== 'string') {
        return false
      }
      return isGitIgnored(rootPath, filePath)
    },
  )

  ipcMain.handle(
    'workspace:gitCheckIgnoredBatch',
    async (_, rootPath: string, filePaths: string[]): Promise<Record<string, boolean>> => {
      if (!rootPath || typeof rootPath !== 'string' || !Array.isArray(filePaths)) {
        return {}
      }
      return batchGitIgnored(rootPath, filePaths)
    },
  )

  ipcMain.handle(
    'workspace:gitLog',
    async (_, rootPath: string, request?: { limit?: number; skip?: number }): Promise<GitLogResultDto> => {
      if (!rootPath || typeof rootPath !== 'string') {
        return { entries: [], hasMore: false, error: '无效的工作区' }
      }
      return loadGitLog(rootPath, request)
    },
  )

  ipcMain.handle('workspace:createFile', async (_, filePath: string): Promise<string> => {
    if (!filePath || typeof filePath !== 'string') {
      throw new Error('无效的文件路径')
    }
    try {
      await stat(filePath)
      throw new Error('文件已存在')
    } catch (err) {
      if (err instanceof Error && err.message === '文件已存在') throw err
    }
    await writeFile(filePath, '', 'utf-8')
    return filePath
  })

  ipcMain.handle('workspace:createDirectory', async (_, dirPath: string): Promise<string> => {
    if (!dirPath || typeof dirPath !== 'string') {
      throw new Error('无效的文件夹路径')
    }
    await mkdir(dirPath, { recursive: true })
    return dirPath
  })

  ipcMain.handle('workspace:rename', async (_, oldPath: string, newPath: string): Promise<string> => {
    if (!oldPath || typeof oldPath !== 'string' || !newPath || typeof newPath !== 'string') {
      throw new Error('无效的路径')
    }
    if (oldPath === newPath) return newPath

    try {
      await stat(newPath)
      throw new Error('目标已存在')
    } catch (err) {
      if (err instanceof Error && err.message === '目标已存在') throw err
    }

    await rename(oldPath, newPath)
    return newPath
  })
}
