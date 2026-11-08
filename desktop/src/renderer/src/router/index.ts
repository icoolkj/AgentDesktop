import { createRouter, createWebHashHistory } from 'vue-router'
import WorkspaceLayout from '@renderer/components/layout/WorkspaceLayout.vue'
import { useChatStore } from '@renderer/stores/chatStore'

const routes = [
  {
    path: '/',
    component: WorkspaceLayout,
    redirect: '/workspace',
    children: [
      {
        name: 'workspace',
        path: 'workspace',
        component: () => import('@renderer/views/WorkspaceContent.vue'),
      },
      {
        name: 'settings',
        path: 'settings',
        component: () => import('@renderer/views/SettingsLayout.vue'),
        redirect: { name: 'settings-color-theme' },
        children: [
          {
            name: 'settings-color-theme',
            path: 'appearance/color',
            component: () => import('@renderer/modules/appearance/components/ColorThemeSettings.vue'),
          },
          {
            name: 'settings-file-icons',
            path: 'appearance/icons',
            component: () => import('@renderer/modules/appearance/components/FileIconThemeSettings.vue'),
          },
          {
            name: 'settings-workspace',
            path: 'appearance/workspace',
            component: () => import('@renderer/views/settings/SettingsWorkspacePanel.vue'),
          },
          {
            name: 'settings-backend',
            path: 'agent/backend',
            component: () => import('@renderer/views/settings/SettingsBackendPanel.vue'),
          },
          {
            name: 'settings-agent',
            path: 'agent/behavior',
            component: () => import('@renderer/views/settings/SettingsAgentPanel.vue'),
          },
          {
            name: 'settings-permissions',
            path: 'agent/permissions',
            component: () => import('@renderer/views/settings/SettingsPermissionsPanel.vue'),
          },
          {
            name: 'settings-llm',
            path: 'agent/llm',
            component: () => import('@renderer/views/settings/SettingsLlmPanel.vue'),
          },
          {
            name: 'settings-features',
            path: 'agent/features',
            component: () => import('@renderer/views/settings/SettingsFeaturesPanel.vue'),
          },
          {
            name: 'settings-plugins',
            path: 'agent/plugins',
            component: () => import('@renderer/views/settings/SettingsPluginsPanel.vue'),
          },
          {
            name: 'settings-skills',
            path: 'agent/skills',
            component: () => import('@renderer/views/settings/SettingsSkillsPanel.vue'),
          },
          {
            name: 'settings-mcp',
            path: 'agent/mcp',
            component: () => import('@renderer/views/settings/SettingsMcpPanel.vue'),
          },
          {
            name: 'settings-models',
            path: 'agent/models',
            component: () => import('@renderer/views/settings/SettingsModelsPanel.vue'),
          },
        ],
      },
      {
        name: 'help',
        path: 'help',
        component: () => import('@renderer/views/HelpView.vue'),
      },
    ],
  },
]

export const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

router.afterEach(to => {
  if (to.name === 'workspace') {
    void useChatStore().syncCurrentSession()
  }
})
