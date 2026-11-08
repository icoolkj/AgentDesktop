<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ChatToolActivity } from '@renderer/stores/chatStore'
import {
  buildAgentRunSummary,
  formatDurationMs,
  formatToolLabel,
  formatToolSummaryLine,
  groupIcon,
  groupToolActivities,
  shouldAutoExpandTools,
  toolDurationMs,
} from '@renderer/utils/toolDisplay'

const props = withDefaults(
  defineProps<{
    tools: ChatToolActivity[]
    defaultExpanded?: boolean
    /** In-flight turn — keep panel open and highlight running step. */
    live?: boolean
    showRunSummary?: boolean
  }>(),
  { defaultExpanded: false, live: false, showRunSummary: false },
)

const expanded = ref(props.defaultExpanded || props.live)
const expandedToolId = ref<string | null>(null)
const expandedGroups = ref<Set<string>>(new Set(['read', 'search', 'edit', 'other']))

watch(
  () => [props.live, props.defaultExpanded, props.tools.length] as const,
  () => {
    if (shouldAutoExpandTools(props.tools, props.live) || props.defaultExpanded) {
      expanded.value = true
    }
  },
  { immediate: true },
)

const doneCount = computed(() => props.tools.filter(t => t.status === 'done').length)
const hasRunning = computed(() => props.tools.some(t => t.status === 'running'))
const runningTool = computed(() => props.tools.find(t => t.status === 'running'))
const groups = computed(() => groupToolActivities(props.tools))
const summary = computed(() => formatToolSummaryLine(props.tools))
const runSummary = computed(() => (props.showRunSummary ? buildAgentRunSummary(props.tools) : null))

const headerTitle = computed(() => {
  if (hasRunning.value) return 'Agent 正在执行…'
  if (doneCount.value === 1) return 'Agent 完成 1 步'
  if (doneCount.value > 0) return `Agent 完成 ${doneCount.value} 步`
  return `Agent · ${props.tools.length} 步`
})

function labelFor(tool: ChatToolActivity): string {
  return formatToolLabel(tool.name, tool.argsJson, tool.output, tool.summary)
}

function durationLabel(tool: ChatToolActivity): string {
  const ms = toolDurationMs(tool)
  if (ms == null) return tool.status === 'running' ? '…' : ''
  return formatDurationMs(ms)
}

function togglePanel() {
  expanded.value = !expanded.value
}

function toggleGroup(kind: string) {
  const next = new Set(expandedGroups.value)
  if (next.has(kind)) next.delete(kind)
  else next.add(kind)
  expandedGroups.value = next
}

function toggleToolOutput(id: string) {
  expandedToolId.value = expandedToolId.value === id ? null : id
}

function statusIcon(tool: ChatToolActivity): string {
  if (tool.status === 'done') return '✓'
  if (tool.status === 'error') return '✗'
  return '…'
}
</script>

<template>
  <div v-if="tools.length" class="tool-panel" :class="{ 'tool-panel--live': live }">
    <button type="button" class="tool-panel__header" @click="togglePanel">
      <span class="tool-panel__chevron" :class="{ 'tool-panel__chevron--open': expanded }">›</span>
      <span v-if="live && hasRunning" class="tool-panel__pulse" aria-hidden="true" />
      <span class="tool-panel__title">{{ headerTitle }}</span>
      <span v-if="!expanded" class="tool-panel__summary">{{ summary }}</span>
    </button>

    <div v-if="runSummary && expanded" class="tool-panel__run-summary">
      <p class="tool-panel__run-headline">{{ runSummary.headline }}</p>
      <ul class="tool-panel__run-phases">
        <li v-for="(phase, idx) in runSummary.phases" :key="idx">
          <span class="tool-panel__phase-label">{{ phase.label }}</span>
          <span class="tool-panel__phase-detail">{{ phase.detail }}</span>
        </li>
      </ul>
    </div>

    <div v-if="expanded && runningTool" class="tool-panel__pinned">
      <span class="tool-panel__spinner" aria-hidden="true" />
      <span class="tool-panel__pinned-label">{{ labelFor(runningTool) }}</span>
      <span class="tool-panel__pinned-meta">进行中</span>
    </div>

    <div v-if="expanded" class="tool-panel__body">
      <section v-for="group in groups" :key="group.kind" class="tool-group">
        <button type="button" class="tool-group__header" @click="toggleGroup(group.kind)">
          <span class="tool-group__chevron" :class="{ open: expandedGroups.has(group.kind) }">›</span>
          <span class="tool-group__icon" aria-hidden="true">{{ groupIcon(group.kind) }}</span>
          <span class="tool-group__title">{{ group.title }}</span>
          <span v-if="group.liveHint" class="tool-group__hint">{{ group.liveHint }}</span>
        </button>

        <div v-if="expandedGroups.has(group.kind)" class="tool-group__list">
          <button
            v-for="tool in group.tools"
            :key="tool.toolCallId"
            type="button"
            class="tool-row"
            :class="{
              'tool-row--running': tool.status === 'running',
              'tool-row--error': tool.status === 'error',
            }"
            @click="toggleToolOutput(tool.toolCallId)"
          >
            <span class="tool-row__icon">{{ statusIcon(tool) }}</span>
            <span class="tool-row__label">{{ labelFor(tool) }}</span>
            <span v-if="durationLabel(tool)" class="tool-row__duration">{{ durationLabel(tool) }}</span>
          </button>

          <pre
            v-for="tool in group.tools"
            v-show="expandedToolId === tool.toolCallId && tool.output"
            :key="`${tool.toolCallId}-out`"
            class="tool-panel__output"
          >{{ tool.output }}</pre>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.tool-panel {
  align-self: stretch;
  border-radius: 8px;
  background: transparent;
  border: 1px solid var(--ide-bubble-border);
  overflow: hidden;
}

.tool-panel--live {
  border-color: color-mix(in srgb, var(--ide-accent) 35%, var(--ide-bubble-border));
}

.tool-panel__header {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 8px 10px;
  border: none;
  background: var(--ide-surface-bg);
  color: var(--ide-muted);
  font-size: 0.74rem;
  cursor: pointer;
  text-align: left;
}

.tool-panel__header:hover {
  background: var(--ide-hover-overlay);
}

.tool-panel__chevron {
  display: inline-block;
  transition: transform 0.15s ease;
  color: var(--ide-welcome-fg);
  font-size: 0.85rem;
}

.tool-panel__chevron--open {
  transform: rotate(90deg);
}

.tool-panel__pulse {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--ide-accent);
  animation: pulse-dot 1.2s ease-in-out infinite;
}

.tool-panel__title {
  color: var(--ide-fg);
  flex-shrink: 0;
  font-weight: 600;
}

.tool-panel__summary {
  margin-left: auto;
  font-size: 0.65rem;
  color: var(--ide-welcome-fg);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 55%;
}

.tool-panel__run-summary {
  padding: 8px 12px;
  border-top: 1px solid var(--ide-bubble-border);
  background: color-mix(in srgb, var(--ide-accent) 6%, var(--ide-surface-bg));
}

.tool-panel__run-headline {
  margin: 0 0 6px;
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--ide-fg);
}

.tool-panel__run-phases {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.tool-panel__run-phases li {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 0.68rem;
}

.tool-panel__phase-label {
  color: var(--ide-fg);
  font-weight: 500;
  min-width: 0;
}

.tool-panel__phase-detail {
  color: var(--ide-welcome-fg);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tool-panel__pinned {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-top: 1px solid var(--ide-bubble-border);
  background: color-mix(in srgb, var(--ide-accent) 8%, var(--ide-surface-bg));
}

.tool-panel__spinner {
  display: inline-block;
  width: 12px;
  height: 12px;
  border: 1.5px solid var(--ide-button-border);
  border-top-color: var(--ide-accent);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
  flex-shrink: 0;
}

.tool-panel__pinned-label {
  flex: 1;
  min-width: 0;
  font-size: 0.74rem;
  font-weight: 500;
  color: var(--ide-fg);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tool-panel__pinned-meta {
  flex-shrink: 0;
  font-size: 0.62rem;
  color: var(--ide-accent);
  font-weight: 600;
}

.tool-panel__body {
  border-top: 1px solid var(--ide-bubble-border);
  padding: 4px 0 6px;
  background: var(--ide-surface-bg);
}

.tool-group + .tool-group {
  border-top: 1px dashed color-mix(in srgb, var(--ide-border) 70%, transparent);
}

.tool-group__header {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 5px 10px 5px 8px;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
  font-size: 0.7rem;
}

.tool-group__header:hover {
  background: var(--ide-hover-overlay);
}

.tool-group__chevron {
  display: inline-block;
  transition: transform 0.12s ease;
  color: var(--ide-welcome-fg);
  font-size: 0.8rem;
}

.tool-group__chevron.open {
  transform: rotate(90deg);
}

.tool-group__icon {
  flex-shrink: 0;
  font-size: 0.72rem;
  opacity: 0.85;
}

.tool-group__title {
  color: var(--ide-fg);
  font-weight: 500;
}

.tool-group__hint {
  margin-left: auto;
  max-width: 45%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ide-accent);
  font-family: ui-monospace, monospace;
  font-size: 0.62rem;
}

.tool-group__list {
  padding-bottom: 2px;
}

.tool-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 4px 12px 4px 28px;
  border: none;
  background: transparent;
  color: var(--ide-fg);
  font-size: 0.72rem;
  cursor: pointer;
  text-align: left;
}

.tool-row:hover {
  background: var(--ide-hover-overlay);
}

.tool-row__icon {
  width: 14px;
  flex-shrink: 0;
  color: var(--ide-success);
  font-size: 0.62rem;
}

.tool-row--running .tool-row__icon {
  color: var(--ide-warning);
}

.tool-row--error .tool-row__icon {
  color: var(--ide-error);
}

.tool-row__label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ide-muted);
}

.tool-row--running .tool-row__label {
  color: var(--ide-fg);
  font-weight: 500;
}

.tool-row__duration {
  flex-shrink: 0;
  font-family: ui-monospace, monospace;
  font-size: 0.6rem;
  color: var(--ide-welcome-fg);
}

.tool-panel__output {
  margin: 0 12px 6px 28px;
  padding: 8px;
  max-height: 160px;
  overflow: auto;
  border-radius: 6px;
  background: var(--ide-code-bg);
  border: 1px solid var(--ide-bubble-border);
  font-size: 0.65rem;
  font-family: ui-monospace, monospace;
  color: var(--ide-fg);
  white-space: pre-wrap;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes pulse-dot {
  0%,
  100% {
    opacity: 0.45;
    transform: scale(0.92);
  }
  50% {
    opacity: 1;
    transform: scale(1);
  }
}
</style>
