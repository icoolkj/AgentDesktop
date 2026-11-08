import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import type { GitChange, GitDiscardScope, GitInfo, GitLogEntry } from '../../../preload/api/workspaceApi'
import {
  badgeForKind,
  colorClassForKind,
  decorationKindFromStatus,
  type GitDecorationKind,
  type GitTreeDecoration,
} from '@renderer/utils/gitTreeDecorations'
import { isLikelyIgnoredPath } from '@renderer/utils/gitPathHeuristics'
import { sortFileChanges, type GitPanelView } from '@renderer/utils/gitSidebarUtils'
import type { GitInfoLoadScope } from '../../../preload/api/workspaceApi'
import { useToastStore } from './toastStore'
import { useWorkspaceStore } from './workspaceStore'

const BATCH_DELAY_MS = 80
const BATCH_SIZE = 200
const LOG_PAGE_SIZE = 50
const REFRESH_DEBOUNCE_MS = 80

export type GitRefreshScope = GitInfoLoadScope

export const useGitStore = defineStore('git', () => {
  const info = ref<GitInfo | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const operating = ref(false)
  const operatingLabel = ref<string | null>(null)
  const activeView = ref<GitPanelView>('changes')
  const commitMessage = ref('')
  const baselineCache = ref<Record<string, string | null>>({})
  const baselineTick = ref(0)
  const ignoreCache = ref<Record<string, boolean>>({})
  const ignoreTick = ref(0)
  const logEntries = ref<GitLogEntry[]>([])
  const logLoading = ref(false)
  const logError = ref<string | null>(null)
  const logHasMore = ref(false)

  let logSkip = 0
  let refreshTimer: ReturnType<typeof setTimeout> | null = null
  let pendingRefreshScope: GitRefreshScope = 'changes'
  let refreshSeq = 0

  let pendingIgnorePaths: string[] = []
  let ignoreBatchTimer: ReturnType<typeof setTimeout> | null = null
  let ignoreBatchRunning = false

  const isRepo = computed(() => info.value?.isRepo ?? false)

  const stagedChanges = computed(() => {
    if (!info.value?.changes.length) return []
    return sortFileChanges(info.value.changes.filter(change => change.staged))
  })

  const unstagedChanges = computed(() => {
    if (!info.value?.changes.length) return []
    return sortFileChanges(info.value.changes.filter(change => change.unstaged))
  })

  const totalChangeCount = computed(() => stagedChanges.value.length + unstagedChanges.value.length)

  function normalizePath(path: string): string {
    return path.replace(/\\/g, '/').toLowerCase()
  }

  function normalizeDir(path: string): string {
    return normalizePath(path).replace(/\/+$/, '')
  }

  function joinRoot(relPath: string): string | null {
    const root = info.value?.gitRoot ?? useWorkspaceStore().rootPath
    if (!root) return null
    const sep = root.includes('\\') ? '\\' : '/'
    const cleaned = relPath.replace(/\/+$/, '')
    return `${root}${root.endsWith(sep) ? '' : sep}${cleaned.replace(/\//g, sep)}`
  }

  const changeByAbsPath = computed(() => {
    const map = new Map<string, GitChange>()
    if (!info.value?.isRepo) return map
    for (const change of info.value.changes) {
      const full = joinRoot(change.path)
      if (full) {
        map.set(normalizeDir(full), change)
      }
    }
    return map
  })

  function pathsRelated(dirNorm: string, changeNorm: string): boolean {
    if (dirNorm === changeNorm) return true
    const dirPrefix = `${dirNorm}/`
    const changePrefix = `${changeNorm}/`
    return changeNorm.startsWith(dirPrefix) || dirNorm.startsWith(changePrefix)
  }

  function markIgnored(paths: string[]): void {
    if (!paths.length) return
    const next = { ...ignoreCache.value }
    let changed = false
    for (const path of paths) {
      const key = normalizeDir(path)
      if (next[key] !== true) {
        next[key] = true
        changed = true
      }
    }
    if (changed) {
      ignoreCache.value = next
      ignoreTick.value++
    }
  }

  function markIgnoreResults(results: Record<string, boolean>): void {
    const next = { ...ignoreCache.value }
    let changed = false
    for (const [path, ignored] of Object.entries(results)) {
      const key = normalizeDir(path)
      if (next[key] === ignored) continue
      next[key] = ignored
      changed = true
    }
    if (changed) {
      ignoreCache.value = next
      ignoreTick.value++
    }
  }

  function isPathIgnoredSync(absolutePath: string): boolean {
    // 读取 tick 以建立响应式依赖
    // eslint-disable-next-line @typescript-eslint/no-unused-expressions
    ignoreTick.value
    const key = normalizeDir(absolutePath)
    if (ignoreCache.value[key] === true) return true
    return isLikelyIgnoredPath(absolutePath)
  }

  function queueIgnoreScan(paths: string[]): void {
    if (!paths.length) return

    const heuristicIgnored: string[] = []
    const toQueue: string[] = []

    for (const path of paths) {
      if (isLikelyIgnoredPath(path)) {
        heuristicIgnored.push(path)
        continue
      }
      const key = normalizeDir(path)
      if (Object.prototype.hasOwnProperty.call(ignoreCache.value, key)) continue
      toQueue.push(path)
    }

    markIgnored(heuristicIgnored)
    if (!toQueue.length) return

    pendingIgnorePaths.push(...toQueue)
    if (ignoreBatchTimer) clearTimeout(ignoreBatchTimer)
    ignoreBatchTimer = setTimeout(() => {
      ignoreBatchTimer = null
      void flushIgnoreScan()
    }, BATCH_DELAY_MS)
  }

  async function flushIgnoreScan(): Promise<void> {
    if (ignoreBatchRunning) return
    ignoreBatchRunning = true

    try {
      while (pendingIgnorePaths.length > 0) {
        const chunk = pendingIgnorePaths.splice(0, BATCH_SIZE)
        const root = useWorkspaceStore().rootPath
        if (!root) break

        const unknown = chunk.filter(
          path => !Object.prototype.hasOwnProperty.call(ignoreCache.value, normalizeDir(path)),
        )
        if (!unknown.length) continue

        const results = await window.workspaceApi.gitCheckIgnoredBatch(root, unknown)
        markIgnoreResults(results)

        for (const path of unknown) {
          if (Object.prototype.hasOwnProperty.call(results, path)) continue
          markIgnoreResults({ [path]: false })
        }
      }
    } finally {
      ignoreBatchRunning = false
      if (pendingIgnorePaths.length > 0) {
        void flushIgnoreScan()
      }
    }
  }

  function findChangeForPath(absolutePath: string): GitChange | null {
    if (isPathIgnoredSync(absolutePath)) return null
    const norm = normalizeDir(absolutePath)
    const direct = changeByAbsPath.value.get(norm)
    if (direct) return direct

    let best: GitChange | null = null
    let bestLen = -1
    for (const [changePath, change] of changeByAbsPath.value) {
      if (!pathsRelated(norm, changePath)) continue
      if (changePath.length > bestLen) {
        best = change
        bestLen = changePath.length
      }
    }
    return best
  }

  function hasChangeUnder(dirPath: string): boolean {
    if (isPathIgnoredSync(dirPath)) return false
    const dirNorm = normalizeDir(dirPath)
    for (const [changePath] of changeByAbsPath.value) {
      if (pathsRelated(dirNorm, changePath)) return true
    }
    return false
  }

  function dirDotKind(dirPath: string): GitDecorationKind | null {
    if (!hasChangeUnder(dirPath)) return null

    const dirNorm = normalizeDir(dirPath)
    let hasModified = false
    let hasUntracked = false

    for (const [changePath, change] of changeByAbsPath.value) {
      if (!pathsRelated(dirNorm, changePath)) continue
      const kind = decorationKindFromStatus(change.status)
      if (kind === 'untracked' || kind === 'added') hasUntracked = true
      else hasModified = true
    }

    if (hasUntracked && !hasModified) return 'untracked'
    return 'modified'
  }

  function decorationForPath(absolutePath: string, kind: 'file' | 'directory'): GitTreeDecoration | null {
    if (!isRepo.value) return null
    if (isPathIgnoredSync(absolutePath)) return null

    if (kind === 'file') {
      const change = findChangeForPath(absolutePath)
      if (!change) return null
      const decoKind = decorationKindFromStatus(change.status)
      return {
        kind: decoKind,
        badge: badgeForKind(decoKind),
        dirDot: null,
        colorClass: colorClassForKind(decoKind),
      }
    }

    const dot = dirDotKind(absolutePath)
    if (!dot) return null
    return {
      kind: dot,
      badge: null,
      dirDot: dot,
      colorClass: colorClassForKind(dot),
    }
  }

  function workspaceRoot(): string | null {
    return useWorkspaceStore().rootPath
  }

  async function refresh(scope: GitRefreshScope = 'full'): Promise<void> {
    if (refreshTimer) {
      clearTimeout(refreshTimer)
      refreshTimer = null
      pendingRefreshScope = scope === 'full' ? 'full' : pendingRefreshScope
    }
    await refreshInternal(scope)
  }

  function scheduleRefresh(scope: GitRefreshScope = 'changes'): void {
    if (scope === 'full') {
      pendingRefreshScope = 'full'
    } else if (scope === 'sync' && pendingRefreshScope === 'changes') {
      pendingRefreshScope = 'sync'
    }

    if (refreshTimer) clearTimeout(refreshTimer)
    refreshTimer = setTimeout(() => {
      refreshTimer = null
      const nextScope = pendingRefreshScope
      pendingRefreshScope = 'changes'
      void refreshInternal(nextScope)
    }, REFRESH_DEBOUNCE_MS)
  }

  async function refreshInternal(scope: GitRefreshScope): Promise<void> {
    const root = workspaceRoot()
    if (!root) {
      info.value = null
      error.value = null
      baselineCache.value = {}
      ignoreCache.value = {}
      pendingIgnorePaths = []
      resetLog()
      return
    }

    const seq = ++refreshSeq
    const showLoading = scope === 'full' || !info.value
    if (showLoading) {
      loading.value = true
    }
    error.value = null
    try {
      const result = await window.workspaceApi.gitInfo(root, scope)
      if (seq !== refreshSeq) return

      if (scope === 'changes' && info.value?.isRepo) {
        info.value = {
          ...info.value,
          gitRoot: result.gitRoot ?? info.value.gitRoot,
          branch: result.branch ?? info.value.branch,
          changes: result.changes,
          error: result.error,
        }
      } else if (scope === 'sync' && info.value?.isRepo) {
        info.value = {
          ...info.value,
          gitRoot: result.gitRoot ?? info.value.gitRoot,
          branch: result.branch ?? info.value.branch,
          remotes: result.remotes.length ? result.remotes : info.value.remotes,
          syncStatus: result.syncStatus,
          error: result.error,
        }
      } else {
        info.value = result
      }

      if (info.value.error) {
        error.value = info.value.error
      }
    } catch (e) {
      if (seq !== refreshSeq) return
      error.value = e instanceof Error ? e.message : String(e)
      if (scope === 'full') {
        info.value = null
      }
    } finally {
      if (seq === refreshSeq && showLoading) {
        loading.value = false
      }
    }
  }

  async function runGitMutation(
    label: string,
    action: (root: string) => Promise<{ ok: boolean; error?: string; stdout?: string }>,
    options?: {
      clearBaselines?: boolean
      switchTo?: GitPanelView
      successMessage?: string
      refreshScope?: GitRefreshScope
    },
  ): Promise<boolean> {
    const root = workspaceRoot()
    if (!root) return false

    operating.value = true
    operatingLabel.value = label
    error.value = null
    try {
      const result = await action(root)
      if (!result.ok) {
        error.value = result.error ?? `${label}失败`
        useToastStore().show(error.value)
        return false
      }

      if (options?.clearBaselines !== false) {
        clearBaselines()
      }
      scheduleRefresh(options?.refreshScope ?? 'changes')

      const message = options?.successMessage ?? `${label}成功`
      useToastStore().show(message)
      if (options?.switchTo) {
        activeView.value = options.switchTo
      }
      return true
    } catch (e) {
      error.value = e instanceof Error ? e.message : String(e)
      useToastStore().show(error.value)
      return false
    } finally {
      operating.value = false
      operatingLabel.value = null
    }
  }

  async function stage(absPaths: string[]): Promise<boolean> {
    return runGitMutation('暂存', root => window.workspaceApi.gitStage(root, absPaths), {
      clearBaselines: false,
    })
  }

  async function stageAll(): Promise<boolean> {
    return runGitMutation('暂存全部', root => window.workspaceApi.gitStageAll(root), {
      clearBaselines: false,
    })
  }

  async function unstage(absPaths: string[]): Promise<boolean> {
    return runGitMutation('取消暂存', root => window.workspaceApi.gitUnstage(root, absPaths), {
      clearBaselines: false,
    })
  }

  async function unstageAll(): Promise<boolean> {
    return runGitMutation('取消全部暂存', root => window.workspaceApi.gitUnstageAll(root), {
      clearBaselines: false,
    })
  }

  async function discard(absPaths: string[], scope: GitDiscardScope = 'head'): Promise<boolean> {
    const label = scope === 'head' ? '还原' : '还原工作区'
    const ok = await runGitMutation(label, root =>
      window.workspaceApi.gitDiscard(root, absPaths, scope),
    )
    if (ok) {
      void import('./editorStore').then(({ useEditorStore }) => {
        void useEditorStore().refreshAfterGitChange(absPaths)
      })
    }
    return ok
  }

  async function commit(): Promise<boolean> {
    const ok = await runGitMutation(
      '提交',
      root => window.workspaceApi.gitCommit(root, commitMessage.value),
      { successMessage: '提交成功', switchTo: 'sync', refreshScope: 'full' },
    )
    if (ok) {
      commitMessage.value = ''
      resetLog()
    }
    return ok
  }

  async function fetchRemote(remote?: string): Promise<boolean> {
    return runGitMutation('Fetch', root => window.workspaceApi.gitFetch(root, remote), {
      clearBaselines: false,
      refreshScope: 'full',
    })
  }

  async function pull(remote?: string, branch?: string): Promise<boolean> {
    return runGitMutation('拉取', root => window.workspaceApi.gitPull(root, remote, branch), {
      clearBaselines: true,
      refreshScope: 'full',
    })
  }

  async function push(remote?: string, branch?: string): Promise<boolean> {
    return runGitMutation('推送', root => window.workspaceApi.gitPush(root, remote, branch), {
      clearBaselines: false,
      refreshScope: 'full',
    })
  }

  function resetLog(): void {
    logSkip = 0
    logEntries.value = []
    logHasMore.value = false
    logError.value = null
  }

  async function loadLog(reset = false): Promise<void> {
    const root = workspaceRoot()
    if (!root) {
      resetLog()
      return
    }

    if (reset) {
      logSkip = 0
      logEntries.value = []
      logHasMore.value = false
    }

    logLoading.value = true
    logError.value = null
    try {
      const result = await window.workspaceApi.gitLog(root, {
        limit: LOG_PAGE_SIZE,
        skip: logSkip,
      })
      if (result.error) {
        logError.value = result.error
      }
      logEntries.value = reset ? result.entries : [...logEntries.value, ...result.entries]
      logHasMore.value = result.hasMore
      logSkip += result.entries.length
    } catch (e) {
      logError.value = e instanceof Error ? e.message : String(e)
    } finally {
      logLoading.value = false
    }
  }

  async function loadMoreLog(): Promise<void> {
    if (!logHasMore.value || logLoading.value) return
    await loadLog(false)
  }

  function clearBaselines(): void {
    baselineCache.value = {}
    baselineTick.value++
  }

  function statusForPath(absolutePath: string): string | null {
    return findChangeForPath(absolutePath)?.status ?? null
  }

  async function ensureBaseline(absolutePath: string): Promise<string | null> {
    if (isPathIgnoredSync(absolutePath)) return null

    const key = normalizeDir(absolutePath)
    if (Object.prototype.hasOwnProperty.call(baselineCache.value, key)) {
      return baselineCache.value[key] ?? null
    }

    const root = workspaceRoot()
    if (!root) return null

    const change = findChangeForPath(absolutePath)
    if (!change || change.kind === 'directory') {
      baselineCache.value = { ...baselineCache.value, [key]: null }
      baselineTick.value++
      return null
    }

    try {
      const result = await window.workspaceApi.gitFileBaseline(root, absolutePath)
      if (result.baseline == null) {
        baselineCache.value = { ...baselineCache.value, [key]: null }
        baselineTick.value++
        return null
      }
      baselineCache.value = { ...baselineCache.value, [key]: result.baseline }
      baselineTick.value++
      return result.baseline
    } catch {
      baselineCache.value = { ...baselineCache.value, [key]: null }
      baselineTick.value++
      return null
    }
  }

  function cachedBaseline(absolutePath: string): string | null | undefined {
    // 读取 tick 以建立响应式依赖
    // eslint-disable-next-line @typescript-eslint/no-unused-expressions
    baselineTick.value
    if (isPathIgnoredSync(absolutePath)) return null
    const key = normalizeDir(absolutePath)
    if (!Object.prototype.hasOwnProperty.call(baselineCache.value, key)) return undefined
    return baselineCache.value[key] ?? null
  }

  return {
    info,
    loading,
    operating,
    operatingLabel,
    error,
    activeView,
    commitMessage,
    isRepo,
    stagedChanges,
    unstagedChanges,
    totalChangeCount,
    refresh,
    scheduleRefresh,
    clearBaselines,
    statusForPath,
    hasChangeUnder,
    decorationForPath,
    ensureBaseline,
    cachedBaseline,
    queueIgnoreScan,
    isPathIgnoredSync,
    stage,
    stageAll,
    unstage,
    unstageAll,
    discard,
    commit,
    fetchRemote,
    pull,
    push,
    logEntries,
    logLoading,
    logError,
    logHasMore,
    loadLog,
    loadMoreLog,
  }
})
