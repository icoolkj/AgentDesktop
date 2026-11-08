<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useSettingsStore } from '@renderer/stores/settingsStore'
import '@renderer/modules/settings/settings.css'

const router = useRouter()
const route = useRoute()
const settings = useSettingsStore()

const navGroups = [
  {
    label: '外观',
    items: [
      { name: 'settings-color-theme', label: '颜色主题', to: '/settings/appearance/color' },
      { name: 'settings-file-icons', label: '文件图标', to: '/settings/appearance/icons' },
      { name: 'settings-workspace', label: '工作区面板', to: '/settings/appearance/workspace' },
    ],
  },
  {
    label: 'Agent',
    items: [
      { name: 'settings-backend', label: '后端连接', to: '/settings/agent/backend' },
      { name: 'settings-agent', label: 'Agent 行为', to: '/settings/agent/behavior' },
      { name: 'settings-permissions', label: '权限规则', to: '/settings/agent/permissions' },
      { name: 'settings-features', label: '扩展引擎', to: '/settings/agent/features' },
      { name: 'settings-plugins', label: '插件', to: '/settings/agent/plugins' },
      { name: 'settings-skills', label: 'Skills', to: '/settings/agent/skills' },
      { name: 'settings-mcp', label: 'MCP 服务器', to: '/settings/agent/mcp' },
      { name: 'settings-models', label: 'Models', to: '/settings/agent/models' },
      { name: 'settings-llm', label: 'LLM 配置', to: '/settings/agent/llm' },
    ],
  },
] as const

const pageTitle = computed(() => {
  for (const group of navGroups) {
    const item = group.items.find(entry => entry.name === route.name)
    if (item) return item.label
  }
  return '设置'
})

function backToWorkspace(): void {
  void router.push('/workspace')
}

function isActive(name: string): boolean {
  return route.name === name
}

onMounted(async () => {
  await settings.ensureAgentConnected()
})
</script>

<template>
  <div class="settings-page">
    <header class="settings-page__header">
      <div>
        <h1>设置</h1>
        <p class="settings-page__subtitle">{{ pageTitle }}</p>
      </div>
      <button type="button" class="settings-page__back" @click="backToWorkspace">返回工作区</button>
    </header>

    <div class="settings-page__layout">
      <nav class="settings-page__nav" aria-label="设置分类">
        <div v-for="group in navGroups" :key="group.label" class="settings-nav__group">
          <div class="settings-nav__label">{{ group.label }}</div>
          <RouterLink
            v-for="item in group.items"
            :key="item.name"
            :to="item.to"
            class="settings-nav__item"
            :class="{ active: isActive(item.name) }"
          >
            {{ item.label }}
          </RouterLink>
        </div>
      </nav>

      <main class="settings-page__content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.settings-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--ide-editor-bg);
  color: var(--ide-fg);
}

.settings-page__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 24px 12px;
  border-bottom: 1px solid var(--ide-border);
  flex-shrink: 0;
}

.settings-page__header h1 {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 600;
}

.settings-page__subtitle {
  margin: 0;
  font-size: 13px;
  color: var(--ide-muted);
}

.settings-page__back {
  padding: 6px 12px;
  border: 1px solid var(--ide-button-border);
  border-radius: 4px;
  background: var(--ide-button-bg);
  color: var(--ide-fg);
  font-size: 12px;
  cursor: pointer;
}

.settings-page__back:hover {
  background: var(--ide-hover-overlay);
}

.settings-page__layout {
  display: flex;
  flex: 1;
  min-height: 0;
}

.settings-page__nav {
  width: 200px;
  flex-shrink: 0;
  padding: 12px;
  border-right: 1px solid var(--ide-border);
  overflow: auto;
  background: color-mix(in srgb, var(--ide-sidebar-bg) 70%, var(--ide-editor-bg));
}

.settings-nav__group + .settings-nav__group {
  margin-top: 12px;
}

.settings-nav__label {
  padding: 4px 10px 6px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--ide-muted);
}

.settings-nav__item {
  display: block;
  width: 100%;
  padding: 8px 10px;
  margin-bottom: 2px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--ide-muted);
  font-size: 13px;
  text-align: left;
  text-decoration: none;
  cursor: pointer;
}

.settings-nav__item:hover {
  background: var(--ide-hover-overlay);
  color: var(--ide-fg);
}

.settings-nav__item.active {
  background: var(--ide-list-active-bg);
  color: var(--ide-list-active-fg);
}

.settings-page__content {
  flex: 1;
  min-width: 0;
  overflow: auto;
  padding: 20px 28px 32px;
  display: flex;
  flex-direction: column;
  align-items: stretch;
}

.settings-page__content > .settings-block,
.settings-page__content > .settings-panel {
  width: 100%;
}
</style>
