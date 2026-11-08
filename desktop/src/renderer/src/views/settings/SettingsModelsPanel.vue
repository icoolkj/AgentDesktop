<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'

import { useSettingsStore } from '@renderer/stores/settingsStore'
import { useModelSettingsStore } from '@renderer/stores/modelSettingsStore'
import '@renderer/modules/settings/settings.css'

const settings = useSettingsStore()
const modelSettings = useModelSettingsStore()
const { saving, error, success } = storeToRefs(settings)
const { catalogLoading } = storeToRefs(modelSettings)

const searchQuery = ref('')
const addModelInput = ref('')
const defaultModelDraft = ref('')

const filteredRows = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  const rows = modelSettings.allModelRows()
  if (!q) return rows
  return rows.filter(row => row.id.toLowerCase().includes(q))
})

async function refreshModels(force = false): Promise<void> {
  const ok = await modelSettings.ensureModelCatalog({ force })
  if (!ok) return
  if (modelSettings.defaultModel) {
    defaultModelDraft.value = modelSettings.defaultModel
  }
}

async function saveDefault(): Promise<void> {
  const id = defaultModelDraft.value.trim()
  if (!id) return
  const ok = await settings.saveDefaultModel(id)
  if (ok) {
    modelSettings.setRemoteModels([...modelSettings.remoteModels], id)
    void modelSettings.ensureModelCatalog({ force: true })
  }
}

function onAddModel(): void {
  const id = addModelInput.value.trim()
  if (!id) return
  if (modelSettings.addCustomModel(id)) {
    addModelInput.value = ''
  }
}

function onToggle(id: string, event: Event): void {
  const checked = (event.target as HTMLInputElement).checked
  modelSettings.toggleModelEnabled(id, checked)
}

onMounted(async () => {
  await settings.loadLlmSettings()
  if (settings.llm?.model) {
    defaultModelDraft.value = settings.llm.model
  }
  await refreshModels(false)
})
</script>

<template>
  <div class="settings-panel settings-models">
    <header class="settings-models__header">
      <div>
        <h2>Models</h2>
        <p class="settings-panel__hint">
          从 CPA <code>GET /v1/models</code> 拉取可用模型。启用后可在 Agent 输入框选择；<strong>Auto</strong> 使用下方默认模型。
        </p>
      </div>
    </header>

    <p v-if="error" class="settings-alert settings-alert--error">{{ error }}</p>
    <p v-if="success" class="settings-alert settings-alert--success">{{ success }}</p>

    <section class="settings-section settings-models__default">
      <h3 class="settings-subsection">默认模型（Auto 时使用）</h3>
      <div class="settings-models__default-row">
        <input
          v-model="defaultModelDraft"
          type="text"
          class="settings-input settings-models__default-input"
          placeholder="claude-sonnet-4-20250514"
          list="model-suggestions"
        />
        <button
          type="button"
          class="settings-btn settings-btn--primary"
          :disabled="saving || !defaultModelDraft.trim()"
          @click="saveDefault"
        >
          {{ saving ? '保存中…' : '设为默认' }}
        </button>
      </div>
      <p v-if="modelSettings.defaultModel" class="settings-section__hint">
        当前默认：<code>{{ modelSettings.defaultModel }}</code>
      </p>
    </section>

    <section class="settings-section">
      <div class="settings-models__toolbar">
        <input
          v-model="searchQuery"
          type="search"
          class="settings-input settings-models__search"
          placeholder="Add or search model"
        />
        <div class="settings-models__toolbar-actions">
          <button
            type="button"
            class="settings-btn"
            :disabled="catalogLoading"
            title="刷新列表"
            @click="refreshModels(true)"
          >
            ↻
          </button>
        </div>
      </div>

      <div class="settings-models__add">
        <input
          v-model="addModelInput"
          type="text"
          class="settings-input"
          placeholder="手动添加模型 ID"
          @keydown.enter.prevent="onAddModel"
        />
        <button type="button" class="settings-btn" :disabled="!addModelInput.trim()" @click="onAddModel">
          添加
        </button>
      </div>

      <ul v-if="filteredRows.length" class="settings-models__list">
        <li v-for="row in filteredRows" :key="row.id" class="settings-models__row">
          <div class="settings-models__row-main">
            <span class="settings-models__name">{{ row.id }}</span>
            <span v-if="row.custom" class="settings-models__tag">自定义</span>
            <span v-if="row.ownedBy" class="settings-models__owned">{{ row.ownedBy }}</span>
            <span v-if="row.id === modelSettings.defaultModel" class="settings-models__tag settings-models__tag--default">
              默认
            </span>
          </div>
          <label class="settings-models__toggle" :title="modelSettings.isModelEnabled(row.id) ? '已启用' : '已禁用'">
            <input
              type="checkbox"
              :checked="modelSettings.isModelEnabled(row.id)"
              @change="onToggle(row.id, $event)"
            />
            <span class="settings-models__switch" />
          </label>
        </li>
      </ul>

      <p v-else-if="!catalogLoading" class="settings-section__hint">
        暂无模型。请确认 LLM 已配置且 CPA 可访问，然后点击刷新。
      </p>
      <p v-if="catalogLoading" class="settings-section__hint">正在从 CPA 加载模型列表…</p>
    </section>

    <datalist id="model-suggestions">
      <option v-for="row in modelSettings.allModelRows()" :key="row.id" :value="row.id" />
    </datalist>
  </div>
</template>

<style scoped>
.settings-models__header h2 {
  margin: 0 0 6px;
  font-size: 1.25rem;
  font-weight: 600;
}

.settings-models__default-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.settings-models__default-input {
  flex: 1;
  min-width: 0;
}

.settings-models__toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.settings-models__search {
  flex: 1;
  min-width: 0;
}

.settings-models__toolbar-actions {
  display: flex;
  gap: 6px;
}

.settings-models__add {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}

.settings-models__add .settings-input {
  flex: 1;
}

.settings-models__list {
  list-style: none;
  margin: 0;
  padding: 0;
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  overflow: hidden;
}

.settings-models__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--ide-border);
}

.settings-models__row:last-child {
  border-bottom: none;
}

.settings-models__row-main {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.settings-models__name {
  font-family: ui-monospace, monospace;
  font-size: 0.82rem;
  word-break: break-all;
}

.settings-models__owned {
  font-size: 0.68rem;
  color: var(--ide-muted);
}

.settings-models__tag {
  font-size: 0.62rem;
  padding: 1px 6px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--ide-border) 80%, transparent);
  color: var(--ide-muted);
}

.settings-models__tag--default {
  background: color-mix(in srgb, var(--ide-accent) 20%, transparent);
  color: var(--ide-accent);
}

.settings-models__toggle {
  position: relative;
  display: inline-flex;
  align-items: center;
  cursor: pointer;
  flex-shrink: 0;
}

.settings-models__toggle input {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
}

.settings-models__switch {
  width: 36px;
  height: 20px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--ide-border) 70%, var(--ide-muted));
  transition: background 0.15s ease;
  position: relative;
}

.settings-models__switch::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #fff;
  transition: transform 0.15s ease;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
}

.settings-models__toggle input:checked + .settings-models__switch {
  background: var(--ide-success);
}

.settings-models__toggle input:checked + .settings-models__switch::after {
  transform: translateX(16px);
}
</style>
