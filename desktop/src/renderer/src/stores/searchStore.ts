import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { useWorkspaceStore } from './workspaceStore'

export type SearchMode = 'workspace' | 'file' | 'replace' | 'workspace-replace'

export interface SearchMatch {
  file: string
  line: number
  column: number
  text: string
  relativePath: string
}

export interface SearchFileGroup {
  file: string
  relativePath: string
  matches: SearchMatch[]
}

export const useSearchStore = defineStore('search', () => {
  const mode = ref<SearchMode>('workspace')
  const query = ref('')
  const replaceText = ref('')
  const caseSensitive = ref(false)
  const wholeWord = ref(false)
  const includeGlob = ref('')
  const searching = ref(false)
  const error = ref<string | null>(null)
  const truncated = ref(false)
  const engine = ref<'ripgrep' | 'fallback' | null>(null)
  const highlightQuery = ref('')
  const rawMatches = ref<SearchMatch[]>([])
  const replacing = ref(false)
  const replaceSummary = ref<string | null>(null)

  const groupedResults = computed((): SearchFileGroup[] => {
    const map = new Map<string, SearchFileGroup>()
    for (const match of rawMatches.value) {
      let group = map.get(match.file)
      if (!group) {
        group = { file: match.file, relativePath: match.relativePath, matches: [] }
        map.set(match.file, group)
      }
      group.matches.push(match)
    }
    return [...map.values()]
  })

  const resultSummary = computed(() => {
    const files = groupedResults.value.length
    const lines = rawMatches.value.length
    if (lines === 0) return null
    const engineHint = engine.value === 'fallback' ? ' · 内置搜索' : ''
    return `${lines} 处匹配 · ${files} 个文件${truncated.value ? '（结果已截断）' : ''}${engineHint}`
  })

  function setMode(next: SearchMode): void {
    mode.value = next
  }

  function setError(message: string | null): void {
    error.value = message
  }

  function resetResults(): void {
    rawMatches.value = []
    truncated.value = false
    engine.value = null
    highlightQuery.value = ''
    error.value = null
    replaceSummary.value = null
  }

  async function searchWorkspace(): Promise<void> {
    const workspace = useWorkspaceStore()
    const root = workspace.rootPath
    if (!root) {
      error.value = '请先打开工作区文件夹'
      return
    }
    const trimmed = query.value.trim()
    if (!trimmed) {
      error.value = '请输入搜索内容'
      return
    }
    if (looksLikeGlobPattern(trimmed)) {
      error.value = '「*.java」这类文件过滤请填在下方「包含文件」，搜索框请输入要查找的文字'
      return
    }

    searching.value = true
    error.value = null

    try {
      const result = await window.workspaceApi.grep({
        rootPath: root,
        pattern: trimmed,
        caseInsensitive: !caseSensitive.value,
        wholeWord: wholeWord.value,
        glob: includeGlob.value.trim() || undefined,
      })

      if (result.error) {
        error.value = result.error
        rawMatches.value = []
        truncated.value = false
        engine.value = null
        return
      }

      truncated.value = result.truncated
      engine.value = result.engine ?? null
      highlightQuery.value = trimmed
      rawMatches.value = result.matches.map(match => ({
        ...match,
        relativePath: toRelative(root, match.file),
      }))
    } catch (e) {
      error.value = e instanceof Error ? e.message : String(e)
      rawMatches.value = []
    } finally {
      searching.value = false
    }
  }

  async function replaceInWorkspace(): Promise<void> {
    const workspace = useWorkspaceStore()
    const root = workspace.rootPath
    if (!root) {
      error.value = '请先打开工作区文件夹'
      return
    }
    const trimmed = query.value.trim()
    if (!trimmed) {
      error.value = '请输入要查找的内容'
      return
    }
    if (looksLikeGlobPattern(trimmed)) {
      error.value = '「*.java」这类文件过滤请填在下方「包含文件」，搜索框请输入要查找的文字'
      return
    }

    replacing.value = true
    error.value = null
    replaceSummary.value = null

    try {
      const result = await window.workspaceApi.replaceInFiles({
        rootPath: root,
        pattern: trimmed,
        replacement: replaceText.value,
        caseInsensitive: !caseSensitive.value,
        wholeWord: wholeWord.value,
        glob: includeGlob.value.trim() || undefined,
      })

      if (result.error) {
        error.value = result.error
        return
      }

      replaceSummary.value = `已在 ${result.fileCount} 个文件中替换 ${result.replacementCount} 处${
        result.truncated ? '（扫描文件数已达上限，可能未全部替换）' : ''
      }`
    } catch (e) {
      error.value = e instanceof Error ? e.message : String(e)
    } finally {
      replacing.value = false
    }
  }

  return {
    mode,
    query,
    replaceText,
    caseSensitive,
    wholeWord,
    includeGlob,
    searching,
    replacing,
    error,
    truncated,
    engine,
    highlightQuery,
    rawMatches,
    replaceSummary,
    groupedResults,
    resultSummary,
    setMode,
    setError,
    resetResults,
    searchWorkspace,
    replaceInWorkspace,
  }
})

function looksLikeGlobPattern(pattern: string): boolean {
  const value = pattern.trim()
  return /^\*\.[A-Za-z0-9_-]+$/.test(value)
}

function toRelative(root: string, file: string): string {
  const normRoot = root.replace(/\\/g, '/').replace(/\/+$/, '')
  const normFile = file.replace(/\\/g, '/')
  if (normFile.startsWith(`${normRoot}/`)) {
    return normFile.slice(normRoot.length + 1)
  }
  return normFile.split('/').pop() ?? normFile
}
