<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import PanelSectionHeader from '@renderer/components/layout/PanelSectionHeader.vue'
import { useSearchStore, type SearchMode } from '@renderer/stores/searchStore'
import { useEditorStore } from '@renderer/stores/editorStore'
import { useWorkspaceStore } from '@renderer/stores/workspaceStore'
import {
  openFindInFile,
  openReplaceInFile,
  waitForEditorAndRevealLine,
} from '@renderer/utils/editorSearchBridge'
import { normalizeEditorPath } from '@renderer/utils/editorPath'
import { buildHighlightSegments } from '@renderer/utils/searchHighlight'

defineEmits<{
  collapse: []
}>()

const search = useSearchStore()
const editor = useEditorStore()
const workspace = useWorkspaceStore()

const {
  mode,
  query,
  replaceText,
  caseSensitive,
  wholeWord,
  includeGlob,
  searching,
  replacing,
  error,
  groupedResults,
  resultSummary,
  replaceSummary,
  highlightQuery,
} = storeToRefs(search)

const queryInputRef = ref<HTMLInputElement | null>(null)
const replaceInputRef = ref<HTMLInputElement | null>(null)
const showFilters = ref(false)

const modeTabs: { id: SearchMode; label: string; hint: string }[] = [
  { id: 'workspace', label: '工作区', hint: '全文搜索整个项目' },
  { id: 'workspace-replace', label: '项目替换', hint: '在整个项目中查找并替换' },
  { id: 'file', label: '当前文件', hint: '在已打开编辑器中查找' },
  { id: 'replace', label: '文件替换', hint: '当前文件查找并替换' },
]

const activeFileLabel = computed(() => {
  const tab = editor.activeTab
  if (!tab) return '未打开文件'
  return tab.name
})

const canSearchFile = computed(() => editor.activeTab?.viewKind === 'text')

function editorSearchOptions() {
  return {
    query: query.value.trim(),
    replace: replaceText.value,
    caseSensitive: caseSensitive.value,
    wholeWord: wholeWord.value,
  }
}

function highlightSegments(line: string) {
  return buildHighlightSegments(
    line.trim(),
    highlightQuery.value,
    caseSensitive.value,
    wholeWord.value,
  )
}

function focusQueryInput() {
  void nextTick(() => queryInputRef.value?.focus())
}

function onModeChange(next: SearchMode) {
  search.setMode(next)
  focusQueryInput()
}

async function onSubmit() {
  if (mode.value === 'workspace') {
    await search.searchWorkspace()
    return
  }

  if (mode.value === 'workspace-replace') {
    await search.replaceInWorkspace()
    return
  }

  if (!canSearchFile.value) {
    search.setError('请先打开一个文本文件')
    return
  }

  if (mode.value === 'file') {
    if (!query.value.trim()) {
      search.setError('请输入要查找的内容')
      return
    }
    search.setError(null)
    const ok = openFindInFile(editor.activePath, editorSearchOptions())
    if (!ok) search.setError('编辑器尚未就绪，请稍后再试')
    return
  }

  if (!query.value.trim()) {
    search.setError('请输入要查找的内容')
    return
  }
  search.setError(null)
  const ok = openReplaceInFile(editor.activePath, editorSearchOptions())
  if (!ok) search.setError('编辑器尚未就绪，请稍后再试')
}

function onResultClick(file: string, line: number) {
  const target = normalizeEditorPath(file)
  void editor.openFile(target, { reveal: false }).then(() =>
    waitForEditorAndRevealLine(target, line),
  )
}

watch(mode, () => {
  search.setError(null)
})

onMounted(() => {
  focusQueryInput()
})

defineExpose({ focusQueryInput })
</script>

<template>
  <aside class="search-sidebar">
    <PanelSectionHeader title="搜索" @collapse="$emit('collapse')">
      <button
        type="button"
        class="search-sidebar__icon-btn"
        :class="{ 'search-sidebar__icon-btn--active': showFilters }"
        title="搜索选项"
        @click="showFilters = !showFilters"
      >
        ···
      </button>
    </PanelSectionHeader>

    <div class="search-sidebar__modes" role="tablist" aria-label="搜索模式">
      <button
        v-for="tab in modeTabs"
        :key="tab.id"
        type="button"
        class="search-sidebar__mode"
        :class="{ 'search-sidebar__mode--active': mode === tab.id }"
        :title="tab.hint"
        role="tab"
        :aria-selected="mode === tab.id"
        @click="onModeChange(tab.id)"
      >
        {{ tab.label }}
      </button>
    </div>

    <div class="search-sidebar__form">
      <div class="search-sidebar__field">
        <label class="search-sidebar__label" for="search-query">
          {{ mode === 'replace' || mode === 'workspace-replace' ? '查找' : '搜索' }}
        </label>
        <div class="search-sidebar__input-row">
          <input
            id="search-query"
            ref="queryInputRef"
            v-model="query"
            type="search"
            class="search-sidebar__input"
            :placeholder="
              mode === 'workspace' || mode === 'workspace-replace'
                ? '搜索文字内容… Enter 执行'
                : mode === 'file'
                  ? '在当前文件中查找… Enter'
                  : '要查找的内容… Enter'
            "
            @keydown.enter.prevent="onSubmit"
          />
          <button
            type="button"
            class="search-sidebar__go"
            :disabled="
              searching ||
              replacing ||
              ((mode === 'workspace' || mode === 'workspace-replace') && !workspace.rootPath)
            "
            @click="onSubmit"
          >
            {{
              searching || replacing
                ? '…'
                : mode === 'workspace-replace'
                  ? '全部替换'
                  : mode === 'workspace'
                    ? '搜索'
                    : '定位'
            }}
          </button>
        </div>
      </div>

      <div v-if="mode === 'replace' || mode === 'workspace-replace'" class="search-sidebar__field">
        <label class="search-sidebar__label" for="search-replace">替换为</label>
        <input
          id="search-replace"
          ref="replaceInputRef"
          v-model="replaceText"
          type="text"
          class="search-sidebar__input"
          :placeholder="
            mode === 'workspace-replace'
              ? '替换内容… Enter 替换整个项目'
              : '替换内容… Enter 同步到编辑器'
          "
          @keydown.enter.prevent="onSubmit"
        />
      </div>

      <div v-if="mode !== 'workspace' && mode !== 'workspace-replace'" class="search-sidebar__hint">
        当前文件：<span>{{ activeFileLabel }}</span>
      </div>

      <div v-if="showFilters || mode === 'workspace' || mode === 'workspace-replace'" class="search-sidebar__filters">
        <label class="search-sidebar__toggle">
          <input v-model="caseSensitive" type="checkbox" />
          区分大小写 (Aa)
        </label>
        <label class="search-sidebar__toggle">
          <input v-model="wholeWord" type="checkbox" />
          全字匹配
        </label>
        <div
          v-if="mode === 'workspace' || mode === 'workspace-replace'"
          class="search-sidebar__field search-sidebar__field--compact"
        >
          <label class="search-sidebar__label" for="search-glob">包含文件</label>
          <input
            id="search-glob"
            v-model="includeGlob"
            type="text"
            class="search-sidebar__input"
            placeholder="例如 *.java, *.vue"
            @keydown.enter.prevent="onSubmit"
          />
        </div>
      </div>
    </div>

    <p v-if="error" class="search-sidebar__error">{{ error }}</p>
    <p v-else-if="replaceSummary && mode === 'workspace-replace'" class="search-sidebar__summary">
      {{ replaceSummary }}
    </p>
    <p v-else-if="resultSummary && mode === 'workspace'" class="search-sidebar__summary">
      {{ resultSummary }}
    </p>

    <div v-if="mode === 'workspace'" class="search-sidebar__results">
      <p v-if="!groupedResults.length && !searching && query.trim()" class="search-sidebar__empty">
        无匹配结果
      </p>
      <p v-else-if="!workspace.rootPath" class="search-sidebar__empty">
        打开文件夹后可搜索整个工作区
      </p>

      <section v-for="group in groupedResults" :key="group.file" class="search-sidebar__group">
        <header class="search-sidebar__group-title" :title="group.file">
          {{ group.relativePath }}
          <span class="search-sidebar__group-count">{{ group.matches.length }}</span>
        </header>
        <button
          v-for="(match, index) in group.matches"
          :key="`${group.file}:${match.line}:${index}`"
          type="button"
          class="search-sidebar__match"
          @click="onResultClick(group.file, match.line)"
        >
          <span class="search-sidebar__match-line">{{ match.line }}</span>
          <span class="search-sidebar__match-text">
            <template v-for="(segment, segIndex) in highlightSegments(match.text)" :key="segIndex">
              <mark v-if="segment.highlight" class="search-sidebar__hl">{{ segment.text }}</mark>
              <template v-else>{{ segment.text }}</template>
            </template>
          </span>
        </button>
      </section>
    </div>

    <div v-else-if="mode === 'workspace-replace'" class="search-sidebar__tips">
      <p>
        在整个工作区文本文件中批量替换。操作不可撤销，建议先用「工作区」搜索确认匹配范围，或提交 Git 后再替换。
      </p>
    </div>

    <div v-else class="search-sidebar__tips">
      <p v-if="mode === 'file'">
        侧栏输入会同步到编辑器查找框。按 <kbd>Enter</kbd> 定位，或直接用 <kbd>Ctrl+F</kbd>。
      </p>
      <p v-else>
        侧栏「查找 / 替换为」会同步到编辑器替换面板，在编辑器内可逐个或全部替换。
      </p>
    </div>
  </aside>
</template>

<style scoped>
.search-sidebar {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--ide-sidebar-bg);
}

.search-sidebar__icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--ide-muted);
  cursor: pointer;
  font-size: 14px;
  letter-spacing: 1px;
}

.search-sidebar__icon-btn:hover,
.search-sidebar__icon-btn--active {
  background: rgba(255, 255, 255, 0.06);
  color: var(--ide-fg);
}

.search-sidebar__modes {
  display: flex;
  gap: 2px;
  padding: 6px 8px 0;
  border-bottom: 1px solid var(--ide-border);
}

.search-sidebar__mode {
  flex: 1;
  padding: 6px 4px;
  border: none;
  border-radius: 4px 4px 0 0;
  background: transparent;
  color: var(--ide-muted);
  font-size: 11px;
  cursor: pointer;
}

.search-sidebar__mode:hover {
  color: var(--ide-fg);
}

.search-sidebar__mode--active {
  color: var(--ide-fg);
  background: rgba(255, 255, 255, 0.04);
  box-shadow: inset 0 -2px 0 var(--ide-accent);
}

.search-sidebar__form {
  padding: 10px 10px 8px;
  border-bottom: 1px solid var(--ide-border);
}

.search-sidebar__field {
  margin-bottom: 8px;
}

.search-sidebar__field--compact {
  margin-bottom: 0;
}

.search-sidebar__label {
  display: block;
  margin-bottom: 4px;
  font-size: 11px;
  color: var(--ide-muted);
}

.search-sidebar__input-row {
  display: flex;
  gap: 6px;
}

.search-sidebar__input {
  width: 100%;
  height: 26px;
  padding: 0 8px;
  border: 1px solid var(--ide-border);
  border-radius: 4px;
  background: var(--ide-input-bg);
  color: var(--ide-fg);
  font-size: 12px;
}

.search-sidebar__input:focus {
  outline: 1px solid var(--ide-accent);
  outline-offset: -1px;
}

.search-sidebar__go {
  flex-shrink: 0;
  height: 26px;
  padding: 0 10px;
  border: 1px solid var(--ide-border);
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.04);
  color: var(--ide-fg);
  font-size: 11px;
  cursor: pointer;
}

.search-sidebar__go:hover:not(:disabled) {
  background: var(--ide-accent);
  border-color: var(--ide-accent);
  color: #fff;
}

.search-sidebar__go:disabled {
  opacity: 0.45;
  cursor: default;
}

.search-sidebar__hint {
  margin-bottom: 6px;
  font-size: 11px;
  color: var(--ide-muted);
}

.search-sidebar__hint span {
  color: var(--ide-fg);
}

.search-sidebar__filters {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-top: 4px;
}

.search-sidebar__toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--ide-muted);
  cursor: pointer;
}

.search-sidebar__error {
  margin: 0;
  padding: 8px 10px;
  font-size: 11px;
  color: #f48771;
  background: rgba(244, 135, 113, 0.08);
}

.search-sidebar__summary {
  margin: 0;
  padding: 6px 10px;
  font-size: 11px;
  color: var(--ide-muted);
  border-bottom: 1px solid var(--ide-border);
}

.search-sidebar__results {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.search-sidebar__empty {
  margin: 0;
  padding: 16px 12px;
  font-size: 12px;
  color: var(--ide-muted);
  text-align: center;
}

.search-sidebar__group {
  border-bottom: 1px solid var(--ide-border);
}

.search-sidebar__group-title {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  font-size: 11px;
  font-weight: 600;
  color: var(--ide-fg);
  background: rgba(255, 255, 255, 0.02);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-sidebar__group-count {
  margin-left: auto;
  font-size: 10px;
  color: var(--ide-muted);
  font-weight: 500;
}

.search-sidebar__match {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
  padding: 4px 10px;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
  color: var(--ide-fg);
}

.search-sidebar__match:hover {
  background: rgba(255, 255, 255, 0.06);
}

.search-sidebar__match-line {
  flex-shrink: 0;
  width: 28px;
  font-size: 11px;
  color: var(--ide-accent);
  font-family: ui-monospace, monospace;
}

.search-sidebar__match-text {
  flex: 1;
  min-width: 0;
  font-size: 11px;
  font-family: ui-monospace, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-sidebar__hl {
  background: rgba(234, 92, 0, 0.45);
  color: #ffffff;
  border-radius: 2px;
  padding: 0 1px;
  font-weight: 600;
}

.search-sidebar__tips {
  padding: 12px;
  font-size: 11px;
  color: var(--ide-muted);
  line-height: 1.6;
}

.search-sidebar__tips kbd {
  padding: 1px 4px;
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.08);
  color: var(--ide-fg);
  font-size: 10px;
}
</style>
