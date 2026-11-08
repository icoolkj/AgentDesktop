<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import ActivityBarIcon from '@renderer/components/layout/ActivityBarIcon.vue'
import { ACTIVITY_ICONS } from '@renderer/utils/activityIcons'
import { useSidebarStore, type SidebarPanel } from '@renderer/stores/sidebarStore'

const props = defineProps<{
  sidebarCollapsed: boolean
  chatCollapsed: boolean
}>()

const emit = defineEmits<{
  toggleSidebar: []
  toggleChat: []
  openPanel: [panel: SidebarPanel]
}>()

const route = useRoute()
const router = useRouter()
const sidebarStore = useSidebarStore()
const { activePanel } = storeToRefs(sidebarStore)

const onWorkspace = computed(() => route.name === 'workspace')
const onSettings = computed(() => route.matched.some(record => record.name === 'settings'))

function isPanelActive(panel: SidebarPanel): boolean {
  return onWorkspace.value && !props.sidebarCollapsed && activePanel.value === panel
}

function openPrimaryPanel(panel: SidebarPanel) {
  if (onSettings.value) {
    void router.push('/workspace')
    emit('openPanel', panel)
    return
  }

  if (isPanelActive(panel)) {
    emit('toggleSidebar')
    return
  }

  emit('openPanel', panel)
}

function openAgent() {
  if (onSettings.value) {
    void router.push('/workspace')
    if (props.chatCollapsed) emit('toggleChat')
    return
  }
  emit('toggleChat')
}

function openSettings() {
  void router.push('/settings')
}
</script>

<template>
  <aside class="activity-bar" aria-label="主侧边栏">
    <button
      type="button"
      class="activity-bar__item"
      :class="{ 'activity-bar__item--active': isPanelActive('explorer') }"
      :title="ACTIVITY_ICONS.explorer.label"
      :aria-label="ACTIVITY_ICONS.explorer.label"
      @click="openPrimaryPanel('explorer')"
    >
      <ActivityBarIcon icon="explorer" />
    </button>

    <button
      type="button"
      class="activity-bar__item"
      :class="{ 'activity-bar__item--active': isPanelActive('search') }"
      :title="ACTIVITY_ICONS.search.label"
      :aria-label="ACTIVITY_ICONS.search.label"
      @click="openPrimaryPanel('search')"
    >
      <ActivityBarIcon icon="search" />
    </button>

    <button
      type="button"
      class="activity-bar__item"
      :class="{ 'activity-bar__item--active': isPanelActive('git') }"
      :title="ACTIVITY_ICONS.git.label"
      :aria-label="ACTIVITY_ICONS.git.label"
      @click="openPrimaryPanel('git')"
    >
      <ActivityBarIcon icon="git" />
    </button>

    <button
      type="button"
      class="activity-bar__item"
      :class="{ 'activity-bar__item--active': onWorkspace && !chatCollapsed }"
      :title="ACTIVITY_ICONS.agent.label"
      :aria-label="ACTIVITY_ICONS.agent.label"
      @click="openAgent"
    >
      <ActivityBarIcon icon="agent" />
    </button>

    <div class="activity-bar__spacer" />

    <button
      type="button"
      class="activity-bar__item"
      :class="{ 'activity-bar__item--active': onSettings }"
      :title="ACTIVITY_ICONS.settings.label"
      :aria-label="ACTIVITY_ICONS.settings.label"
      @click="openSettings"
    >
      <ActivityBarIcon icon="settings" />
    </button>
  </aside>
</template>

<style scoped>
.activity-bar {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  width: var(--ide-activitybar-width, 48px);
  flex-shrink: 0;
  background: var(--ide-activitybar-bg);
  border-right: 1px solid var(--ide-border);
  padding: 0;
}

.activity-bar__item {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 48px;
  padding: 0;
  border: none;
  border-radius: 0;
  background: transparent;
  color: var(--ide-activitybar-icon-idle);
  cursor: pointer;
  position: relative;
}

.activity-bar__item:hover {
  color: var(--ide-fg);
}

.activity-bar__item--active {
  color: var(--ide-activitybar-icon-active);
}

.activity-bar__item--active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 2px;
  background: var(--ide-activitybar-icon-active);
}

.activity-bar__spacer {
  flex: 1;
  min-height: 8px;
}
</style>
