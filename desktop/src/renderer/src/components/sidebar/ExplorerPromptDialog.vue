<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useExplorerPromptStore } from '@renderer/stores/explorerPromptStore'

const prompt = useExplorerPromptStore()
const { open, mode, title, message, value, placeholder, confirmLabel } = storeToRefs(prompt)

const inputRef = ref<HTMLInputElement | null>(null)

const isConfirm = computed(() => mode.value === 'confirm')

watch(open, async isOpen => {
  if (!isOpen || isConfirm.value) return
  await nextTick()
  inputRef.value?.focus()
  inputRef.value?.select()
})

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter') {
    event.preventDefault()
    prompt.submit()
  } else if (event.key === 'Escape') {
    event.preventDefault()
    prompt.dismiss()
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="explorer-prompt-backdrop" @click.self="prompt.dismiss()">
      <form class="explorer-prompt" @submit.prevent="prompt.submit()">
        <h3 class="explorer-prompt__title">{{ title }}</h3>
        <p v-if="isConfirm && message" class="explorer-prompt__message">{{ message }}</p>
        <input
          v-if="!isConfirm"
          ref="inputRef"
          v-model="value"
          type="text"
          class="explorer-prompt__input"
          :placeholder="placeholder"
          spellcheck="false"
          @keydown="onKeydown"
        />
        <div class="explorer-prompt__actions">
          <button type="button" class="explorer-prompt__btn" @click="prompt.dismiss()">
            {{ isConfirm ? '取消' : '取消' }}
          </button>
          <button type="submit" class="explorer-prompt__btn explorer-prompt__btn--primary">
            {{ isConfirm ? confirmLabel : '确定' }}
          </button>
        </div>
      </form>
    </div>
  </Teleport>
</template>

<style scoped>
.explorer-prompt-backdrop {
  position: fixed;
  inset: 0;
  z-index: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.45);
}

.explorer-prompt {
  width: min(360px, calc(100vw - 32px));
  padding: 16px;
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  background: var(--ide-dropdown-bg, #252526);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.5);
}

.explorer-prompt__title {
  margin: 0 0 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--ide-fg);
}

.explorer-prompt__message {
  margin: 0 0 14px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--ide-muted);
}

.explorer-prompt__input {
  width: 100%;
  height: 30px;
  padding: 0 10px;
  border: 1px solid var(--ide-border);
  border-radius: 4px;
  background: var(--ide-input-bg);
  color: var(--ide-fg);
  font-size: 13px;
}

.explorer-prompt__input:focus {
  outline: 1px solid var(--ide-accent);
  outline-offset: -1px;
}

.explorer-prompt__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 14px;
}

.explorer-prompt__btn {
  padding: 6px 14px;
  border: 1px solid var(--ide-border);
  border-radius: 4px;
  background: transparent;
  color: var(--ide-fg);
  font-size: 12px;
  cursor: pointer;
}

.explorer-prompt__btn:hover {
  background: rgba(255, 255, 255, 0.06);
}

.explorer-prompt__btn--primary {
  background: var(--ide-accent);
  border-color: var(--ide-accent);
  color: #fff;
}

.explorer-prompt__btn--primary:hover {
  filter: brightness(1.08);
}
</style>
