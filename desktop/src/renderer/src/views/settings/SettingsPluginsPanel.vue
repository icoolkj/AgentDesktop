<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { RouterLink } from 'vue-router'

import { useSettingsStore } from '@renderer/stores/settingsStore'
import { useWorkspaceStore } from '@renderer/stores/workspaceStore'
import type { MarketplacePluginInfo } from '@renderer/types/featureSettings'
import '@renderer/modules/settings/settings.css'

const settings = useSettingsStore()
const workspace = useWorkspaceStore()
const { saving, error, success, extensions, marketplaceInfo } = storeToRefs(settings)

const pluginScope = ref<'user' | 'workspace'>('user')
const pluginSourcePath = ref('')
const marketQuery = ref('')
const marketFilter = ref<'all' | 'available' | 'installed'>('available')
const installingPlugin = ref<string | null>(null)
const refreshing = ref(false)
const marketplaceLoading = ref(false)

const workspaceHint = computed(() =>
  workspace.rootPath ? workspace.rootPath : '未打开工作区 — 仍可管理用户级插件',
)

const canInstallToWorkspace = computed(() => Boolean(workspace.rootPath))

function isPluginInstalled(plugin: MarketplacePluginInfo): boolean {
  return plugin.installed === true || plugin.installed === 'true'
}

const installedPluginCount = computed(() => marketplaceInfo.value?.installedCount ?? 0)
const marketplaceTotal = computed(() => marketplaceInfo.value?.plugins?.length ?? 0)

const filteredMarketPlugins = computed(() => {
  let plugins = marketplaceInfo.value?.plugins ?? []
  if (marketFilter.value === 'installed') {
    plugins = plugins.filter(isPluginInstalled)
  } else if (marketFilter.value === 'available') {
    plugins = plugins.filter(p => !isPluginInstalled(p))
  }
  const q = marketQuery.value.trim().toLowerCase()
  if (!q) return plugins
  return plugins.filter(
    p =>
      p.name.toLowerCase().includes(q) ||
      (p.description ?? '').toLowerCase().includes(q),
  )
})

async function reload(): Promise<void> {
  refreshing.value = true
  marketplaceLoading.value = true
  try {
    await Promise.all([
      settings.loadExtensionInfo(workspace.rootPath).finally(() => {
        refreshing.value = false
      }),
      settings.loadMarketplaceInfo(workspace.rootPath).finally(() => {
        marketplaceLoading.value = false
      }),
    ])
  } catch {
    refreshing.value = false
    marketplaceLoading.value = false
  }
}

async function pickPluginFolder(): Promise<void> {
  const path = await window.workspaceApi.openDirectory()
  if (path) pluginSourcePath.value = path
}

async function installFromPath(): Promise<void> {
  if (!pluginSourcePath.value.trim()) return
  if (pluginScope.value === 'workspace' && !workspace.rootPath) return
  const result = await settings.installPlugin(
    pluginSourcePath.value.trim(),
    pluginScope.value,
    workspace.rootPath,
  )
  if (result?.ok) {
    pluginSourcePath.value = ''
    await reload()
  }
}

async function installMarketPlugin(name: string): Promise<void> {
  if (installingPlugin.value) return
  installingPlugin.value = name
  try {
    if (pluginScope.value === 'workspace' && !workspace.rootPath) return
    const result = await settings.installMarketplacePlugin(
      name,
      pluginScope.value,
      workspace.rootPath,
      marketplaceInfo.value?.defaultInstallMethod ?? 'github',
    )
    if (result?.ok) await reload()
  } finally {
    installingPlugin.value = null
  }
}

async function removeInstalledPlugin(name: string): Promise<void> {
  const ok = await settings.uninstallPlugin(name, pluginScope.value, workspace.rootPath)
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

    <section class="settings-section">
      <h2>插件</h2>
      <p class="settings-section__hint">
        插件包可含 commands、skills、hooks。与独立 Skill 目录分开管理；插件内的 skills 安装后会在
        <RouterLink to="/settings/agent/skills">Skills</RouterLink> 页「已发现」中出现。
      </p>
      <p class="settings-section__hint">
        插件市场与 Skills 页共用同一远程源：市场安装的是<strong>插件包</strong>（不是单独 skill 文件）。
        独立 skill 请在 Skills 页从目录或内置目录安装。
      </p>
      <p class="settings-section__hint">工作区：<code>{{ workspaceHint }}</code></p>
      <dl v-if="marketplaceInfo?.pluginDirs || extensions?.pluginDirs" class="plugins-dirs">
        <div>
          <dt>用户</dt>
          <dd>{{ marketplaceInfo?.pluginDirs?.user ?? extensions?.pluginDirs?.user ?? '~/.agent-desktop/plugins/' }}</dd>
        </div>
        <div v-if="marketplaceInfo?.pluginDirs?.workspace ?? extensions?.pluginDirs?.workspace">
          <dt>工作区</dt>
          <dd>{{ marketplaceInfo?.pluginDirs?.workspace ?? extensions?.pluginDirs?.workspace }}</dd>
        </div>
      </dl>
    </section>

    <section class="settings-section">
      <div class="plugins-market-header">
        <h3 class="settings-subsection">插件市场</h3>
        <button type="button" class="settings-btn" :disabled="refreshing || marketplaceLoading" @click="reload">
          {{ refreshing || marketplaceLoading ? '刷新中…' : '刷新列表' }}
        </button>
      </div>
      <p v-if="marketplaceLoading" class="settings-section__hint">市场列表加载中…（需后端访问 GitHub）</p>
      <template v-else-if="marketplaceTotal > 0">
        <p class="settings-section__hint">
          官方插件源 · 共 {{ marketplaceTotal }} 个 · 已装 {{ installedPluginCount }} 个 · 从 GitHub 按需下载
        </p>
        <div class="plugins-market-toolbar">
          <label class="settings-field plugins-market-toolbar__search">
            <span>搜索</span>
            <input
              v-model="marketQuery"
              type="search"
              class="plugins-toolbar-input"
              placeholder="按名称或描述过滤…"
            />
          </label>
          <label class="settings-field">
            <span>显示</span>
            <select v-model="marketFilter" class="plugins-toolbar-select">
              <option value="available">未安装（推荐）</option>
              <option value="installed">已安装</option>
              <option value="all">全部</option>
            </select>
          </label>
          <label class="settings-field">
            <span>安装到</span>
            <select v-model="pluginScope" class="plugins-toolbar-select">
              <option value="user">用户 (~/.agent-desktop/plugins/)</option>
              <option value="workspace" :disabled="!canInstallToWorkspace">当前工作区</option>
            </select>
          </label>
        </div>
        <ul class="plugins-catalog">
          <li v-for="plugin in filteredMarketPlugins" :key="plugin.name">
            <div class="plugins-catalog__meta">
              <div class="plugins-catalog__title">
                <strong>{{ plugin.name }}</strong>
                <span v-if="plugin.version" class="plugins-version">v{{ plugin.version }}</span>
                <span v-if="isPluginInstalled(plugin)" class="plugins-badge">已安装</span>
              </div>
              <p v-if="plugin.description" class="plugins-catalog__desc">{{ plugin.description }}</p>
              <div v-if="plugin.installPath" class="plugins-install-path">
                <code>{{ plugin.installPath }}</code>
              </div>
            </div>
            <div class="plugins-catalog__action">
              <button
                type="button"
                class="settings-btn settings-btn--small plugins-action-btn"
                :disabled="saving || isPluginInstalled(plugin) || installingPlugin === plugin.name"
                @click="installMarketPlugin(plugin.name)"
              >
                {{
                  installingPlugin === plugin.name
                    ? '下载中…'
                    : isPluginInstalled(plugin)
                      ? '已安装'
                      : '安装'
                }}
              </button>
            </div>
          </li>
        </ul>
        <p v-if="!filteredMarketPlugins.length" class="settings-section__hint">没有匹配的插件。</p>
      </template>
      <div v-else class="settings-section__hint">
        <p>暂无市场数据。</p>
        <p v-if="marketplaceInfo?.loadError" class="settings-alert settings-alert--error plugins-load-error">
          {{ marketplaceInfo.loadError }}
        </p>
        <p v-else>请确认后端已启动且可访问 GitHub（raw.githubusercontent.com），然后点「刷新列表」。</p>
      </div>
    </section>

    <section class="settings-section">
      <h3 class="settings-subsection">从本地目录安装</h3>
      <p class="settings-section__hint">
        选择含 <code>plugin.json</code>（或 <code>.claude-plugin/plugin.json</code>）的插件目录。
      </p>
      <label class="settings-field">
        <span>安装到</span>
        <select v-model="pluginScope">
          <option value="user">用户 (~/.agent-desktop/plugins/)</option>
          <option value="workspace" :disabled="!canInstallToWorkspace">当前工作区</option>
        </select>
      </label>
      <label class="settings-field">
        <span>插件目录</span>
        <div class="plugins-path-row">
          <input v-model="pluginSourcePath" type="text" placeholder="选择或粘贴绝对路径" />
          <button type="button" class="settings-btn" @click="pickPluginFolder">浏览…</button>
        </div>
      </label>
      <div class="settings-form__actions">
        <button
          type="button"
          class="settings-btn settings-btn--primary"
          :disabled="saving || !pluginSourcePath.trim()"
          @click="installFromPath"
        >
          安装插件
        </button>
      </div>
    </section>

    <section class="settings-section">
      <h3 class="settings-subsection">
        已安装的插件
        <span v-if="extensions" class="plugins-count">({{ extensions.plugins?.length ?? 0 }})</span>
      </h3>
      <p v-if="!extensions?.plugins?.length" class="settings-section__hint">
        暂无插件。从插件市场或本地目录安装后，在上方市场区点「刷新列表」。
      </p>
      <table v-else class="plugins-table">
        <thead>
          <tr>
            <th>名称</th>
            <th>版本</th>
            <th>说明</th>
            <th>路径</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="plugin in extensions.plugins" :key="plugin.name">
            <td><code>{{ plugin.name }}</code></td>
            <td>{{ plugin.version || '—' }}</td>
            <td>{{ plugin.description || '—' }}</td>
            <td><code class="plugins-table__path">{{ plugin.root }}</code></td>
            <td class="plugins-table__action">
              <button
                type="button"
                class="settings-btn settings-btn--small plugins-action-btn"
                @click="removeInstalledPlugin(plugin.name)"
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
.plugins-dirs {
  margin: 12px 0 0;
  font-size: 0.78rem;
  color: var(--ide-muted);
}

.plugins-dirs div {
  display: grid;
  grid-template-columns: 56px 1fr;
  gap: 8px;
  margin-bottom: 6px;
}

.plugins-dirs dt {
  font-weight: 600;
}

.plugins-market-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.plugins-market-header .settings-subsection {
  margin: 0;
}

.plugins-market-toolbar {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) auto auto;
  gap: 12px;
  margin-top: 12px;
  align-items: end;
}

.plugins-toolbar-input,
.plugins-toolbar-select {
  min-height: 36px;
  height: 36px;
  box-sizing: border-box;
}

.plugins-market-toolbar__search input {
  width: 100%;
}

.plugins-catalog {
  list-style: none;
  margin: 12px 0 0;
  padding: 0;
}

.plugins-catalog li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 76px;
  gap: 16px;
  align-items: start;
  padding: 12px 0;
  border-bottom: 1px solid var(--ide-border);
}

.plugins-catalog__meta {
  min-width: 0;
  font-size: 0.85rem;
}

.plugins-catalog__title {
  line-height: 1.4;
}

.plugins-catalog__desc {
  margin: 6px 0 0;
  font-size: 0.82rem;
  line-height: 1.45;
  color: var(--ide-muted);
}

.plugins-catalog__action {
  flex-shrink: 0;
  padding-top: 2px;
}

.plugins-action-btn {
  width: 76px;
  min-width: 76px;
  box-sizing: border-box;
  text-align: center;
  white-space: nowrap;
}

.plugins-version {
  margin-left: 6px;
  font-size: 0.72rem;
  color: var(--ide-muted);
}

.plugins-badge {
  margin-left: 8px;
  padding: 1px 6px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--ide-list-active-bg) 60%, transparent);
  font-size: 0.72rem;
}

.plugins-install-path {
  margin-top: 4px;
  font-size: 0.72rem;
  color: var(--ide-muted);
  word-break: break-all;
}

.plugins-load-error {
  margin-top: 10px;
}

.plugins-path-row {
  display: flex;
  gap: 8px;
}

.plugins-path-row input {
  flex: 1;
}

.plugins-count {
  font-size: 0.85rem;
  color: var(--ide-muted);
  font-weight: normal;
}

.plugins-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85rem;
  margin-top: 12px;
}

.plugins-table th,
.plugins-table td {
  text-align: left;
  padding: 8px 10px;
  border-bottom: 1px solid var(--ide-border);
  vertical-align: top;
}

.plugins-table th {
  font-size: 0.75rem;
  color: var(--ide-muted);
  font-weight: 600;
}

.plugins-table__path {
  font-size: 0.72rem;
  word-break: break-all;
}

.plugins-table__action {
  width: 76px;
}

.settings-btn--small {
  padding: 4px 8px;
  font-size: 0.75rem;
}

a {
  color: var(--ide-accent);
}
</style>
