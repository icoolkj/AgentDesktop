<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useProblemsStore } from '@renderer/stores/problemsStore'
import { useWorkspaceStore } from '@renderer/stores/workspaceStore'
import { severityLabel, type ProblemEntry } from '@renderer/utils/problemsModel'

const problems = useProblemsStore()
const workspace = useWorkspaceStore()
const {
  groupedByFile,
  summaryLabel,
  scanHint,
  totalCount,
  scanning,
  scanMeta,
} = storeToRefs(problems)

function onProblemClick(entry: ProblemEntry) {
  void problems.goToProblem(entry)
}

function onScanClick() {
  void problems.runWorkspaceScan()
}

function severityClass(severity: ProblemEntry['severity']): string {
  return `problems-panel__severity--${severity}`
}
</script>

<template>
  <div class="problems-panel">
    <div class="problems-panel__toolbar">
      <button
        type="button"
        class="problems-panel__scan"
        :disabled="!workspace.rootPath || scanning"
        @click="onScanClick"
      >
        {{ scanning ? '扫描中…' : '扫描工作区' }}
      </button>
      <span v-if="scanHint" class="problems-panel__hint">{{ scanHint }}</span>
    </div>

    <p v-if="totalCount === 0" class="problems-panel__empty">
      {{ scanning ? '正在扫描整个项目…' : '暂无问题。打开文件可查看编辑器诊断，或点击「扫描工作区」运行 TypeScript / ESLint。' }}
    </p>
    <p v-else class="problems-panel__summary">
      {{ summaryLabel }}
      <span v-if="scanMeta?.truncated" class="problems-panel__truncated">（结果已截断）</span>
    </p>

    <div v-if="groupedByFile.length" class="problems-panel__list">
      <section v-for="group in groupedByFile" :key="group.path" class="problems-panel__group">
        <header class="problems-panel__group-title" :title="group.path">
          {{ group.relativePath }}
          <span class="problems-panel__group-count">{{ group.problems.length }}</span>
        </header>
        <button
          v-for="(problem, index) in group.problems"
          :key="`${group.path}:${problem.line}:${problem.column}:${index}`"
          type="button"
          class="problems-panel__row"
          @click="onProblemClick(problem)"
        >
          <span class="problems-panel__severity" :class="severityClass(problem.severity)">
            {{ severityLabel(problem.severity) }}
          </span>
          <span class="problems-panel__location">{{ problem.line }}:{{ problem.column }}</span>
          <span class="problems-panel__message" :title="problem.message">
            {{ problem.message }}
          </span>
          <span v-if="problem.source" class="problems-panel__source">{{ problem.source }}</span>
        </button>
      </section>
    </div>
  </div>
</template>

<style scoped>
.problems-panel {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: var(--ide-sidebar-bg);
  color: var(--ide-fg);
}

.problems-panel__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  padding: 6px 10px;
  border-bottom: 1px solid var(--ide-border);
}

.problems-panel__scan {
  border: 1px solid var(--ide-border);
  border-radius: 4px;
  background: var(--ide-input-bg, transparent);
  color: var(--ide-fg);
  font-size: 11px;
  padding: 2px 8px;
  cursor: pointer;
}

.problems-panel__scan:disabled {
  opacity: 0.5;
  cursor: default;
}

.problems-panel__hint {
  font-size: 11px;
  color: var(--ide-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.problems-panel__empty,
.problems-panel__summary {
  flex-shrink: 0;
  margin: 0;
  padding: 8px 10px;
  font-size: 12px;
  color: var(--ide-muted);
  border-bottom: 1px solid var(--ide-border);
}

.problems-panel__truncated {
  color: var(--ide-warning);
}

.problems-panel__list {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.problems-panel__group-title {
  position: sticky;
  top: 0;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  font-size: 11px;
  font-weight: 600;
  color: var(--ide-muted);
  background: color-mix(in srgb, var(--ide-sidebar-bg) 92%, var(--ide-border));
  border-bottom: 1px solid var(--ide-border);
}

.problems-panel__group-count {
  margin-left: auto;
  font-weight: 500;
}

.problems-panel__row {
  display: grid;
  grid-template-columns: 44px 56px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  width: 100%;
  padding: 4px 10px;
  border: none;
  border-bottom: 1px solid color-mix(in srgb, var(--ide-border) 60%, transparent);
  background: transparent;
  color: var(--ide-fg);
  text-align: left;
  cursor: pointer;
  font-size: 12px;
}

.problems-panel__row:hover {
  background: color-mix(in srgb, var(--ide-accent) 10%, transparent);
}

.problems-panel__severity {
  font-size: 11px;
  font-weight: 600;
}

.problems-panel__severity--error {
  color: var(--ide-error);
}

.problems-panel__severity--warning {
  color: var(--ide-warning);
}

.problems-panel__severity--info {
  color: var(--ide-accent);
}

.problems-panel__location {
  font-family: Consolas, 'Courier New', monospace;
  color: var(--ide-muted);
}

.problems-panel__message {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.problems-panel__source {
  font-size: 10px;
  color: var(--ide-muted);
}
</style>
