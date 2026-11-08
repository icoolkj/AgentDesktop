<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterView } from 'vue-router'
import { useChatStore } from '@renderer/stores/chatStore'
import {
  useWorkspaceStore,
  type WindowBootConfig,
} from '@renderer/stores/workspaceStore'
import { addRecentWorkspace, getLastWorkspacePath } from '@renderer/utils/workspacePersistence'

const LEGACY_LAST_KEY = 'agent-desktop:last-workspace'
const LEGACY_RECENT_KEY = 'agent-desktop:recent-workspaces'
const LEGACY_PANEL_KEY = 'agent-desktop:panel-layout'
const LEGACY_SESSION_PREFIX = 'agent-desktop:session-id:'

const bootReady = ref(false)

function parseBootFromUrl(): WindowBootConfig | null {
  const params = new URLSearchParams(window.location.search)
  const rawWorkspace = params.get('workspace')
  if (rawWorkspace) {
    let workspacePath = rawWorkspace
    try {
      workspacePath = decodeURIComponent(rawWorkspace)
    } catch {
      /* keep raw */
    }
    return { mode: 'workspace', workspacePath }
  }
  if (params.get('blank') === '1') return { mode: 'blank' }
  if (params.get('restore') === '1') return { mode: 'restore' }
  return null
}

async function resolveWindowBoot(): Promise<WindowBootConfig> {
  const boot = await window.desktopApi.getWindowBoot()
  const urlBoot = parseBootFromUrl()

  if (urlBoot?.mode === 'workspace' && urlBoot.workspacePath?.trim()) {
    return urlBoot
  }
  if (boot.mode !== 'blank') {
    return boot
  }
  return urlBoot ?? boot
}

async function bootWindowWorkspace(): Promise<void> {
  const boot = await resolveWindowBoot()
  await useWorkspaceStore().initializeWindowBoot(boot)
}

async function migrateLegacyLocalStorage(): Promise<void> {
  const last = localStorage.getItem(LEGACY_LAST_KEY)?.trim()
  if (last && !(await getLastWorkspacePath())) {
    await addRecentWorkspace(last)
  }

  const recentRaw = localStorage.getItem(LEGACY_RECENT_KEY)
  if (recentRaw) {
    try {
      const parsed = JSON.parse(recentRaw) as { path: string; openedAt?: number }[]
      if (Array.isArray(parsed)) {
        for (const item of [...parsed].reverse()) {
          if (item?.path) await addRecentWorkspace(item.path)
        }
      }
    } catch {
      /* ignore */
    }
  }

  const panelRaw = localStorage.getItem(LEGACY_PANEL_KEY)
  if (panelRaw) {
    try {
      const parsed = JSON.parse(panelRaw)
      if (parsed && typeof parsed === 'object') {
        await window.desktopApi.setPanelLayout(parsed)
      }
    } catch {
      /* ignore */
    }
  }

  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i)
    if (!key?.startsWith(LEGACY_SESSION_PREFIX)) continue
    const workspaceRoot = key.slice(LEGACY_SESSION_PREFIX.length)
    const sessionId = localStorage.getItem(key)
    if (sessionId) {
      await window.desktopApi.setWorkspaceSession(workspaceRoot, sessionId)
    }
  }
}

onMounted(() => {
  void (async () => {
    await migrateLegacyLocalStorage()
    await bootWindowWorkspace()
    bootReady.value = true
    void useChatStore().init()
  })()

  window.addEventListener('beforeunload', onAppUnload)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', onAppUnload)
  onAppUnload()
})

function onAppUnload(): void {
  useChatStore().cleanup()
}
</script>

<template>
  <RouterView v-if="bootReady" />
</template>
