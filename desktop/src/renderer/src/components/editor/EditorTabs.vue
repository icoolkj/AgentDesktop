<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useEditorStore } from '@renderer/stores/editorStore'
import { useGitStore } from '@renderer/stores/gitStore'
import { useSessionChangesStore } from '@renderer/stores/sessionChangesStore'
import { badgeForKind, decorationKindFromStatus } from '@renderer/utils/gitTreeDecorations'

const editor = useEditorStore()
const gitStore = useGitStore()
const sessionChanges = useSessionChangesStore()

const contextMenu = ref<{
  path: string
  x: number
  y: number
} | null>(null)

const contextTabIndex = computed(() => {
  if (!contextMenu.value) return -1
  return editor.tabs.findIndex(tab => tab.path === contextMenu.value!.path)
})

const canCloseLeft = computed(() => contextTabIndex.value > 0)
const canCloseRight = computed(
  () => contextTabIndex.value >= 0 && contextTabIndex.value < editor.tabs.length - 1,
)
const canCloseOthers = computed(() => editor.tabs.length > 1)

function onSelect(path: string): void {
  closeContextMenu()
  const tab = editor.tabs.find(t => t.path === path)
  if (tab && !tab.loading) {
    editor.activateTab(path)
    return
  }
  void editor.openFile(path, { reveal: false })
}

function onClose(event: MouseEvent, path: string): void {
  event.stopPropagation()
  closeContextMenu()
  void editor.closeTab(path)
}

function onTabContextMenu(event: MouseEvent, path: string): void {
  event.preventDefault()
  void editor.openFile(path)
  contextMenu.value = { path, x: event.clientX, y: event.clientY }
}

function closeContextMenu(): void {
  contextMenu.value = null
}

async function runClose(action: () => Promise<boolean>): Promise<void> {
  await action()
  closeContextMenu()
}

function onDocumentClick(event: MouseEvent): void {
  if ((event.target as HTMLElement).closest('.editor-tab-menu')) return
  closeContextMenu()
}

function onDocumentKeyDown(event: KeyboardEvent): void {
  if (event.key === 'Escape') closeContextMenu()
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onDocumentKeyDown)
})

onUnmounted(() => {
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onDocumentKeyDown)
})
</script>

<template>
  <div v-if="editor.tabs.length" class="editor-tabs">
    <button
      v-for="tab in editor.tabs"
      :key="tab.path"
      type="button"
      class="editor-tab"
      :class="{
        active: tab.path === editor.activePath,
        dirty: tab.dirty,
        'disk-ahead': tab.diskAhead,
        loading: tab.loading,
        'git-diff-tab': tab.viewKind === 'git-diff',
        'agent-modified': sessionChanges.isChanged(tab.path),
        'git-modified': gitStore.statusForPath(tab.path) != null && tab.viewKind !== 'git-diff',
      }"
      @click="onSelect(tab.path)"
      @contextmenu="onTabContextMenu($event, tab.path)"
    >
      <span class="editor-tab__label">
        <span v-if="tab.diskAhead" class="editor-tab__conflict" title="Agent 已在磁盘更新，与未保存编辑冲突">!</span>
        <span v-if="tab.dirty" class="editor-tab__dot" aria-hidden="true">●</span>
        {{ tab.name }}
        <span
          v-if="tab.viewKind === 'git-diff' && gitStore.statusForPath(tab.path)"
          class="editor-tab__git-badge"
        >
          {{ badgeForKind(decorationKindFromStatus(gitStore.statusForPath(tab.path)!)) }}
        </span>
      </span>
      <span class="editor-tab__close" @click="onClose($event, tab.path)">×</span>
    </button>

    <div
      v-if="contextMenu"
      class="editor-tab-menu"
      :style="{ left: `${contextMenu.x}px`, top: `${contextMenu.y}px` }"
      role="menu"
      @click.stop
    >
      <button type="button" role="menuitem" @click="void runClose(() => editor.closeTab(contextMenu!.path))">
        关闭
      </button>
      <button
        type="button"
        role="menuitem"
        :disabled="!canCloseOthers"
        @click="void runClose(() => editor.closeOtherTabs(contextMenu!.path))"
      >
        关闭其他
      </button>
      <button
        type="button"
        role="menuitem"
        :disabled="!canCloseLeft"
        @click="void runClose(() => editor.closeTabsToLeft(contextMenu!.path))"
      >
        关闭左侧
      </button>
      <button
        type="button"
        role="menuitem"
        :disabled="!canCloseRight"
        @click="void runClose(() => editor.closeTabsToRight(contextMenu!.path))"
      >
        关闭右侧
      </button>
      <div class="editor-tab-menu__sep" aria-hidden="true" />
      <button type="button" role="menuitem" @click="void runClose(() => editor.closeAllTabs())">
        关闭全部
      </button>
    </div>
  </div>
</template>

<style scoped>
.editor-tabs {
  display: flex;
  align-items: stretch;
  gap: 0;
  min-height: 35px;
  overflow-x: auto;
  overflow-y: hidden;
  background: var(--ide-tab-bar-bg, #252526);
  border-bottom: 1px solid var(--ide-border, #3c3c3c);
  scrollbar-width: thin;
  scrollbar-color: var(--ide-scrollbar-thumb) transparent;
  position: relative;
}

.editor-tabs::-webkit-scrollbar {
  height: 3px;
}

.editor-tabs::-webkit-scrollbar-track {
  background: transparent;
}

.editor-tabs::-webkit-scrollbar-thumb {
  background: var(--ide-scrollbar-thumb);
  border-radius: 999px;
}

.editor-tabs:hover::-webkit-scrollbar-thumb {
  background: color-mix(in srgb, var(--ide-scrollbar-thumb) 80%, var(--ide-fg));
}

.editor-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 200px;
  min-height: 35px;
  padding: 0 10px;
  border: none;
  border-right: 1px solid var(--ide-border, #3c3c3c);
  background: transparent;
  color: var(--ide-muted, #969696);
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
}

.editor-tab:hover {
  background: var(--ide-hover-overlay);
}

.editor-tab.active {
  background: var(--ide-editor-bg, #1e1e1e);
  color: var(--ide-fg, #cccccc);
}

.editor-tab.git-diff-tab .editor-tab__label {
  color: var(--ide-accent);
}

.editor-tab.git-diff-tab.active .editor-tab__label {
  color: color-mix(in srgb, var(--ide-accent) 75%, var(--ide-fg));
}

.editor-tab.agent-modified .editor-tab__label {
  color: var(--ide-git-modified);
}

.editor-tab.agent-modified.active .editor-tab__label {
  color: var(--ide-list-active-fg);
}

.editor-tab.git-modified .editor-tab__label {
  color: var(--ide-git-modified);
}

.editor-tab.git-modified.active .editor-tab__label {
  color: var(--ide-list-active-fg);
}

.editor-tab.loading .editor-tab__label {
  opacity: 0.6;
}

.editor-tab__label {
  overflow: hidden;
  text-overflow: ellipsis;
}

.editor-tab__git-badge {
  margin-left: 4px;
  font-size: 11px;
  color: var(--ide-git-untracked);
  font-weight: 600;
}

.editor-tab__dot {
  margin-right: 4px;
  color: var(--ide-accent, #3794ff);
  font-size: 10px;
}

.editor-tab__conflict {
  margin-right: 4px;
  color: var(--ide-warning, #d7a038);
  font-size: 10px;
  font-weight: 700;
}

.editor-tab.disk-ahead {
  border-bottom: 2px solid var(--ide-warning, #d7a038);
}

.editor-tab__close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 4px;
  opacity: 0.7;
}

.editor-tab__close:hover {
  opacity: 1;
  background: var(--ide-hover-overlay);
}

.editor-tab-menu {
  position: fixed;
  z-index: 200;
  min-width: 168px;
  padding: 4px;
  border: 1px solid var(--ide-border);
  border-radius: 6px;
  background: var(--ide-dropdown-bg);
  box-shadow: var(--ide-shadow);
}

.editor-tab-menu button {
  display: block;
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

.editor-tab-menu button:hover:not(:disabled) {
  background: var(--ide-accent);
  color: var(--ide-on-accent);
}

.editor-tab-menu button:disabled {
  opacity: 0.45;
  cursor: default;
}

.editor-tab-menu__sep {
  height: 1px;
  margin: 4px 0;
  background: var(--ide-border);
}
</style>
