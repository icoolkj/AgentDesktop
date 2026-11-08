<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import ChatTurnContent from '@renderer/components/chat/ChatTurnContent.vue'
import type { HistoryTurn, TurnPreview } from '@renderer/utils/turnPreview'
import { formatMessageTime } from '@renderer/utils/sessionFormat'
import '@renderer/styles/chat-markdown.css'

defineProps<{
  turn: HistoryTurn
  index: number
  preview: TurnPreview
}>()

const emit = defineEmits<{
  close: []
}>()

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('close')
}

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
  document.body.style.overflow = 'hidden'
})

onUnmounted(() => {
  document.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <div class="history-detail-overlay" @click.self="emit('close')">
      <div class="history-detail" role="dialog" aria-modal="true" aria-label="历史对话详情">
        <header class="history-detail__header">
          <div class="history-detail__title-wrap">
            <span class="history-detail__index">#{{ index }}</span>
            <span v-if="preview.slashLabel" class="history-detail__slash">{{ preview.slashLabel }}</span>
            <h3 class="history-detail__title">{{ preview.title }}</h3>
          </div>
          <div class="history-detail__actions">
            <span v-if="turn.user?.createdAt" class="history-detail__time">
              {{ formatMessageTime(turn.user.createdAt) }}
            </span>
            <button type="button" class="history-detail__close" @click="emit('close')">关闭</button>
          </div>
        </header>

        <div class="history-detail__body">
          <ChatTurnContent :user="turn.user" :assistants="turn.assistants" />
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.history-detail-overlay {
  position: fixed;
  inset: 0;
  z-index: 9000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
  background: color-mix(in srgb, var(--ide-bg, #111) 55%, transparent);
  backdrop-filter: blur(2px);
}

.history-detail {
  display: flex;
  flex-direction: column;
  width: min(920px, 96vw);
  max-height: min(88vh, 860px);
  border-radius: 12px;
  border: 1px solid var(--ide-border);
  background: var(--ide-sidebar-bg);
  box-shadow: 0 16px 48px color-mix(in srgb, #000 28%, transparent);
  overflow: hidden;
}

.history-detail__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--ide-border);
  background: color-mix(in srgb, var(--ide-surface-bg) 80%, var(--ide-sidebar-bg));
  flex-shrink: 0;
}

.history-detail__title-wrap {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.history-detail__index {
  font-size: 0.68rem;
  font-family: ui-monospace, monospace;
  color: var(--ide-muted);
}

.history-detail__slash {
  font-size: 0.62rem;
  padding: 1px 6px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--ide-accent) 14%, transparent);
  color: var(--ide-accent);
  font-family: ui-monospace, monospace;
}

.history-detail__title {
  margin: 0;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--ide-fg);
  line-height: 1.4;
  word-break: break-word;
}

.history-detail__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.history-detail__time {
  font-size: 0.62rem;
  color: var(--ide-muted);
}

.history-detail__close {
  padding: 4px 10px;
  border-radius: 6px;
  border: 1px solid var(--ide-border);
  background: var(--ide-button-bg);
  color: var(--ide-fg);
  font-size: 0.72rem;
  cursor: pointer;
}

.history-detail__close:hover {
  background: var(--ide-hover-overlay);
}

.history-detail__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px 16px 18px;
}
</style>
