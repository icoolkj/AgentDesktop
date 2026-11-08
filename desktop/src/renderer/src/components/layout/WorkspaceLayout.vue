<script setup lang="ts">
// KeepAlive 在下方模板中使用；TS 规则无法识别 Vue 模板中的组件引用
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import { computed, KeepAlive, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import IdeTopBar from '@renderer/components/layout/IdeTopBar.vue'
import ActivityBar from '@renderer/components/layout/ActivityBar.vue'
import ConnectionBanner from '@renderer/components/layout/ConnectionBanner.vue'
import ResizeHandle from '@renderer/components/layout/ResizeHandle.vue'
import FileTreeSidebar from '@renderer/components/sidebar/FileTreeSidebar.vue'
import SearchSidebar from '@renderer/components/sidebar/SearchSidebar.vue'
import GitSidebar from '@renderer/components/sidebar/GitSidebar.vue'
import ChatPanel from '@renderer/components/chat/ChatPanel.vue'
import ExplorerPromptDialog from '@renderer/components/sidebar/ExplorerPromptDialog.vue'
import ToastHost from '@renderer/components/common/ToastHost.vue'
import TerminalPanel from '@renderer/components/terminal/TerminalPanel.vue'
import ProblemsPanel from '@renderer/components/problems/ProblemsPanel.vue'
import CommandPalette from '@renderer/components/common/CommandPalette.vue'
import { useToastStore } from '@renderer/stores/toastStore'
import { useProblemsStore } from '@renderer/stores/problemsStore'
import { useWorkspaceUiStore } from '@renderer/stores/workspaceUiStore'
import {
  BOTTOM_PANEL_MIN_EDITOR_HEIGHT,
  BOTTOM_PANEL_MIN_HEIGHT,
  useBottomPanelLayout,
} from '@renderer/composables/useBottomPanelLayout'
import { useResizablePanels } from '@renderer/composables/useResizablePanels'
import { useSidebarStore, type SidebarPanel } from '@renderer/stores/sidebarStore'
import { useSearchStore } from '@renderer/stores/searchStore'
import { useEditorStore } from '@renderer/stores/editorStore'
import { useInlineEditStore } from '@renderer/stores/inlineEditStore'
import { openFindInFile, openReplaceInFile } from '@renderer/utils/editorSearchBridge'
import {
  registerSlashPaletteCommands,
  registerWorkspaceCommands,
  type CommandItem,
} from '@renderer/utils/commandRegistry'

const route = useRoute()
const router = useRouter()
const sidebarStore = useSidebarStore()
const searchStore = useSearchStore()
const editorStore = useEditorStore()
const problemsStore = useProblemsStore()
const workspaceUi = useWorkspaceUiStore()
const { activePanel } = storeToRefs(sidebarStore)
const { errorCount, warningCount } = storeToRefs(problemsStore)

const searchSidebarRef = ref<InstanceType<typeof SearchSidebar> | null>(null)
const terminalPanelRef = ref<InstanceType<typeof TerminalPanel> | null>(null)
const centerPaneRef = ref<HTMLElement | null>(null)
const commandPaletteOpen = ref(false)

function getMaxBottomPanelHeight(): number {
  const height = centerPaneRef.value?.clientHeight ?? 0
  if (height <= 0) return BOTTOM_PANEL_MIN_HEIGHT
  return Math.max(BOTTOM_PANEL_MIN_HEIGHT, height - BOTTOM_PANEL_MIN_EDITOR_HEIGHT)
}

const {
  bottomPanelOpen,
  bottomPanelTab,
  bottomPanelHeight,
  load: loadBottomPanelLayout,
  applyFromPrefs,
  resizeBottomPanel: resizeBottomPanelHeight,
  enforceHeightLimit,
  maximizeBottomPanel,
  openBottomPanel,
  toggleBottomPanel,
  onBottomPanelTabChange,
} = useBottomPanelLayout(getMaxBottomPanelHeight)

function refitTerminalSoon(): void {
  window.requestAnimationFrame(() => terminalPanelRef.value?.refit())
}

function resizeBottomPanel(delta: number) {
  resizeBottomPanelHeight(delta)
  refitTerminalSoon()
}

function maximizeBottomPanelWithRefit() {
  maximizeBottomPanel()
  refitTerminalSoon()
}

function onCenterPaneResize() {
  enforceHeightLimit()
  if (bottomPanelOpen.value && bottomPanelTab.value === 'terminal') {
    refitTerminalSoon()
  }
}

function openBottomPanelWithRefit(tab: Parameters<typeof openBottomPanel>[0] = 'terminal') {
  openBottomPanel(tab)
  if (tab === 'terminal') refitTerminalSoon()
}

function toggleTerminal() {
  toggleBottomPanel('terminal')
  if (bottomPanelOpen.value && bottomPanelTab.value === 'terminal') refitTerminalSoon()
}

function showProblemsPanel() {
  openBottomPanelWithRefit('problems')
}

function onBottomPanelTabChangeWithRefit(tab: Parameters<typeof onBottomPanelTabChange>[0]) {
  onBottomPanelTabChange(tab)
  if (tab === 'terminal') refitTerminalSoon()
}

let centerPaneResizeObserver: ResizeObserver | null = null

watch(
  () => [workspaceUi.bottomPanelOpenOnStartup, workspaceUi.bottomPanelTabOnStartup] as const,
  () => {
    applyFromPrefs()
    if (bottomPanelOpen.value && bottomPanelTab.value === 'terminal') refitTerminalSoon()
  },
)

const {
  sidebarWidth,
  chatWidth,
  sidebarCollapsed,
  chatCollapsed,
  resizeSidebar,
  resizeChat,
  resetSidebarWidth,
  resetChatWidth,
  collapseSidebar,
  expandSidebar,
  collapseChat,
  expandChat,
  flushPanelLayout,
} = useResizablePanels()

const onSettings = computed(() => route.matched.some(record => record.name === 'settings'))

function toggleSidebar() {
  if (sidebarCollapsed.value) expandSidebar()
  else collapseSidebar()
}

function toggleChat() {
  if (chatCollapsed.value) expandChat()
  else collapseChat()
}

function openSidebarPanel(panel: SidebarPanel) {
  if (onSettings.value) {
    void router.push('/workspace')
  }
  sidebarStore.setPanel(panel)
  if (sidebarCollapsed.value) expandSidebar()
}

function openExplorerPanel() {
  openSidebarPanel('explorer')
}

function openGitPanel() {
  openSidebarPanel('git')
}

function revealInExplorer() {
  if (!editorStore.activePath) {
    useToastStore().show('请先打开一个文件')
    return
  }
  openSidebarPanel('explorer')
  editorStore.revealActiveInTree()
}

function onOpenSearch() {
  openSidebarPanel('search')
  searchStore.setMode('workspace')
  searchSidebarRef.value?.focusQueryInput()
}

function focusChatComposer() {
  if (chatCollapsed.value) expandChat()
  window.dispatchEvent(new CustomEvent('agent:focus-composer'))
}

function openInlineEdit() {
  useInlineEditStore().openWidget()
}

function openCommandPalette() {
  commandPaletteOpen.value = true
}

function onCommandRun(command: CommandItem) {
  void command.run()
}

function onGlobalKeyDown(event: KeyboardEvent) {
  const mod = event.ctrlKey || event.metaKey
  if (!mod) return

  const key = event.key.toLowerCase()
  if (key === 'p' && event.shiftKey) {
    event.preventDefault()
    openCommandPalette()
    return
  }

  if (key === 'f' && event.shiftKey) {
    event.preventDefault()
    openSidebarPanel('search')
    searchStore.setMode('workspace')
    searchSidebarRef.value?.focusQueryInput()
    return
  }

  if (key === 'e' && event.shiftKey) {
    event.preventDefault()
    openExplorerPanel()
    return
  }

  if (key === 'g' && event.shiftKey) {
    event.preventDefault()
    openGitPanel()
    return
  }

  if (key === '`' && !event.shiftKey) {
    event.preventDefault()
    toggleTerminal()
    return
  }

  if (key === 'm' && event.shiftKey) {
    event.preventDefault()
    showProblemsPanel()
    return
  }

  if (key === 'k' && !event.shiftKey) {
    event.preventDefault()
    openInlineEdit()
    return
  }

  if (key === 'f' && !event.shiftKey) {
    event.preventDefault()
    openSidebarPanel('search')
    searchStore.setMode('file')
    if (editorStore.activePath) {
      openFindInFile(editorStore.activePath, {
        query: searchStore.query.trim(),
        caseSensitive: searchStore.caseSensitive,
        wholeWord: searchStore.wholeWord,
      })
    } else {
      searchSidebarRef.value?.focusQueryInput()
    }
    return
  }

  if (key === 'h') {
    event.preventDefault()
    openSidebarPanel('search')
    searchStore.setMode('replace')
    if (editorStore.activePath) {
      openReplaceInFile(editorStore.activePath, {
        query: searchStore.query.trim(),
        replace: searchStore.replaceText,
        caseSensitive: searchStore.caseSensitive,
        wholeWord: searchStore.wholeWord,
      })
    } else {
      searchSidebarRef.value?.focusQueryInput()
    }
  }
}

onMounted(async () => {
  await useWorkspaceUiStore().init()
  await loadBottomPanelLayout()
  enforceHeightLimit()
  if (centerPaneRef.value) {
    centerPaneResizeObserver = new ResizeObserver(() => onCenterPaneResize())
    centerPaneResizeObserver.observe(centerPaneRef.value)
  }
  problemsStore.init()
  registerWorkspaceCommands({
    router,
    toggleSidebar,
    toggleChat,
    toggleTerminal,
    showProblems: showProblemsPanel,
    scanProblems: () => problemsStore.runWorkspaceScan(),
    openSearch: onOpenSearch,
    openExplorer: openExplorerPanel,
    openGit: openGitPanel,
    revealInExplorer,
    focusChat: focusChatComposer,
    openInlineEdit,
  })
  registerSlashPaletteCommands(command => {
    focusChatComposer()
    window.dispatchEvent(new CustomEvent('agent:insert-slash', { detail: command }))
  })
  window.addEventListener('keydown', onGlobalKeyDown)
})

onUnmounted(() => {
  centerPaneResizeObserver?.disconnect()
  centerPaneResizeObserver = null
  window.removeEventListener('keydown', onGlobalKeyDown)
  problemsStore.dispose()
})
</script>

<template>
  <div class="ide-shell">
    <IdeTopBar
      :sidebar-collapsed="sidebarCollapsed"
      :chat-collapsed="chatCollapsed"
      :bottom-panel-open="bottomPanelOpen"
      :bottom-panel-tab="bottomPanelTab"
      :problem-count="errorCount + warningCount"
      :has-active-editor-file="Boolean(editorStore.activePath)"
      @toggle-sidebar="toggleSidebar"
      @toggle-chat="toggleChat"
      @toggle-terminal="toggleTerminal"
      @show-problems="showProblemsPanel"
      @open-search="onOpenSearch"
      @open-explorer="openExplorerPanel"
      @open-git="openGitPanel"
      @reveal-in-explorer="revealInExplorer"
    />
    <ConnectionBanner />

    <div class="ide-main">
      <ActivityBar
        :sidebar-collapsed="sidebarCollapsed"
        :chat-collapsed="chatCollapsed"
        @toggle-sidebar="toggleSidebar"
        @toggle-chat="toggleChat"
        @open-panel="openSidebarPanel"
      />

      <div class="ide-workbench">
        <template v-if="!onSettings">
          <div
            v-show="!sidebarCollapsed"
            class="ide-pane ide-pane--sidebar"
            :style="{ width: `${sidebarWidth}px` }"
          >
            <FileTreeSidebar
              v-show="activePanel === 'explorer'"
              @collapse="collapseSidebar"
            />
            <SearchSidebar
              v-show="activePanel === 'search'"
              ref="searchSidebarRef"
              @collapse="collapseSidebar"
            />
            <GitSidebar
              v-show="activePanel === 'git'"
              @collapse="collapseSidebar"
            />
          </div>

          <ResizeHandle
            v-if="!sidebarCollapsed"
            @resize="resizeSidebar"
            @resize-end="flushPanelLayout"
            @reset="resetSidebarWidth"
          />
        </template>

        <div ref="centerPaneRef" class="ide-pane ide-pane--center">
          <div class="ide-center-view-host">
            <router-view v-slot="{ Component }">
              <KeepAlive include="WorkspaceContent">
                <component :is="Component" class="ide-center-view" />
              </KeepAlive>
            </router-view>
          </div>

          <div v-show="!onSettings" class="workspace-bottom-panel-host">
            <ResizeHandle
              v-show="bottomPanelOpen"
              axis="y"
              @resize="resizeBottomPanel"
              @reset="maximizeBottomPanelWithRefit"
            />
            <div
              v-show="bottomPanelOpen"
              class="workspace-bottom-panel"
              :style="{ height: `${bottomPanelHeight}px` }"
            >
              <div class="workspace-bottom-panel__bar">
                <div class="workspace-bottom-panel__tabs">
                  <button
                    type="button"
                    class="workspace-bottom-panel__tab"
                    :class="{ 'workspace-bottom-panel__tab--active': bottomPanelTab === 'terminal' }"
                    @click="onBottomPanelTabChangeWithRefit('terminal')"
                  >
                    终端
                  </button>
                  <button
                    type="button"
                    class="workspace-bottom-panel__tab"
                    :class="{ 'workspace-bottom-panel__tab--active': bottomPanelTab === 'problems' }"
                    @click="onBottomPanelTabChangeWithRefit('problems')"
                  >
                    问题
                    <span v-if="errorCount + warningCount" class="workspace-bottom-panel__badge">
                      {{ errorCount + warningCount }}
                    </span>
                  </button>
                </div>
                <button type="button" class="workspace-bottom-panel__hide" @click="bottomPanelOpen = false">
                  隐藏
                </button>
              </div>
              <div class="workspace-bottom-panel__body">
                <TerminalPanel
                  ref="terminalPanelRef"
                  v-show="bottomPanelTab === 'terminal'"
                  :visible="bottomPanelOpen && bottomPanelTab === 'terminal'"
                />
                <ProblemsPanel v-show="bottomPanelTab === 'problems'" />
              </div>
            </div>
            <button
              v-show="!bottomPanelOpen"
              type="button"
              class="workspace-bottom-panel__restore"
              @click="openBottomPanelWithRefit('terminal')"
            >
              显示面板（终端 / 问题）
            </button>
          </div>
        </div>

        <template v-if="!onSettings">
          <ResizeHandle
            v-if="!chatCollapsed"
            @resize="resizeChat"
            @resize-end="flushPanelLayout"
            @reset="resetChatWidth"
          />

          <div
            v-show="!chatCollapsed"
            class="ide-pane ide-pane--chat"
            :style="{ width: `${chatWidth}px` }"
          >
            <ChatPanel @collapse="collapseChat" />
          </div>
        </template>
      </div>
    </div>

    <ExplorerPromptDialog />
    <ToastHost />
    <CommandPalette
      :open="commandPaletteOpen"
      @close="commandPaletteOpen = false"
      @run="onCommandRun"
    />
  </div>
</template>

<style scoped>
.ide-main {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.ide-workbench {
  display: flex;
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.workspace-bottom-panel-host {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.workspace-bottom-panel {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-top: 1px solid var(--ide-border);
  background: var(--ide-terminal-bg);
  color: var(--ide-terminal-fg);
}

.workspace-bottom-panel :deep(.terminal-panel),
.workspace-bottom-panel :deep(.problems-panel) {
  min-height: 0;
}

.workspace-bottom-panel__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 4px 8px;
  font-size: 11px;
  color: var(--ide-muted);
  border-bottom: 1px solid var(--ide-border);
  flex-shrink: 0;
}

.workspace-bottom-panel__body {
  flex: 1;
  min-height: 0;
  position: relative;
  overflow: hidden;
}

.workspace-bottom-panel__body :deep(.terminal-panel),
.workspace-bottom-panel__body :deep(.problems-panel) {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.workspace-bottom-panel__tabs {
  display: flex;
  gap: 4px;
}

.workspace-bottom-panel__tab {
  border: none;
  background: transparent;
  color: var(--ide-muted);
  cursor: pointer;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
}

.workspace-bottom-panel__tab--active {
  color: var(--ide-fg);
  background: color-mix(in srgb, var(--ide-accent) 12%, transparent);
}

.workspace-bottom-panel__badge {
  margin-left: 4px;
  color: var(--ide-error);
}

.workspace-bottom-panel__hide,
.workspace-bottom-panel__restore {
  border: none;
  background: transparent;
  color: var(--ide-accent);
  cursor: pointer;
  font-size: 11px;
}

.workspace-bottom-panel__restore {
  flex-shrink: 0;
  padding: 4px 8px;
  text-align: left;
  border-top: 1px solid var(--ide-border);
}
</style>
