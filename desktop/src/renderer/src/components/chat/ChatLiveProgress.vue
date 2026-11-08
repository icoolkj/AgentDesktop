<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useChatStore } from '@renderer/stores/chatStore'
import { renderSimpleMarkdown } from '@renderer/utils/renderMarkdown'
import { formatToolLabel } from '@renderer/utils/toolDisplay'
import ChatToolPanel from '@renderer/components/chat/ChatToolPanel.vue'

const chatStore = useChatStore()
const { isSending, toolActivity, streamingText, error, runStartedAt, runStatus } = storeToRefs(chatStore)

const now = ref(Date.now())
let tickTimer: ReturnType<typeof setInterval> | null = null

watch(
  isSending,
  active => {
    if (tickTimer) {
      clearInterval(tickTimer)
      tickTimer = null
    }
    if (active) {
      now.value = Date.now()
      tickTimer = setInterval(() => {
        now.value = Date.now()
      }, 500)
    }
  },
  { immediate: true },
)

onUnmounted(() => {
  if (tickTimer) clearInterval(tickTimer)
})

const elapsedLabel = computed(() => {
  if (!runStartedAt.value) return ''
  const secs = Math.max(0, Math.floor((now.value - runStartedAt.value) / 1000))
  if (secs < 60) return `${secs}s`
  return `${Math.floor(secs / 60)}m ${secs % 60}s`
})

const hasTools = computed(() => toolActivity.value.length > 0)
const hasRunningTool = computed(() => toolActivity.value.some(t => t.status === 'running'))

const showThinkingOnly = computed(
  () => isSending.value && !error.value && !streamingText.value && !hasTools.value,
)

const showToolFlow = computed(() => isSending.value && !error.value && hasTools.value && !streamingText.value)

const showAnswerStream = computed(() => isSending.value && !error.value && !!streamingText.value)

const runningTool = computed(() => toolActivity.value.find(t => t.status === 'running'))

const runningToolLabel = computed(() => {
  if (runningTool.value) {
    return formatToolLabel(
      runningTool.value.name,
      runningTool.value.argsJson,
      runningTool.value.output,
      runningTool.value.summary,
    )
  }
  return runStatus.value || 'Agent 正在工作'
})

const showRunSummary = computed(
  () => hasTools.value && !hasRunningTool.value && (!isSending.value || !!streamingText.value),
)
</script>

<template>
  <div v-if="isSending" class="live-progress" :class="{ 'live-progress--error': !!error }">
    <div v-if="showThinkingOnly" class="live-progress__thinking">
      <span class="live-progress__dot" aria-hidden="true" />
      <span class="live-progress__thinking-label">{{ runStatus || 'Thinking…' }}</span>
      <span v-if="elapsedLabel" class="live-progress__elapsed">{{ elapsedLabel }}</span>
    </div>

    <div v-else-if="showToolFlow" class="live-progress__flow">
      <div class="live-progress__status-bar">
        <span class="live-progress__status-label">{{ runningToolLabel }}</span>
        <span v-if="elapsedLabel" class="live-progress__elapsed">{{ elapsedLabel }}</span>
      </div>
      <ChatToolPanel :tools="toolActivity" :live="true" :default-expanded="true" />
    </div>

    <div v-else-if="showAnswerStream" class="live-progress__answer">
      <ChatToolPanel
        v-if="hasTools"
        :tools="toolActivity"
        :live="hasRunningTool"
        :default-expanded="true"
        :show-run-summary="showRunSummary"
      />
      <div class="live-progress__stream-body md" v-html="renderSimpleMarkdown(streamingText)" />
    </div>

    <p v-if="error" class="live-progress__error">{{ error }}</p>
  </div>
</template>

<style scoped>
.live-progress {
  align-self: stretch;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.live-progress--error .live-progress__error {
  border-radius: 8px;
  border: 1px solid color-mix(in srgb, var(--ide-error) 35%, var(--ide-border));
  background: color-mix(in srgb, var(--ide-error) 6%, var(--ide-surface-bg));
}

.live-progress__thinking,
.live-progress__status-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 0;
  font-size: 0.74rem;
  color: var(--ide-muted);
}

.live-progress__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--ide-accent);
  animation: pulse-dot 1.2s ease-in-out infinite;
}

.live-progress__thinking-label,
.live-progress__status-label {
  flex: 1;
  min-width: 0;
  color: var(--ide-fg);
  font-weight: 500;
}

.live-progress__elapsed {
  flex-shrink: 0;
  font-family: ui-monospace, monospace;
  font-size: 0.62rem;
  color: var(--ide-welcome-fg);
}

.live-progress__flow {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.live-progress__answer {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.live-progress__stream-body {
  font-size: 0.84rem;
  line-height: 1.6;
}

.live-progress__error {
  margin: 0;
  padding: 8px 10px;
  font-size: 0.72rem;
  color: var(--ide-error);
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
