<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useWorkspaceStore } from '@renderer/stores/workspaceStore'
import {
  EMPTY_PERMISSIONS,
  loadWorkspacePermissions,
  permissionsFilePath,
  saveWorkspacePermissions,
  type WorkspacePermissionsFile,
} from '@renderer/utils/workspacePermissions'
import '@renderer/modules/settings/settings.css'

type RuleKind = keyof WorkspacePermissionsFile['permissions']

const workspace = useWorkspaceStore()
const { rootPath } = storeToRefs(workspace)

const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const success = ref<string | null>(null)
const rules = ref<WorkspacePermissionsFile>(structuredClone(EMPTY_PERMISSIONS))
const newRule = ref<Record<RuleKind, string>>({ allow: '', ask: '', deny: '' })

const filePath = computed(() => (rootPath.value ? permissionsFilePath(rootPath.value) : ''))

const sections: { kind: RuleKind; title: string; hint: string }[] = [
  {
    kind: 'deny',
    title: 'Deny — 始终拒绝',
    hint: '优先级最高。例：write_file(secrets/**)',
  },
  {
    kind: 'ask',
    title: 'Ask — 强制询问',
    hint: '例：write_file(**/*.env)',
  },
  {
    kind: 'allow',
    title: 'Allow — 自动允许',
    hint: '例：write_file(docs/**)',
  },
]

async function reload(): Promise<void> {
  if (!rootPath.value) return
  loading.value = true
  error.value = null
  success.value = null
  try {
    rules.value = await loadWorkspacePermissions(rootPath.value)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function save(): Promise<void> {
  if (!rootPath.value) return
  saving.value = true
  error.value = null
  success.value = null
  try {
    await saveWorkspacePermissions(rootPath.value, rules.value)
    success.value = '已保存到工作区 .agent-desktop/permissions.json'
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    saving.value = false
  }
}

function addRule(kind: RuleKind): void {
  const value = newRule.value[kind].trim()
  if (!value) return
  if (!rules.value.permissions[kind].includes(value)) {
    rules.value.permissions[kind].push(value)
  }
  newRule.value[kind] = ''
}

function removeRule(kind: RuleKind, index: number): void {
  rules.value.permissions[kind].splice(index, 1)
}

onMounted(() => {
  void reload()
})

watch(rootPath, () => {
  void reload()
})
</script>

<template>
  <div class="settings-panel">
    <section class="settings-section">
      <h2>项目权限规则</h2>
      <p class="settings-section__hint">
        与 Claude Code 项目设置对齐：写入工作区 <code>.agent-desktop/permissions.json</code>（若无则回退读取
        <code>.claude/settings.json</code>）。规则在每次 Agent 请求时加载，优先级 Deny → Ask → Allow。
      </p>

      <p v-if="!rootPath" class="settings-alert settings-alert--warn">
        请先在资源管理器中打开工作区文件夹，再编辑项目权限规则。
      </p>

      <template v-else>
        <p v-if="filePath" class="settings-section__hint settings-section__hint--path">{{ filePath }}</p>
        <p v-if="loading" class="settings-section__hint">加载中…</p>
        <p v-if="error" class="settings-alert settings-alert--error">{{ error }}</p>
        <p v-if="success" class="settings-alert settings-alert--success">{{ success }}</p>

        <div v-for="section in sections" :key="section.kind" class="perm-section">
          <h3 class="settings-subsection">{{ section.title }}</h3>
          <p class="settings-section__hint">{{ section.hint }}</p>

          <ul v-if="rules.permissions[section.kind].length" class="perm-list">
            <li v-for="(rule, idx) in rules.permissions[section.kind]" :key="`${section.kind}-${rule}`">
              <code>{{ rule }}</code>
              <button type="button" class="perm-list__remove" @click="removeRule(section.kind, idx)">删除</button>
            </li>
          </ul>
          <p v-else class="settings-section__hint">暂无规则</p>

          <div class="perm-add">
            <input
              v-model="newRule[section.kind]"
              type="text"
              class="settings-input"
              :placeholder="section.kind === 'deny' ? 'write_file(secrets/**)' : 'write_file'"
              @keydown.enter.prevent="addRule(section.kind)"
            />
            <button type="button" class="settings-btn" @click="addRule(section.kind)">添加</button>
          </div>
        </div>

        <div class="settings-actions">
          <button type="button" class="settings-btn settings-btn--primary" :disabled="saving" @click="save">
            {{ saving ? '保存中…' : '保存规则' }}
          </button>
          <button type="button" class="settings-btn" :disabled="loading" @click="reload">重新加载</button>
        </div>
      </template>
    </section>
  </div>
</template>

<style scoped>
.settings-section__hint--path {
  font-family: ui-monospace, monospace;
  font-size: 0.72rem;
  word-break: break-all;
}

.perm-section + .perm-section {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--ide-border);
}

.perm-list {
  margin: 8px 0 10px;
  padding: 0;
  list-style: none;
}

.perm-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 8px;
  margin-bottom: 4px;
  border-radius: 4px;
  background: var(--ide-hover-overlay);
}

.perm-list code {
  font-size: 0.78rem;
  color: var(--ide-fg);
}

.perm-list__remove {
  border: none;
  background: transparent;
  color: var(--ide-error);
  font-size: 0.72rem;
  cursor: pointer;
}

.perm-add {
  display: flex;
  gap: 8px;
}

.perm-add .settings-input {
  flex: 1;
}

.settings-actions {
  display: flex;
  gap: 8px;
  margin-top: 20px;
}

.settings-alert--warn {
  color: var(--ide-warning);
  background: color-mix(in srgb, var(--ide-warning) 12%, transparent);
  padding: 8px 10px;
  border-radius: 4px;
}
</style>
