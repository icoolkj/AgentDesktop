<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ChatMessage } from '@renderer/stores/chatStore'

export type RewindMode = 'both' | 'conversation' | 'code'

const props = defineProps<{
  open: boolean
  messages: ChatMessage[]
  busy?: boolean
}>()

const emit = defineEmits<{
  close: []
  confirm: [payload: { messageUuid: string; mode: RewindMode }]
}>()

const selectedUuid = ref<string | null>(null)
const mode = ref<RewindMode>('both')

const userMessages = computed(() =>
  props.messages.filter(m => m.role === 'user' && m.messageUuid),
)

function onConfirm() {
  if (!selectedUuid.value) return
  emit('confirm', { messageUuid: selectedUuid.value, mode: mode.value })
}

function onClose() {
  emit('close')
}

function preview(text: string): string {
  const flat = text.replace(/\s+/g, ' ').trim()
  return flat.length > 120 ? `${flat.slice(0, 117)}…` : flat
}
</script>

<template>
  <div v-if="open" class="rewind-overlay" @click.self="onClose">
    <div class="rewind-dialog" role="dialog" aria-labelledby="rewind-title">
      <header class="rewind-dialog__header">
        <h2 id="rewind-title">回退到消息</h2>
        <button type="button" class="rewind-dialog__close" aria-label="关闭" @click="onClose">×</button>
      </header>

      <p class="rewind-dialog__hint">
        选择一条用户消息，撤销该消息之后的 Agent 修改和/或对话记录（Claude Code /rewind）。
      </p>

      <div class="rewind-dialog__modes">
        <label v-for="opt in ([
          { id: 'both', label: '对话 + 代码' },
          { id: 'conversation', label: '仅对话' },
          { id: 'code', label: '仅代码' },
        ] as const)" :key="opt.id" class="rewind-dialog__mode">
          <input v-model="mode" type="radio" name="rewind-mode" :value="opt.id" />
          {{ opt.label }}
        </label>
      </div>

      <ul class="rewind-dialog__list">
        <li v-for="(msg, idx) in userMessages" :key="msg.messageUuid!">
          <button
            type="button"
            class="rewind-dialog__item"
            :class="{ 'rewind-dialog__item--active': selectedUuid === msg.messageUuid }"
            @click="selectedUuid = msg.messageUuid!"
          >
            <span class="rewind-dialog__idx">#{{ idx + 1 }}</span>
            <span class="rewind-dialog__text">{{ preview(msg.content) }}</span>
          </button>
        </li>
      </ul>

      <footer class="rewind-dialog__footer">
        <button type="button" class="rewind-dialog__btn" @click="onClose">取消</button>
        <button
          type="button"
          class="rewind-dialog__btn rewind-dialog__btn--primary"
          :disabled="!selectedUuid || busy"
          @click="onConfirm"
        >
          {{ busy ? '回退中…' : '确认回退' }}
        </button>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.rewind-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: color-mix(in srgb, var(--ide-shadow) 55%, transparent);
  backdrop-filter: blur(2px);
}

.rewind-dialog {
  width: min(520px, 92vw);
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  background: var(--ide-surface-bg);
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  box-shadow: 0 8px 32px var(--ide-shadow);
}

.rewind-dialog__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--ide-border);
}

.rewind-dialog__header h2 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--ide-fg);
}

.rewind-dialog__close {
  border: none;
  background: transparent;
  color: var(--ide-muted);
  font-size: 20px;
  cursor: pointer;
}

.rewind-dialog__close:hover {
  color: var(--ide-fg);
}

.rewind-dialog__hint {
  margin: 0;
  padding: 10px 16px;
  font-size: 12px;
  color: var(--ide-muted);
}

.rewind-dialog__modes {
  display: flex;
  gap: 12px;
  padding: 0 16px 10px;
  font-size: 12px;
  color: var(--ide-fg);
}

.rewind-dialog__mode {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
}

.rewind-dialog__list {
  list-style: none;
  margin: 0;
  padding: 0 8px;
  overflow: auto;
  flex: 1;
  min-height: 120px;
}

.rewind-dialog__item {
  width: 100%;
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 8px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--ide-fg);
  text-align: left;
  cursor: pointer;
  font-size: 12px;
}

.rewind-dialog__item:hover,
.rewind-dialog__item--active {
  background: var(--ide-hover-overlay);
  border-color: var(--ide-accent);
}

.rewind-dialog__idx {
  flex-shrink: 0;
  color: var(--ide-muted);
  font-variant-numeric: tabular-nums;
}

.rewind-dialog__footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--ide-border);
}

.rewind-dialog__btn {
  padding: 6px 12px;
  border-radius: 4px;
  border: 1px solid var(--ide-button-border);
  background: transparent;
  color: var(--ide-fg);
  cursor: pointer;
  font-size: 12px;
}

.rewind-dialog__btn:hover:not(:disabled) {
  background: var(--ide-hover-overlay);
}

.rewind-dialog__btn--primary {
  background: var(--ide-button-primary-bg);
  border-color: var(--ide-button-primary-bg);
  color: var(--ide-button-primary-fg);
}

.rewind-dialog__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
