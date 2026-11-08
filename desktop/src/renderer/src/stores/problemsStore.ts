import { computed, nextTick, ref, watch } from 'vue'
import { defineStore } from 'pinia'

import { waitForEditorAndRevealLine } from '@renderer/utils/editorSearchBridge'
import { normalizeEditorPath } from '@renderer/utils/editorPath'
import { collectMonacoProblems, subscribeMonacoMarkerChanges } from '@renderer/utils/monacoMarkersBridge'
import {
  countBySeverity,
  groupProblemsByFile,
  mergeProblemEntries,
  type ProblemEntry,
} from '@renderer/utils/problemsModel'
import { formatWorkspaceError } from '@renderer/utils/workspaceErrors'
import { useEditorStore } from '@renderer/stores/editorStore'
import { useWorkspaceStore } from '@renderer/stores/workspaceStore'
import { useWorkspaceUiStore } from '@renderer/stores/workspaceUiStore'

export interface ProblemsScanMeta {
  engines: string[]
  scannedAt: number
  error?: string
  truncated?: boolean
}

export const useProblemsStore = defineStore('problems', () => {
  const editorEntries = ref<ProblemEntry[]>([])
  const workspaceEntries = ref<ProblemEntry[]>([])
  const entries = ref<ProblemEntry[]>([])
  const scanning = ref(false)
  const scanMeta = ref<ProblemsScanMeta | null>(null)

  let unsubscribe: (() => void) | null = null
  let stopRootWatch: (() => void) | null = null

  function recompute(): void {
    entries.value = mergeProblemEntries(editorEntries.value, workspaceEntries.value)
  }

  function refreshEditor(): void {
    editorEntries.value = collectMonacoProblems()
    recompute()
  }

  function init(): void {
    if (unsubscribe) return
    unsubscribe = subscribeMonacoMarkerChanges(refreshEditor)
    refreshEditor()

    stopRootWatch = watch(
      () => useWorkspaceStore().rootPath,
      root => {
        workspaceEntries.value = []
        scanMeta.value = null
        recompute()
        if (root && useWorkspaceUiStore().autoScanProblemsOnOpen) void runWorkspaceScan()
      },
    )

    const root = useWorkspaceStore().rootPath
    if (root && useWorkspaceUiStore().autoScanProblemsOnOpen) void runWorkspaceScan()
  }

  function dispose(): void {
    unsubscribe?.()
    unsubscribe = null
    stopRootWatch?.()
    stopRootWatch = null
  }

  async function runWorkspaceScan(): Promise<void> {
    const root = useWorkspaceStore().rootPath
    if (!root || scanning.value) return

    scanning.value = true
    try {
      const result = await window.workspaceApi.scanDiagnostics(root)
      workspaceEntries.value = result.problems.map(problem => ({
        path: normalizeEditorPath(problem.path),
        line: problem.line,
        column: problem.column,
        endLine: problem.endLine,
        endColumn: problem.endColumn,
        message: problem.message,
        severity: problem.severity,
        source: problem.source,
        code: problem.code,
      }))
      scanMeta.value = {
        engines: result.engines,
        scannedAt: Date.now(),
        error: result.error,
        truncated: result.truncated,
      }
    } catch (err) {
      workspaceEntries.value = []
      scanMeta.value = {
        engines: [],
        scannedAt: Date.now(),
        error: formatWorkspaceError(err),
      }
    } finally {
      scanning.value = false
      recompute()
    }
  }

  const counts = computed(() => countBySeverity(entries.value))

  const errorCount = computed(() => counts.value.errors)
  const warningCount = computed(() => counts.value.warnings)
  const infoCount = computed(() => counts.value.infos)
  const totalCount = computed(() => entries.value.length)
  const workspaceCount = computed(() => workspaceEntries.value.length)
  const editorCount = computed(() => editorEntries.value.length)

  const groupedByFile = computed(() =>
    groupProblemsByFile(entries.value, useWorkspaceStore().rootPath ?? undefined),
  )

  const summaryLabel = computed(() => {
    if (scanning.value) return '正在扫描工作区…'
    if (totalCount.value === 0) {
      if (scanMeta.value?.error) return scanMeta.value.error
      return '无问题'
    }
    const parts: string[] = []
    if (errorCount.value) parts.push(`${errorCount.value} 错误`)
    if (warningCount.value) parts.push(`${warningCount.value} 警告`)
    if (infoCount.value) parts.push(`${infoCount.value} 信息`)
    return parts.join(' · ')
  })

  const scanHint = computed(() => {
    if (scanning.value) return 'TypeScript / ESLint 全项目扫描中'
    if (!scanMeta.value) return '打开工作区后将自动扫描'
    if (scanMeta.value.engines.length) {
      return `引擎：${scanMeta.value.engines.join(' · ')}`
    }
    return undefined
  })

  async function goToProblem(entry: ProblemEntry): Promise<void> {
    const editor = useEditorStore()
    const path = normalizeEditorPath(entry.path)
    await editor.openFile(path, { reveal: false })
    await nextTick()
    await waitForEditorAndRevealLine(path, entry.line, entry.column)
  }

  return {
    entries,
    groupedByFile,
    errorCount,
    warningCount,
    infoCount,
    totalCount,
    workspaceCount,
    editorCount,
    summaryLabel,
    scanHint,
    scanning,
    scanMeta,
    refreshEditor,
    runWorkspaceScan,
    init,
    dispose,
    goToProblem,
  }
})
