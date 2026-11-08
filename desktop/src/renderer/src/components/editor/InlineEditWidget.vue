<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useInlineEditStore } from '@renderer/stores/inlineEditStore'

const store = useInlineEditStore()
const { open, instruction, previewText, status, error, anchor, selection } = storeToRefs(store)

const inputRef = ref<HTMLTextAreaElement | null>(null)

const widgetStyle = computed(() => {
  const rect = anchor.value
  if (!rect) {
    return { top: '40%', left: '50%', width: '420px', transform: 'translateX(-50%)' }
  }
  const maxLeft = Math.max(8, window.innerWidth - rect.width - 8)
  const left = Math.min(Math.max(8, rect.left), maxLeft)
  const top = Math.min(rect.top, window.innerHeight - 220)
  return {
    top: `${top}px`,
    left: `${left}px`,
    width: `${rect.width}px`,
  }
})

const canAccept = computed(() => status.value === 'done' && Boolean(previewText.value.trim()))
const isBusy = computed(() => status.value === 'streaming')

watch(open, async value => {
  if (!value) return
  await nextTick()
  inputRef.value?.focus()
})

function onInputKeyDown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault()
    void store.reject()
    return
  }
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    if (canAccept.value) {
      store.accept()
    } else if (!isBusy.value) {
      void store.submit()
    }
  }
}

function onGlobalKeyDown(event: KeyboardEvent) {
  if (!open.value) return
  if (event.key === 'Escape') {
    event.preventDefault()
    void store.reject()
  }
}

function onScrollOrResize() {
  if (open.value) store.refreshAnchor()
}

onMounted(() => {
  window.addEventListener('keydown', onGlobalKeyDown)
  window.addEventListener('resize', onScrollOrResize)
  window.addEventListener('scroll', onScrollOrResize, true)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onGlobalKeyDown)
  window.removeEventListener('resize', onScrollOrResize)
  window.removeEventListener('scroll', onScrollOrResize, true)
})
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="inline-edit-layer" @mousedown.self="store.reject()">
      <div class="inline-edit-widget" :style="widgetStyle" role="dialog" aria-label="Inline Edit">
        <header class="inline-edit-widget__header">
          <span class="inline-edit-widget__title">Inline Edit</span>
          <span v-if="selection" class="inline-edit-widget__hint">Ctrl+K · Enter 提交 · Esc 取消</span>
        </header>

        <textarea
          ref="inputRef"
          v-model="instruction"
          class="inline-edit-widget__input"
          rows="2"
          placeholder="描述要如何修改选中代码…"
          :disabled="isBusy"
          @keydown="onInputKeyDown"
        />

        <div v-if="previewText || isBusy" class="inline-edit-widget__preview">
          <div class="inline-edit-widget__preview-label">
            {{ isBusy ? '生成中…' : '预览' }}
          </div>
          <pre class="inline-edit-widget__preview-code">{{ previewText }}<span v-if="isBusy" class="inline-edit-widget__cursor">▍</span></pre>
        </div>

        <p v-if="error" class="inline-edit-widget__error">{{ error }}</p>

        <footer class="inline-edit-widget__actions">
          <button type="button" class="inline-edit-widget__btn" :disabled="isBusy" @click="store.reject()">
            取消
          </button>
          <button
            v-if="!canAccept"
            type="button"
            class="inline-edit-widget__btn inline-edit-widget__btn--primary"
            :disabled="isBusy || !instruction.trim()"
            @click="store.submit()"
          >
            {{ isBusy ? '生成中…' : '生成' }}
          </button>
          <button
            v-else
            type="button"
            class="inline-edit-widget__btn inline-edit-widget__btn--primary"
            @click="store.accept()"
          >
            接受 (Enter)
          </button>
        </footer>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.inline-edit-layer {
  position: fixed;
  inset: 0;
  z-index: 9000;
  pointer-events: auto;
}

.inline-edit-widget {
  position: fixed;
  z-index: 9001;
  border-radius: 8px;
  border: 1px solid var(--ide-border);
  background: var(--ide-surface-bg);
  box-shadow: 0 8px 32px var(--ide-shadow);
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.inline-edit-widget__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.inline-edit-widget__title {
  font-size: 12px;
  font-weight: 600;
  color: var(--ide-fg);
}

.inline-edit-widget__hint {
  font-size: 10px;
  color: var(--ide-muted);
}

.inline-edit-widget__input {
  width: 100%;
  resize: vertical;
  min-height: 44px;
  padding: 8px 10px;
  border-radius: 6px;
  border: 1px solid var(--ide-border);
  background: var(--ide-editor-bg);
  color: var(--ide-fg);
  font-size: 12px;
  font-family: inherit;
  line-height: 1.4;
}

.inline-edit-widget__input:focus {
  outline: none;
  border-color: color-mix(in srgb, var(--ide-accent) 50%, var(--ide-border));
}

.inline-edit-widget__preview {
  border-radius: 6px;
  border: 1px solid var(--ide-border);
  background: var(--ide-editor-bg);
  overflow: hidden;
}

.inline-edit-widget__preview-label {
  padding: 4px 8px;
  font-size: 10px;
  color: var(--ide-muted);
  border-bottom: 1px solid var(--ide-border);
}

.inline-edit-widget__preview-code {
  margin: 0;
  padding: 8px;
  max-height: 160px;
  overflow: auto;
  font-size: 11px;
  font-family: ui-monospace, monospace;
  color: var(--ide-fg);
  white-space: pre-wrap;
  word-break: break-word;
}

.inline-edit-widget__cursor {
  animation: inline-edit-blink 1s step-end infinite;
}

@keyframes inline-edit-blink {
  50% {
    opacity: 0;
  }
}

.inline-edit-widget__error {
  margin: 0;
  font-size: 11px;
  color: var(--ide-error);
}

.inline-edit-widget__actions {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
}

.inline-edit-widget__btn {
  padding: 4px 12px;
  border-radius: 4px;
  border: 1px solid var(--ide-border);
  background: var(--ide-surface-bg);
  color: var(--ide-fg);
  font-size: 11px;
  cursor: pointer;
}

.inline-edit-widget__btn:hover:not(:disabled) {
  background: var(--ide-hover-overlay);
}

.inline-edit-widget__btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.inline-edit-widget__btn--primary {
  border-color: color-mix(in srgb, var(--ide-accent) 40%, var(--ide-border));
  background: color-mix(in srgb, var(--ide-accent) 14%, var(--ide-surface-bg));
  color: var(--ide-accent);
}
</style>
