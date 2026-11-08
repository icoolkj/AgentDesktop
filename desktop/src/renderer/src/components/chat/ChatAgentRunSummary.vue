<script setup lang="ts">
import { computed } from 'vue'
import type { ChatFileChange, ChatToolActivity } from '@renderer/stores/chatStore'
import { buildAgentRunSummary, formatDurationMs } from '@renderer/utils/toolDisplay'

const props = defineProps<{
  tools: ChatToolActivity[]
  fileChanges?: ChatFileChange[]
}>()

const summary = computed(() => buildAgentRunSummary(props.tools))

const fileStats = computed(() => {
  const changes = props.fileChanges ?? []
  if (changes.length === 0) return null
  const added = changes.reduce((s, c) => s + c.added, 0)
  const removed = changes.reduce((s, c) => s + c.removed, 0)
  const created = changes.filter(c => c.changeKind === 'created').length
  const modified = changes.filter(c => c.changeKind === 'modified').length
  return { files: changes.length, added, removed, created, modified }
})
</script>

<template>
  <div v-if="summary" class="run-summary">
    <div class="run-summary__head">
      <span class="run-summary__badge">Agent</span>
      <span class="run-summary__headline">{{ summary.headline }}</span>
    </div>

    <div class="run-summary__grid">
      <div v-for="(phase, idx) in summary.phases" :key="idx" class="run-summary__phase">
        <span class="run-summary__phase-label">{{ phase.label }}</span>
        <span class="run-summary__phase-detail">{{ phase.detail }}</span>
      </div>

      <div v-if="fileStats" class="run-summary__phase run-summary__phase--files">
        <span class="run-summary__phase-label">写入磁盘</span>
        <span class="run-summary__phase-detail">
          {{ fileStats.files }} 文件
          <template v-if="fileStats.created"> · {{ fileStats.created }} 新建</template>
          <template v-if="fileStats.modified"> · {{ fileStats.modified }} 修改</template>
          <span class="run-summary__diff">
            +{{ fileStats.added }} −{{ fileStats.removed }}
          </span>
        </span>
      </div>
    </div>

    <p v-if="summary.totalDurationMs >= 1000" class="run-summary__foot">
      工具执行合计 {{ formatDurationMs(summary.totalDurationMs) }}
    </p>
  </div>
</template>

<style scoped>
.run-summary {
  align-self: stretch;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid color-mix(in srgb, var(--ide-accent) 28%, var(--ide-border));
  background: color-mix(in srgb, var(--ide-accent) 5%, var(--ide-surface-bg));
}

.run-summary__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.run-summary__badge {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 0.58rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--ide-accent);
  background: color-mix(in srgb, var(--ide-accent) 14%, transparent);
  border: 1px solid color-mix(in srgb, var(--ide-accent) 30%, transparent);
}

.run-summary__headline {
  font-size: 0.76rem;
  font-weight: 600;
  color: var(--ide-fg);
}

.run-summary__grid {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.run-summary__phase {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 0.68rem;
  padding-left: 2px;
}

.run-summary__phase::before {
  content: '';
  width: 4px;
  height: 4px;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--ide-accent);
  opacity: 0.7;
  align-self: center;
}

.run-summary__phase-label {
  color: var(--ide-fg);
  font-weight: 500;
  min-width: 88px;
  flex-shrink: 0;
}

.run-summary__phase-detail {
  color: var(--ide-muted);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.run-summary__phase--files .run-summary__phase-label {
  color: var(--ide-success);
}

.run-summary__diff {
  margin-left: 4px;
  font-family: ui-monospace, monospace;
  font-size: 0.62rem;
}

.run-summary__foot {
  margin: 8px 0 0;
  padding-top: 6px;
  border-top: 1px solid color-mix(in srgb, var(--ide-border) 80%, transparent);
  font-size: 0.62rem;
  color: var(--ide-welcome-fg);
}
</style>
