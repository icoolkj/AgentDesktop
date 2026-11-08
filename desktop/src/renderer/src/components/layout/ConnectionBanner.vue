<script setup lang="ts">
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useAgentStore } from '@renderer/stores/agentStore'
import { useChatStore } from '@renderer/stores/chatStore'

const agentStore = useAgentStore()
const chatStore = useChatStore()
const { status, error: agentError } = storeToRefs(agentStore)

const banner = computed(() => {
  if (status.value === 'connected') return null
  if (agentError.value) {
    return { kind: status.value === 'connecting' ? 'warn' : 'error', text: agentError.value }
  }
  if (status.value === 'connecting') {
    return { kind: 'warn', text: '正在连接 Agent 后端…' }
  }
  if (status.value === 'disconnected') {
    return { kind: 'error', text: '与 Agent 后端断开，正在尝试重连' }
  }
  if (status.value === 'error') {
    return { kind: 'error', text: 'Agent 连接失败' }
  }
  return null
})

async function onReconnect() {
  await chatStore.reconnect()
}

async function onRestart() {
  await chatStore.restart()
}
</script>

<template>
  <div v-if="banner" class="conn-banner" :class="`conn-banner--${banner.kind}`">
    <span class="conn-banner__text">{{ banner.text }}</span>
    <div class="conn-banner__actions">
      <button type="button" class="conn-banner__btn" @click="onReconnect">重连</button>
      <button type="button" class="conn-banner__btn conn-banner__btn--primary" @click="onRestart">
        重启 Agent
      </button>
    </div>
  </div>
</template>

<style scoped>
.conn-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 12px;
  font-size: 0.78rem;
  border-bottom: 1px solid transparent;
  flex-shrink: 0;
}

.conn-banner--warn {
  background: color-mix(in srgb, var(--ide-warning) 12%, transparent);
  color: var(--ide-warning);
  border-bottom-color: color-mix(in srgb, var(--ide-warning) 25%, transparent);
}

.conn-banner--error {
  background: color-mix(in srgb, var(--ide-error) 12%, transparent);
  color: var(--ide-error);
  border-bottom-color: color-mix(in srgb, var(--ide-error) 25%, transparent);
}

.conn-banner__text {
  line-height: 1.4;
}

.conn-banner__actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.conn-banner__btn {
  padding: 3px 10px;
  border-radius: 5px;
  border: 1px solid var(--ide-button-border);
  background: transparent;
  color: inherit;
  font-size: 0.72rem;
  cursor: pointer;
}

.conn-banner__btn:hover {
  background: var(--ide-hover-overlay);
}

.conn-banner__btn--primary {
  border-color: color-mix(in srgb, var(--ide-accent) 50%, var(--ide-border));
  color: var(--ide-accent);
}
</style>
