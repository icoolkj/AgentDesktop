<script setup lang="ts">
import { onMounted } from 'vue'
import { storeToRefs } from 'pinia'

import { useSettingsStore } from '@renderer/stores/settingsStore'
import { useAgentStore } from '@renderer/stores/agentStore'
import '@renderer/modules/settings/settings.css'

const settings = useSettingsStore()
const agent = useAgentStore()
const { status, wsUrl, error: agentError } = storeToRefs(agent)
const {
  loading,
  testing,
  error,
  success,
  manualBackendUrl,
  httpBaseFromAgent,
  effectiveHttpBase,
  backendReachable,
} = storeToRefs(settings)

async function onReload(): Promise<void> {
  await settings.loadLlmSettings()
}

async function onReconnect(): Promise<void> {
  await agent.reconnect()
  settings.syncBackendFromAgent()
  await onReload()
}

onMounted(async () => {
  await onReload()
})
</script>

<template>
  <div class="settings-panel">
    <section class="settings-section">
      <h2>Agent 后端</h2>
      <p class="settings-section__hint">
        配置通过 Java 后端 HTTP 保存。WebSocket 断开时 HTTP 仍可能可用 — 请在 IDEA 控制台确认
        <code>AGENT_READY ... port</code> 中的地址与端口。
      </p>

      <div class="settings-status-row">
        <span class="badge" :class="`badge-${status}`">WS: {{ status }}</span>
        <span v-if="backendReachable === true" class="settings-reach settings-reach--ok">
          HTTP: reachable
        </span>
        <span v-else-if="backendReachable === false" class="settings-reach settings-reach--bad">
          HTTP: unreachable
        </span>
        <code v-if="wsUrl" class="settings-status-row__ws">{{ wsUrl }}</code>
      </div>

      <p v-if="agentError && status !== 'connected'" class="settings-note">
        {{ agentError }}。若下方 Backend URL 正确，仍可配置 LLM。
      </p>

      <p v-if="error" class="settings-alert settings-alert--error">{{ error }}</p>
      <p v-if="success" class="settings-alert settings-alert--success">{{ success }}</p>

      <label class="settings-field">
        <span>Backend URL</span>
        <input
          :value="manualBackendUrl"
          type="url"
          placeholder="http://127.0.0.1:8080"
          @input="settings.setManualBackendUrl(($event.target as HTMLInputElement).value)"
        />
      </label>
      <p v-if="effectiveHttpBase" class="settings-section__hint">
        当前使用 <code>{{ effectiveHttpBase }}</code>
      </p>

      <div class="settings-inline-actions">
        <button
          type="button"
          class="settings-btn"
          :disabled="!httpBaseFromAgent"
          @click="settings.syncBackendFromAgent()"
        >
          使用 WS 地址
        </button>
        <button
          type="button"
          class="settings-btn"
          :disabled="testing"
          @click="settings.testBackendConnection()"
        >
          {{ testing ? '测试中…' : '测试连接' }}
        </button>
        <button type="button" class="settings-btn" @click="onReconnect">重连 Agent</button>
        <button type="button" class="settings-btn" :disabled="loading" @click="onReload">
          {{ loading ? '加载中…' : '重新加载' }}
        </button>
      </div>
    </section>
  </div>
</template>
