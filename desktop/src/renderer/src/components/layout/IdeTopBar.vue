<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useAgentStore } from '@renderer/stores/agentStore'
import { useWorkspaceStore } from '@renderer/stores/workspaceStore'
import { useEditorStore } from '@renderer/stores/editorStore'
import { useChatStore } from '@renderer/stores/chatStore'
import { useToastStore } from '@renderer/stores/toastStore'
import ColorThemePicker from '@renderer/modules/appearance/components/ColorThemePicker.vue'
import FileIconThemePicker from '@renderer/modules/appearance/components/FileIconThemePicker.vue'
import {
  listRecentWorkspaces,
  workspaceDisplayName,
  type RecentWorkspace,
} from '@renderer/utils/workspacePersistence'
import { PRODUCT_WEBSITE } from '@renderer/constants/productInfo'

const desktopApi = window.desktopApi

defineProps<{
  sidebarCollapsed: boolean
  chatCollapsed: boolean
  bottomPanelOpen: boolean
  bottomPanelTab: 'terminal' | 'problems'
  problemCount: number
  hasActiveEditorFile: boolean
}>()

const emit = defineEmits<{
  toggleSidebar: []
  toggleChat: []
  toggleTerminal: []
  showProblems: []
  openSearch: []
  openExplorer: []
  openGit: []
  revealInExplorer: []
}>()

const router = useRouter()
const agentStore = useAgentStore()
const workspaceStore = useWorkspaceStore()
const editorStore = useEditorStore()
const chat = useChatStore()
const { status, wsUrl } = storeToRefs(agentStore)

const openMenu = ref<string | null>(null)
const isWin = ref(false)
const recentWorkspaces = ref<RecentWorkspace[]>([])

const workspaceLabel = computed(() => {
  const root = workspaceStore.rootPath
  if (!root) return '未打开工作区'
  return root.replace(/\\/g, '/').split('/').pop() ?? root
})

const editorHint = computed(() => {
  const tab = editorStore.activeTab
  if (!tab) return null
  return tab.name
})

const statusTitle = computed(() => {
  switch (status.value) {
    case 'connected':
      return wsUrl.value ? `Agent 已连接 · ${wsUrl.value}` : 'Agent 已连接'
    case 'connecting':
      return '正在连接 Agent…'
    case 'disconnected':
      return 'Agent 未连接'
    case 'error':
      return 'Agent 连接异常'
    default:
      return status.value
  }
})

function toggleMenu(id: string) {
  if (id === 'file') {
    void listRecentWorkspaces().then(items => {
      recentWorkspaces.value = items
    })
  }
  openMenu.value = openMenu.value === id ? null : id
}

function normalizeWorkspaceKey(path: string): string {
  return path.replace(/\\/g, '/').replace(/\/+$/, '').toLowerCase()
}

function isCurrentWorkspace(path: string): boolean {
  const root = workspaceStore.rootPath
  if (!root) return false
  return normalizeWorkspaceKey(root) === normalizeWorkspaceKey(path)
}

function openNewWindow(): void {
  void window.desktopApi.newWindow()
}

function openFolderInNewWindow(): void {
  void window.desktopApi.openWorkspaceWindow(undefined, { openInNewWindow: true })
}

function openFolderInCurrentWindow(): void {
  void workspaceStore.openFolder()
}

/** Recent: switch this window, or focus another window that already has the project. */
async function openRecentInCurrentWindow(path: string): Promise<void> {
  if (isCurrentWorkspace(path)) return

  const focusedElsewhere = await window.desktopApi.focusWorkspaceWindow(path)
  if (focusedElsewhere) return

  const ok = await workspaceStore.openWorkspaceAt(path)
  if (!ok && workspaceStore.error) {
    useToastStore().show(workspaceStore.error)
  }
}

async function openRecentInNewWindow(path: string): Promise<void> {
  await window.desktopApi.openWorkspaceWindow(path, {
    forceNew: isCurrentWorkspace(path),
    openInNewWindow: isCurrentWorkspace(path),
  })
}

async function onRecentClick(path: string, event: MouseEvent): Promise<void> {
  if (event.shiftKey) {
    await openRecentInNewWindow(path)
    return
  }
  await openRecentInCurrentWindow(path)
}

function closeMenus() {
  openMenu.value = null
}

function run(action: () => void) {
  closeMenus()
  action()
}

function onDocClick(e: MouseEvent) {
  if ((e.target as HTMLElement).closest('.ide-top-bar__menu-item')) return
  closeMenus()
}

onMounted(() => {
  isWin.value = window.desktopEnv?.platform === 'win32'
  document.addEventListener('click', onDocClick)
})

onUnmounted(() => document.removeEventListener('click', onDocClick))
</script>

<template>
  <header class="ide-top-bar" :class="{ 'ide-top-bar--win': isWin }">
    <div class="ide-top-bar__brand">
      <svg class="ide-top-bar__logo" viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
        <path fill="var(--ide-accent)" d="M12 2 2 7l10 5 10-5-10-5zm0 7L2 14l10 5 10-5-10-5z" />
      </svg>
      <span class="ide-top-bar__app">Agent Desktop</span>
    </div>

    <div class="ide-top-bar__divider" aria-hidden="true" />

    <nav class="ide-top-bar__menus" aria-label="应用菜单">
      <div class="ide-top-bar__menu-item">
        <button type="button" class="ide-top-bar__trigger" @click.stop="toggleMenu('file')">文件</button>
        <div v-if="openMenu === 'file'" class="ide-top-bar__dropdown">
          <button type="button" @click="run(() => openNewWindow())">新建窗口</button>
          <button type="button" @click="run(() => openFolderInCurrentWindow())">打开文件夹…</button>
          <button type="button" @click="run(() => openFolderInNewWindow())">在新窗口打开文件夹…</button>
          <div class="ide-top-bar__dropdown-sep" aria-hidden="true" />
          <div class="ide-top-bar__dropdown-label">最近打开</div>
          <button
            v-for="item in recentWorkspaces"
            :key="item.path"
            type="button"
            class="ide-top-bar__recent-entry"
            :class="{ 'ide-top-bar__recent-entry--active': isCurrentWorkspace(item.path) }"
            :title="item.path"
            @click="run(() => void onRecentClick(item.path, $event))"
          >
            {{ workspaceDisplayName(item.path) }}
          </button>
          <button v-if="!recentWorkspaces.length" type="button" disabled class="ide-top-bar__recent-empty">
            暂无最近项目
          </button>
          <div class="ide-top-bar__dropdown-sep" aria-hidden="true" />
          <button
            type="button"
            :disabled="!editorStore.activeTab?.dirty"
            @click="run(() => void editorStore.saveActive())"
          >
            保存 <span class="ide-top-bar__kbd">Ctrl+S</span>
          </button>
        </div>
      </div>

      <div class="ide-top-bar__menu-item">
        <button type="button" class="ide-top-bar__trigger" @click.stop="toggleMenu('view')">查看</button>
        <div v-if="openMenu === 'view'" class="ide-top-bar__dropdown">
          <div class="ide-top-bar__dropdown-label">侧栏面板</div>
          <button type="button" @click="run(() => emit('openExplorer'))">
            资源管理器 <span class="ide-top-bar__kbd">Ctrl+Shift+E</span>
          </button>
          <button type="button" @click="run(() => emit('openSearch'))">
            搜索… <span class="ide-top-bar__kbd">Ctrl+Shift+F</span>
          </button>
          <button type="button" @click="run(() => emit('openGit'))">
            源代码管理 <span class="ide-top-bar__kbd">Ctrl+Shift+G</span>
          </button>
          <div class="ide-top-bar__dropdown-sep" aria-hidden="true" />
          <div class="ide-top-bar__dropdown-label">布局</div>
          <button type="button" @click="run(() => emit('toggleTerminal'))">
            {{ bottomPanelOpen && bottomPanelTab === 'terminal' ? '隐藏终端' : '显示终端' }}
            <span class="ide-top-bar__kbd">Ctrl+`</span>
          </button>
          <button type="button" @click="run(() => emit('showProblems'))">
            {{ bottomPanelOpen && bottomPanelTab === 'problems' ? '隐藏问题' : '显示问题' }}
            <span v-if="problemCount" class="ide-top-bar__menu-badge">{{ problemCount }}</span>
            <span class="ide-top-bar__kbd">Ctrl+Shift+M</span>
          </button>
          <button type="button" @click="run(() => emit('toggleChat'))">
            {{ chatCollapsed ? '显示' : '隐藏' }}Agent 面板
          </button>
          <button type="button" @click="run(() => emit('toggleSidebar'))">
            {{ sidebarCollapsed ? '显示' : '隐藏' }}侧栏
          </button>
          <div class="ide-top-bar__dropdown-sep" aria-hidden="true" />
          <button
            type="button"
            :disabled="!hasActiveEditorFile"
            @click="run(() => emit('revealInExplorer'))"
          >
            在资源管理器中定位
          </button>
        </div>
      </div>

      <div class="ide-top-bar__menu-item">
        <button type="button" class="ide-top-bar__trigger" @click.stop="toggleMenu('agent')">Agent</button>
        <div v-if="openMenu === 'agent'" class="ide-top-bar__dropdown">
          <button type="button" @click="run(() => chat.newSession())">新对话</button>
          <button type="button" @click="run(() => void chat.reconnect())">重连后端</button>
          <button type="button" @click="run(() => void chat.restart())">重启 Agent</button>
        </div>
      </div>

      <div class="ide-top-bar__menu-item">
        <button type="button" class="ide-top-bar__trigger" @click.stop="toggleMenu('colorTheme')">
          颜色主题
        </button>
        <div v-if="openMenu === 'colorTheme'" class="ide-top-bar__dropdown ide-top-bar__dropdown--theme">
          <ColorThemePicker @select="closeMenus" />
        </div>
      </div>

      <div class="ide-top-bar__menu-item">
        <button type="button" class="ide-top-bar__trigger" @click.stop="toggleMenu('fileIconTheme')">
          文件图标
        </button>
        <div v-if="openMenu === 'fileIconTheme'" class="ide-top-bar__dropdown ide-top-bar__dropdown--theme">
          <FileIconThemePicker @select="closeMenus" />
        </div>
      </div>

      <div class="ide-top-bar__menu-item">
        <button type="button" class="ide-top-bar__trigger" @click.stop="toggleMenu('help')">帮助</button>
        <div v-if="openMenu === 'help'" class="ide-top-bar__dropdown">
          <button type="button" @click="run(() => void router.push('/help'))">系统帮助文档</button>
          <button
            type="button"
            @click="run(() => void desktopApi.openExternal(PRODUCT_WEBSITE))"
          >
            访问官网
          </button>
          <div class="ide-top-bar__dropdown-sep" aria-hidden="true" />
          <button type="button" @click="run(() => void router.push('/settings'))">设置…</button>
        </div>
      </div>
    </nav>

    <div class="ide-top-bar__center" :title="workspaceStore.rootPath ?? undefined">
      <span class="ide-top-bar__workspace">{{ workspaceLabel }}</span>
      <template v-if="editorHint">
        <span class="ide-top-bar__sep">›</span>
        <span class="ide-top-bar__file">{{ editorHint }}</span>
      </template>
    </div>

    <div class="ide-top-bar__actions">
      <button
        type="button"
        class="ide-top-bar__status"
        :class="`ide-top-bar__status--${status}`"
        :title="statusTitle"
        @click="void router.push('/settings')"
      >
        <span class="ide-top-bar__dot" aria-hidden="true" />
      </button>
    </div>
  </header>
</template>

<style scoped>
.ide-top-bar {
  display: flex;
  align-items: stretch;
  height: var(--ide-topbar-height, 35px);
  padding: 0 8px;
  background: var(--ide-titlebar-bg);
  border-bottom: 1px solid var(--ide-border);
  flex-shrink: 0;
  -webkit-app-region: drag;
}

.ide-top-bar--win {
  padding-right: var(--ide-window-controls-width, 138px);
}

.ide-top-bar__brand {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  padding-right: 4px;
  -webkit-app-region: no-drag;
}

.ide-top-bar__logo {
  flex-shrink: 0;
}

.ide-top-bar__app {
  font-size: 12px;
  font-weight: 600;
  color: var(--ide-fg);
  letter-spacing: 0.02em;
  white-space: nowrap;
}

.ide-top-bar__menus {
  display: flex;
  align-items: stretch;
  gap: 2px;
  flex-shrink: 0;
  -webkit-app-region: no-drag;
}

.ide-top-bar__menu-item {
  position: relative;
}

.ide-top-bar__trigger {
  height: 100%;
  padding: 0 10px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--ide-fg);
  font-size: 13px;
  cursor: pointer;
}

.ide-top-bar__trigger:hover,
.ide-top-bar__menu-item:has(.ide-top-bar__dropdown) .ide-top-bar__trigger {
  background: var(--ide-hover-overlay);
}

.ide-top-bar__dropdown {
  position: absolute;
  top: calc(100% + 2px);
  left: 0;
  z-index: 100;
  min-width: 200px;
  padding: 4px;
  border-radius: 6px;
  border: 1px solid var(--ide-border);
  background: var(--ide-dropdown-bg);
  box-shadow: var(--ide-shadow);
}

.ide-top-bar__dropdown--theme {
  min-width: 240px;
  padding: 6px;
}

.ide-top-bar__dropdown-sep {
  height: 1px;
  margin: 4px 0;
  background: var(--ide-border);
}

.ide-top-bar__dropdown-label {
  padding: 4px 10px 2px;
  font-size: 11px;
  font-weight: 600;
  color: var(--ide-muted);
  text-transform: none;
  pointer-events: none;
}

.ide-top-bar__recent-entry {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ide-top-bar__recent-entry--active {
  font-weight: 600;
}

.ide-top-bar__recent-empty {
  justify-content: flex-start !important;
  font-size: 12px;
  color: var(--ide-muted);
}

.ide-top-bar__dropdown--theme :deep(.theme-picker__item),
.ide-top-bar__dropdown--theme :deep(.icon-theme-picker__item) {
  flex-direction: column;
  align-items: flex-start;
  justify-content: flex-start;
}

.ide-top-bar__dropdown--theme :deep(.theme-picker__item:hover:not(.active)),
.ide-top-bar__dropdown--theme :deep(.icon-theme-picker__item:hover:not(.active)) {
  background: var(--ide-hover-overlay);
  color: var(--ide-fg);
}

.ide-top-bar__dropdown--theme :deep(.theme-picker__item.active),
.ide-top-bar__dropdown--theme :deep(.icon-theme-picker__item.active) {
  background: var(--ide-list-active-bg);
  color: var(--ide-list-active-fg);
}

.ide-top-bar__dropdown button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 6px 10px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--ide-fg);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.ide-top-bar__dropdown button:hover:not(:disabled) {
  background: var(--ide-accent);
  color: var(--ide-on-accent);
}

.ide-top-bar__dropdown button:disabled {
  opacity: 0.45;
  cursor: default;
}

.ide-top-bar__kbd {
  font-size: 11px;
  opacity: 0.65;
  margin-left: 16px;
}

.ide-top-bar__menu-badge {
  margin-left: 8px;
  padding: 0 5px;
  border-radius: 8px;
  font-size: 10px;
  color: var(--ide-error);
  background: color-mix(in srgb, var(--ide-error) 16%, transparent);
}

.ide-top-bar__divider {
  width: 1px;
  height: 16px;
  margin: 0 8px 0 4px;
  background: var(--ide-border);
  flex-shrink: 0;
  align-self: center;
  -webkit-app-region: no-drag;
}

.ide-top-bar__center {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-width: 0;
  padding: 0 12px;
  font-size: 12px;
  color: var(--ide-muted);
}

.ide-top-bar__workspace {
  color: var(--ide-fg);
  font-weight: 500;
}

.ide-top-bar__sep {
  opacity: 0.45;
}

.ide-top-bar__file {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: min(280px, 30vw);
}

.ide-top-bar__actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  -webkit-app-region: no-drag;
}

.ide-top-bar__status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: none;
  border-radius: 4px;
  background: transparent;
  cursor: pointer;
}

.ide-top-bar__status:hover {
  background: var(--ide-hover-overlay);
}

.ide-top-bar__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--ide-muted);
}

.ide-top-bar__status--connected .ide-top-bar__dot {
  background: var(--ide-success);
}

.ide-top-bar__status--connecting .ide-top-bar__dot {
  background: var(--ide-warning);
  animation: ide-pulse 1.2s ease-in-out infinite;
}

.ide-top-bar__status--disconnected .ide-top-bar__dot,
.ide-top-bar__status--error .ide-top-bar__dot {
  background: var(--ide-error);
}

@keyframes ide-pulse {
  0%,
  100% {
    opacity: 0.45;
  }
  50% {
    opacity: 1;
  }
}
</style>
