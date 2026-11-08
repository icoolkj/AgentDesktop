<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { RouterLink } from 'vue-router'

import { useSettingsStore } from '@renderer/stores/settingsStore'
import { useWorkspaceStore } from '@renderer/stores/workspaceStore'
import type { SkillInfo } from '@renderer/types/featureSettings'
import '@renderer/modules/settings/settings.css'

const settings = useSettingsStore()
const workspace = useWorkspaceStore()
const { saving, error, success, skillsInfo } = storeToRefs(settings)

const skillScope = ref<'user-ad' | 'user' | 'workspace'>('user-ad')
const skillSourcePath = ref('')
const refreshing = ref(false)

const workspaceHint = computed(() =>
  workspace.rootPath ? workspace.rootPath : '未打开工作区 — 仍可管理用户级 Skills',
)

const canInstallToWorkspace = computed(() => Boolean(workspace.rootPath))

async function reload(): Promise<void> {
  refreshing.value = true
  try {
    await settings.loadSkillsInfo(workspace.rootPath)
  } finally {
    refreshing.value = false
  }
}

async function pickSkillFolder(): Promise<void> {
  const path = await window.workspaceApi.openDirectory()
  if (path) skillSourcePath.value = path
}

async function installFromPath(): Promise<void> {
  if (!skillSourcePath.value.trim()) return
  if (skillScope.value === 'workspace' && !workspace.rootPath) return
  const result = await settings.installSkill(
    skillSourcePath.value.trim(),
    skillScope.value,
    workspace.rootPath,
  )
  if (result?.ok) {
    skillSourcePath.value = ''
    await reload()
  }
}

async function installCatalog(id: string): Promise<void> {
  const result = await settings.installCatalogSkill(id, skillScope.value, workspace.rootPath)
  if (result?.ok) await reload()
}

async function removeSkill(skill: SkillInfo): Promise<void> {
  if (!skill.managed || !skill.managedScope || !skill.name) return
  const ok = await settings.uninstallSkill(skill.name, skill.managedScope, workspace.rootPath)
  if (ok) await reload()
}

onMounted(() => {
  void reload()
})
</script>

<template>
  <div class="settings-panel">
    <p v-if="error" class="settings-alert settings-alert--error">{{ error }}</p>
    <p v-if="success" class="settings-alert settings-alert--success">{{ success }}</p>

    <section class="settings-section settings-section--guide">
      <h3 class="settings-subsection">Skills 管理说明</h3>
      <ul class="skills-guide">
        <li><strong>Skill ≠ 插件</strong>：本页管理独立 <code>SKILL.md</code>；插件包（可含 skills）请见 <RouterLink to="/settings/agent/plugins">插件</RouterLink> 页。</li>
        <li><strong>插件市场</strong>：远程源安装的是插件包，不是单个 skill 文件；插件内的 skills 安装后自动出现在下方「已发现」。</li>
        <li><strong>元数据 vs 正文</strong>：每轮对话只列出名称/描述；正文在 <code>Skill</code> 工具或 <code>/skill-name</code> 调用时加载。</li>
        <li><strong>发现顺序</strong>：工作区 monorepo 向上扫描 → 用户目录 → 已装插件 → 内置 JAR → MCP。</li>
      </ul>
    </section>

    <section class="settings-section">
      <h2>Skills</h2>
      <p class="settings-section__hint">
        工作区：<code>{{ workspaceHint }}</code>
      </p>
      <dl v-if="skillsInfo?.skillDirs" class="skills-dirs">
        <div><dt>用户 AD</dt><dd>{{ skillsInfo.skillDirs.userAd }}</dd></div>
        <div><dt>用户</dt><dd>{{ skillsInfo.skillDirs.user }}</dd></div>
        <div v-if="skillsInfo.skillDirs.workspace"><dt>工作区</dt><dd>{{ skillsInfo.skillDirs.workspace }}</dd></div>
      </dl>
      <div class="settings-form__actions">
        <button type="button" class="settings-btn" :disabled="refreshing" @click="reload">
          {{ refreshing ? '刷新中…' : '刷新 Skills 列表' }}
        </button>
        <span class="skills-refresh-hint">仅刷新 Skill 发现列表，不含插件市场</span>
      </div>
    </section>

    <section v-if="skillsInfo?.catalog?.length" class="settings-section">
      <h3 class="settings-subsection">内置 Skill 目录</h3>
      <p class="settings-section__hint">
        已随服务端 JAR 加载，可直接使用；也可复制到磁盘目录。
      </p>
      <label class="settings-field">
        <span>复制到</span>
        <select v-model="skillScope">
          <option value="user-ad">用户 (skills-agent-desktop) — 推荐</option>
          <option value="user">用户 (~/.agent-desktop/skills)</option>
          <option value="workspace" :disabled="!canInstallToWorkspace">当前工作区</option>
        </select>
      </label>
      <ul class="skills-catalog">
        <li v-for="item in skillsInfo.catalog" :key="item.id">
          <div class="skills-catalog__meta">
            <strong>{{ item.name }}</strong>
            <span v-if="item.description"> — {{ item.description }}</span>
            <span v-if="item.installed" class="skills-badge">已复制到磁盘</span>
            <span v-else class="skills-badge skills-badge--muted">内置可用</span>
          </div>
          <button
            type="button"
            class="settings-btn settings-btn--small"
            :disabled="saving || item.installed"
            @click="installCatalog(item.id)"
          >
            {{ item.installed ? '已复制' : '复制到磁盘' }}
          </button>
        </li>
      </ul>
    </section>

    <section class="settings-section">
      <h3 class="settings-subsection">从本地目录安装 Skill</h3>
      <p class="settings-section__hint">
        选择含 <code>SKILL.md</code> 的 skill 目录（或仅含一个 skill 的父目录）。
      </p>
      <label class="settings-field">
        <span>安装到</span>
        <select v-model="skillScope">
          <option value="user-ad">用户 (skills-agent-desktop) — 推荐</option>
          <option value="user">用户 (~/.agent-desktop/skills)</option>
          <option value="workspace" :disabled="!canInstallToWorkspace">当前工作区</option>
        </select>
      </label>
      <label class="settings-field">
        <span>Skill 目录</span>
        <div class="skills-path-row">
          <input v-model="skillSourcePath" type="text" placeholder="选择或粘贴绝对路径" />
          <button type="button" class="settings-btn" @click="pickSkillFolder">浏览…</button>
        </div>
      </label>
      <div class="settings-form__actions">
        <button
          type="button"
          class="settings-btn settings-btn--primary"
          :disabled="saving || !skillSourcePath.trim()"
          @click="installFromPath"
        >
          安装 Skill
        </button>
      </div>
    </section>

    <section class="settings-section">
      <h3 class="settings-subsection">
        已发现的 Skills
        <span v-if="skillsInfo" class="skills-count">({{ skillsInfo.skills.length }})</span>
      </h3>
      <p v-if="!skillsInfo?.skills.length" class="settings-section__hint">
        暂无 Skill。将 <code>SKILL.md</code> 放入 skills 目录，或从 <RouterLink to="/settings/agent/plugins">插件</RouterLink> 页安装含 skills 的插件。
      </p>
      <table v-else class="skills-table">
        <thead>
          <tr>
            <th>名称</th>
            <th>说明</th>
            <th>来源</th>
            <th>调用</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="skill in skillsInfo.skills" :key="skill.name + (skill.installPath ?? '')">
            <td><code>{{ skill.name }}</code></td>
            <td>{{ skill.description || '—' }}</td>
            <td>{{ skill.sourceLabel || skill.source }}</td>
            <td>
              <span v-if="skill.userInvocable" class="skills-tag">/slash</span>
              <span v-if="skill.modelInvocable" class="skills-tag">Skill</span>
            </td>
            <td>
              <button
                v-if="skill.managed && skill.managedScope"
                type="button"
                class="settings-btn settings-btn--small"
                @click="removeSkill(skill)"
              >
                卸载
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<style scoped>
.skills-dirs {
  margin: 12px 0 0;
  font-size: 0.78rem;
  color: var(--ide-muted);
}

.skills-dirs div {
  display: grid;
  grid-template-columns: 72px 1fr;
  gap: 8px;
  margin-bottom: 6px;
}

.skills-dirs dt {
  font-weight: 600;
}

.skills-guide {
  margin: 0;
  padding-left: 18px;
  font-size: 0.85rem;
  line-height: 1.6;
  color: var(--ide-muted);
}

.skills-guide li + li {
  margin-top: 8px;
}

.skills-guide a {
  color: var(--ide-accent);
}

.skills-refresh-hint {
  font-size: 0.78rem;
  color: var(--ide-muted);
  align-self: center;
}

.settings-form__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.skills-catalog {
  list-style: none;
  margin: 12px 0 0;
  padding: 0;
}

.skills-catalog li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--ide-border);
}

.skills-catalog__meta {
  font-size: 0.85rem;
  line-height: 1.5;
}

.skills-badge {
  margin-left: 8px;
  padding: 1px 6px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--ide-list-active-bg) 60%, transparent);
  font-size: 0.72rem;
}

.skills-badge--muted {
  background: var(--ide-hover-overlay);
  color: var(--ide-muted);
}

.skills-path-row {
  display: flex;
  gap: 8px;
}

.skills-path-row input {
  flex: 1;
}

.skills-count {
  font-size: 0.85rem;
  color: var(--ide-muted);
  font-weight: normal;
}

.skills-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85rem;
  margin-top: 12px;
}

.skills-table th,
.skills-table td {
  text-align: left;
  padding: 8px 10px;
  border-bottom: 1px solid var(--ide-border);
  vertical-align: top;
}

.skills-table th {
  font-size: 0.75rem;
  color: var(--ide-muted);
  font-weight: 600;
}

.skills-tag {
  display: inline-block;
  margin-right: 4px;
  padding: 1px 5px;
  border-radius: 3px;
  background: var(--ide-hover-overlay);
  font-size: 0.72rem;
  font-family: var(--ide-monospace, monospace);
}

.settings-btn--small {
  padding: 2px 8px;
  font-size: 0.75rem;
}
</style>
