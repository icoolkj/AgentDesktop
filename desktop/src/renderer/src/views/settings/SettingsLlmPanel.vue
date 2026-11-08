<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { storeToRefs } from 'pinia'

import { useSettingsStore } from '@renderer/stores/settingsStore'
import { API_KEY_MASK, type LlmSettings } from '@renderer/types/settings'
import '@renderer/modules/settings/settings.css'

const settings = useSettingsStore()
const { saving, error, success } = storeToRefs(settings)

const apiKeyInput = ref('')
const secretsAvailable = ref(false)

const form = reactive<LlmSettings>({
  enabled: true,
  provider: 'openai-compatible',
  baseUrl: 'http://127.0.0.1:8317/v1',
  apiKey: '',
  model: 'gpt-4o-mini',
  temperature: 0.7,
  maxTokens: 4096,
  timeoutSeconds: 120,
  streamToolTurns: false,
  streamAnswerTurns: false,
  configured: false,
  apiKeySet: false,
})

function applyLoaded(settingsDto: LlmSettings): void {
  Object.assign(form, settingsDto)
  apiKeyInput.value = settingsDto.apiKeySet ? API_KEY_MASK : ''
}

async function onSave(): Promise<void> {
  settings.persistManualBackendUrl()
  const ok = await settings.saveLlmSettings(form, apiKeyInput.value)
  if (ok && settings.llm) {
    applyLoaded(settings.llm)
  }
}

async function onReload(): Promise<void> {
  await settings.loadLlmSettings()
  if (settings.llm) {
    applyLoaded(settings.llm)
  }
}

onMounted(async () => {
  secretsAvailable.value = await window.secretsApi?.isAvailable?.().catch(() => false)
  await onReload()
})
</script>

<template>
  <div class="settings-panel">
    <p v-if="error" class="settings-alert settings-alert--error">{{ error }}</p>
    <p v-if="success" class="settings-alert settings-alert--success">{{ success }}</p>

    <form @submit.prevent="onSave">
      <section class="settings-section">
        <h2>LLM 提供商</h2>
        <p class="settings-section__hint">
          推荐组合：<strong>CLIProxyAPI-Java</strong>（<code>http://127.0.0.1:8317/v1</code>）+
          Agent Desktop。CPA 负责原生 <code>tool_calls</code> 协议转换，Agent 只执行工具。
        </p>

        <label class="settings-field settings-field--checkbox">
          <input v-model="form.enabled" type="checkbox" />
          启用 LLM
        </label>

        <label class="settings-field">
          <span>LLM Base URL</span>
          <input v-model="form.baseUrl" type="url" placeholder="http://127.0.0.1:8317/v1" required />
        </label>

        <label class="settings-field">
          <span>API Key</span>
          <input
            v-model="apiKeyInput"
            type="password"
            :placeholder="form.apiKeySet ? '已保存在 MySQL — 仅替换时填写' : 'sk-...'"
            autocomplete="off"
          />
        </label>
        <p v-if="form.apiKeySet" class="settings-section__hint settings-section__hint--ok">
          API Key 已保存在服务端（MySQL）。仅修改其他项时可留空。
          <span v-if="secretsAvailable"> 本地 OS 密钥链可用（Electron safeStorage）。</span>
        </p>

        <label class="settings-field">
          <span>Model</span>
          <input v-model="form.model" type="text" placeholder="gpt-4o-mini" required />
          <small class="settings-field__note">也可在 <RouterLink to="/settings/agent/models">Models</RouterLink> 中从 CPA 列表选择并设为默认。</small>
        </label>

        <div class="settings-field-row">
          <label class="settings-field">
            <span>Temperature</span>
            <input v-model.number="form.temperature" type="number" min="0" max="2" step="0.1" />
          </label>
          <label class="settings-field">
            <span>Max tokens</span>
            <input v-model.number="form.maxTokens" type="number" min="256" max="128000" step="256" />
          </label>
          <label class="settings-field">
            <span>Timeout (s)</span>
            <input v-model.number="form.timeoutSeconds" type="number" min="10" max="600" step="10" />
          </label>
        </div>

        <h3 class="settings-subsection">Agent 流式策略</h3>
        <p class="settings-section__hint">
          工具调用只认 CPA 返回的原生 <code>tool_calls</code>。流式选项仅影响 SSE 展示，不影响工具解析。
          CPA「Tool 调用测试」完整两轮建议两项都关；需要打字机效果可只开「回答流式」。
        </p>

        <label class="settings-field settings-field--checkbox">
          <input v-model="form.streamToolTurns" type="checkbox" />
          工具轮流式（tools=auto，仅调试 SSE；完整两轮建议关）
        </label>

        <label class="settings-field settings-field--checkbox">
          <input v-model="form.streamAnswerTurns" type="checkbox" />
          回答流式（tool_choice=none；DeepSeek 建议关，失败会自动降级非流式）
        </label>
      </section>

      <div class="settings-form__meta">
        <span v-if="form.configured" class="settings-form__ok">LLM 已在服务端配置</span>
        <span v-else class="settings-form__warn">尚未配置 LLM — 请填写 API Key 并保存</span>
        <span v-if="form.settingsPath" class="settings-form__path" :title="form.settingsPath">
          持久化在服务端
        </span>
      </div>

      <div class="settings-form__actions">
        <button type="button" class="settings-btn" :disabled="saving" @click="onReload">
          {{ saving ? '加载中…' : '重新加载' }}
        </button>
        <button type="submit" class="settings-btn settings-btn--primary" :disabled="saving">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </div>
    </form>
  </div>
</template>
