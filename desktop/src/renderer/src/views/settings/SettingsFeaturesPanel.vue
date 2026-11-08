<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { RouterLink } from 'vue-router'

import { useSettingsStore } from '@renderer/stores/settingsStore'
import { useWorkspaceStore } from '@renderer/stores/workspaceStore'
import { defaultFeatureSettings, type FeatureSettings, type HooksSettings } from '@renderer/types/featureSettings'
import '@renderer/modules/settings/settings.css'

const settings = useSettingsStore()
const workspace = useWorkspaceStore()
const { saving, loading, error, success, extensions } = storeToRefs(settings)

const form = reactive<FeatureSettings>(defaultFeatureSettings())
const storageLabel = ref('')
const hooksJson = ref('[]')
const hooksStorageLabel = ref('')

const workspaceHint = computed(() =>
  workspace.rootPath ? workspace.rootPath : '未打开工作区 — 斜杠命令列表可能为空',
)

async function loadHooks(): Promise<void> {
  const data = await settings.loadHooksSettings()
  if (data) {
    hooksJson.value = JSON.stringify(data.hooks ?? [], null, 2)
    hooksStorageLabel.value = data.storage ?? ''
  }
}

async function saveHooks(): Promise<void> {
  try {
    const parsed = JSON.parse(hooksJson.value) as unknown
    if (!Array.isArray(parsed)) {
      settings.error = 'Hooks JSON 必须是数组'
      return
    }
    const ok = await settings.saveHooksSettings(parsed as HooksSettings['hooks'])
    if (ok) await loadHooks()
  } catch {
    settings.error = 'Hooks JSON 格式无效'
  }
}

function applyLoaded(data: FeatureSettings): void {
  Object.assign(form, defaultFeatureSettings(), data)
  storageLabel.value = data.storage ?? ''
}

async function reload(): Promise<void> {
  const data = await settings.loadFeatureSettings()
  if (data) applyLoaded(data)
  await settings.loadExtensionInfo(workspace.rootPath)
  await loadHooks()
}

async function save(): Promise<void> {
  const ok = await settings.saveFeatureSettings({ ...form })
  if (ok && settings.features) applyLoaded(settings.features)
  await settings.loadExtensionInfo(workspace.rootPath)
}

onMounted(() => {
  void reload()
})
</script>

<template>
  <div class="settings-panel">
    <p v-if="error" class="settings-alert settings-alert--error">{{ error }}</p>
    <p v-if="success" class="settings-alert settings-alert--success">{{ success }}</p>

    <section class="settings-section">
      <h2>扩展引擎</h2>
      <p class="settings-section__hint">
        控制 Task 子 Agent、WebSearch、WebFetch、Bash 沙箱等运行时开关。插件与 Skills 请分别在
        <RouterLink to="/settings/agent/plugins">插件</RouterLink>、
        <RouterLink to="/settings/agent/skills">Skills</RouterLink> 页管理。
      </p>
      <p v-if="storageLabel" class="settings-section__hint settings-section__hint--path">{{ storageLabel }}</p>

      <h3 class="settings-subsection">子 Agent（Task 工具）</h3>
      <label class="settings-field settings-field--checkbox">
        <input v-model="form.subagentsEnabled" type="checkbox" />
        启用 Task 子 Agent
      </label>
      <label class="settings-field">
        <span>最大嵌套深度</span>
        <input v-model.number="form.maxSubagentDepth" type="number" min="1" max="5" />
      </label>
      <label class="settings-field">
        <span>子 Agent 最大轮次</span>
        <input v-model.number="form.maxSubagentTurns" type="number" min="1" max="32" />
      </label>

      <h3 class="settings-subsection">Web 工具</h3>
      <label class="settings-field settings-field--checkbox">
        <input v-model="form.webSearchEnabled" type="checkbox" />
        启用 WebSearch
      </label>
      <label class="settings-field">
        <span>搜索提供商</span>
        <select v-model="form.webSearchProvider">
          <option value="duckduckgo">DuckDuckGo（无需 API Key）</option>
        </select>
      </label>

      <label class="settings-field settings-field--checkbox">
        <input v-model="form.webFetchEnabled" type="checkbox" />
        启用 WebFetch（HTTP GET）
      </label>
      <label class="settings-field">
        <span>WebFetch 最大字节</span>
        <input v-model.number="form.webFetchMaxBytes" type="number" min="4096" step="1024" />
      </label>

      <h3 class="settings-subsection">Bash 沙箱（run_terminal_cmd）</h3>
      <label class="settings-field settings-field--checkbox">
        <input v-model="form.bashSandboxEnabled" type="checkbox" />
        启用 Bash 沙箱
      </label>
      <label class="settings-field">
        <span>沙箱模式</span>
        <select v-model="form.bashSandboxMode">
          <option value="strict">strict — 拦截危险命令</option>
          <option value="warn">warn — 警告但允许</option>
          <option value="off">off — 关闭</option>
        </select>
      </label>

      <div class="settings-form__actions">
        <button type="button" class="settings-btn" :disabled="loading || saving" @click="reload">
          {{ loading ? '加载中…' : '重新加载' }}
        </button>
        <button type="button" class="settings-btn settings-btn--primary" :disabled="saving" @click="save">
          {{ saving ? '保存中…' : '保存到数据库' }}
        </button>
      </div>
    </section>

    <section v-if="extensions" class="settings-section extensions-info">
      <h2>工作区扩展发现</h2>
      <p class="settings-section__hint">工作区：<code>{{ workspaceHint }}</code></p>
      <p class="settings-section__hint">
        Hooks：{{ extensions.hookCount }} 条 ·
        自定义斜杠命令：{{ extensions.slashCommands.length }} 个 ·
        插件：{{ extensions.plugins?.length ?? 0 }} 个
      </p>
      <p class="settings-section__hint">
        插件安装与管理 → <RouterLink to="/settings/agent/plugins">插件</RouterLink> ·
        Skills 管理 → <RouterLink to="/settings/agent/skills">Skills</RouterLink>
      </p>

      <ul v-if="extensions.slashCommands.length" class="extensions-info__list">
        <li v-for="cmd in extensions.slashCommands" :key="cmd.name">
          <strong>/{{ cmd.name }}</strong>
          <span v-if="cmd.description"> — {{ cmd.description }}</span>
        </li>
      </ul>
      <p v-else class="settings-section__hint">
        在 <code>.agent-desktop/commands/*.md</code> 或 <code>.claude/commands/</code> 添加 Markdown 斜杠命令。
      </p>

      <dl v-if="extensions.persistence" class="extensions-info__persist">
        <div><dt>Features / MCP</dt><dd>{{ extensions.persistence.features }} · {{ extensions.persistence.mcp }}</dd></div>
        <div><dt>Skills / 斜杠</dt><dd>{{ extensions.persistence.skills }}</dd></div>
      </dl>
    </section>

    <section class="settings-section">
      <h2>全局 Hooks</h2>
      <p class="settings-section__hint">
        保存在 <strong>MySQL</strong> <code>agent_config.hooks_global</code>，Redis 热缓存。工作区/插件 Hooks 仍用文件。
      </p>
      <p v-if="hooksStorageLabel" class="settings-section__hint settings-section__hint--path">{{ hooksStorageLabel }}</p>
      <label class="settings-field settings-field--full">
        <span>Hook 规则 JSON（HookDefinition 数组）</span>
        <textarea v-model="hooksJson" rows="12" class="settings-textarea" spellcheck="false" />
      </label>
      <div class="settings-form__actions">
        <button type="button" class="settings-btn" :disabled="loading" @click="loadHooks">重新加载 Hooks</button>
        <button type="button" class="settings-btn settings-btn--primary" :disabled="saving" @click="saveHooks">
          保存 Hooks 到数据库
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.extensions-info {
  margin-top: 16px;
}

.extensions-info__list {
  margin: 8px 0 0;
  padding-left: 18px;
  font-size: 0.85rem;
  line-height: 1.6;
}

.extensions-info__list strong {
  font-family: var(--ide-monospace, monospace);
}

.extensions-info__persist {
  margin: 12px 0 0;
  font-size: 0.78rem;
  color: var(--ide-muted);
}

.extensions-info__persist div {
  display: grid;
  grid-template-columns: 100px 1fr;
  gap: 8px;
  margin-bottom: 6px;
}

.extensions-info__persist dt {
  font-weight: 600;
}

.extensions-info a {
  color: var(--ide-accent);
}
</style>
