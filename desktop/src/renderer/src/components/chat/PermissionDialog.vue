<script setup lang="ts">
import { ref } from 'vue'
import type { PermissionRequestEvent } from '@agent-desktop/shared-types'
import { useAgentSettingsStore } from '@renderer/stores/agentSettingsStore'

defineProps<{
  request: PermissionRequestEvent
}>()

const emit = defineEmits<{
  reply: [allowed: boolean, remember?: boolean]
  dismiss: []
}>()

const agentSettings = useAgentSettingsStore()
const busy = ref(false)

async function onAllow(remember = false) {
  if (busy.value) return
  busy.value = true
  if (remember) {
    agentSettings.setPermissionMode('auto')
  }
  emit('reply', true, remember)
}

function onDeny() {
  if (busy.value) return
  busy.value = true
  emit('reply', false)
}
</script>

<template>
  <div class="perm-overlay" role="dialog" aria-modal="true" @click.self="emit('dismiss')">
    <div class="perm-dialog">
      <header class="perm-dialog__header">
        <h2>允许 Agent 修改文件？</h2>
        <p class="perm-dialog__mode">当前模式：{{ agentSettings.permissionModeLabel }}</p>
      </header>

      <div class="perm-dialog__body">
        <p class="perm-dialog__tool">{{ request.toolName }}</p>
        <p v-if="request.path" class="perm-dialog__path" :title="request.path">{{ request.path }}</p>
        <p class="perm-dialog__summary">{{ request.summary }}</p>
      </div>

      <footer class="perm-dialog__actions">
        <button type="button" class="perm-dialog__btn" :disabled="busy" @click="onDeny">拒绝</button>
        <button type="button" class="perm-dialog__btn perm-dialog__btn--primary" :disabled="busy" @click="onAllow(false)">
          允许
        </button>
        <button
          type="button"
          class="perm-dialog__btn perm-dialog__btn--ghost"
          :disabled="busy"
          @click="onAllow(true)"
        >
          始终允许写文件
        </button>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.perm-overlay {
  position: fixed;
  inset: 0;
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: color-mix(in srgb, var(--ide-shadow) 55%, transparent);
  backdrop-filter: blur(2px);
}

.perm-dialog {
  width: min(420px, calc(100vw - 32px));
  border-radius: 10px;
  border: 1px solid var(--ide-border);
  background: var(--ide-surface-bg);
  box-shadow: 0 12px 40px var(--ide-shadow);
}

.perm-dialog__header {
  padding: 16px 18px 8px;
}

.perm-dialog__header h2 {
  margin: 0;
  font-size: 0.95rem;
  color: var(--ide-fg);
}

.perm-dialog__mode {
  margin: 6px 0 0;
  font-size: 0.72rem;
  color: var(--ide-muted);
}

.perm-dialog__body {
  padding: 8px 18px 16px;
}

.perm-dialog__tool {
  margin: 0 0 4px;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--ide-accent);
}

.perm-dialog__path {
  margin: 0 0 8px;
  font-size: 0.72rem;
  font-family: ui-monospace, monospace;
  color: var(--ide-fg);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.perm-dialog__summary {
  margin: 0;
  font-size: 0.78rem;
  color: var(--ide-muted);
}

.perm-dialog__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
  padding: 12px 18px 16px;
  border-top: 1px solid var(--ide-border);
}

.perm-dialog__btn {
  padding: 6px 12px;
  border-radius: 6px;
  border: 1px solid var(--ide-button-border);
  background: transparent;
  color: var(--ide-fg);
  font-size: 0.75rem;
  cursor: pointer;
}

.perm-dialog__btn:hover:not(:disabled) {
  background: var(--ide-hover-overlay);
}

.perm-dialog__btn--primary {
  border-color: var(--ide-accent);
  background: var(--ide-button-primary-bg);
  color: var(--ide-button-primary-fg);
}

.perm-dialog__btn--ghost {
  border-color: transparent;
  color: var(--ide-muted);
}

.perm-dialog__btn:disabled {
  opacity: 0.5;
  cursor: default;
}
</style>
