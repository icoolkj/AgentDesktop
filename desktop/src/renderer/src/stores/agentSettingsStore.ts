import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { PermissionMode } from '@agent-desktop/shared-types'

const STORAGE_KEY = 'agent-desktop.permission-mode'

function loadMode(): PermissionMode {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw === 'ask' || raw === 'auto' || raw === 'plan') return raw
  } catch {
    // ignore
  }
  return 'ask'
}

export const useAgentSettingsStore = defineStore('agentSettings', () => {
  const permissionMode = ref<PermissionMode>(loadMode())

  function setPermissionMode(mode: PermissionMode): void {
    permissionMode.value = mode
    try {
      localStorage.setItem(STORAGE_KEY, mode)
    } catch {
      // ignore
    }
  }

  const permissionModeLabel = computed(() => {
    switch (permissionMode.value) {
      case 'auto':
        return '自动允许写文件'
      case 'plan':
        return 'Plan（只读）'
      default:
        return '每次询问'
    }
  })

  return {
    permissionMode,
    permissionModeLabel,
    setPermissionMode,
  }
})
