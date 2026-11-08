<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useWorkspaceStore } from '@renderer/stores/workspaceStore'
import { useEditorStore } from '@renderer/stores/editorStore'

const workspace = useWorkspaceStore()
const editor = useEditorStore()
const { contextMenu } = storeToRefs(workspace)

async function onOpen(): Promise<void> {
  const path = contextMenu.value?.kind === 'file' ? contextMenu.value.path : null
  workspace.closeContextMenu()
  if (path) await editor.openFile(path)
}

async function onCreateFile(): Promise<void> {
  const menu = contextMenu.value
  const targetDir =
    menu?.kind === 'directory' || menu?.kind === 'root' ? menu.targetDir : undefined
  workspace.closeContextMenu()
  const filePath = await workspace.createFile(targetDir)
  if (filePath) await editor.openFile(filePath)
}

async function onCreateFolder(): Promise<void> {
  const menu = contextMenu.value
  const targetDir =
    menu?.kind === 'directory' || menu?.kind === 'root' ? menu.targetDir : undefined
  workspace.closeContextMenu()
  await workspace.createFolder(targetDir)
}

function onRefresh(): void {
  workspace.closeContextMenu()
  void workspace.refreshTree()
}

async function onRename(): Promise<void> {
  const menu = contextMenu.value
  if (menu?.kind !== 'file' && menu?.kind !== 'directory') return

  const { path, kind } = menu
  workspace.closeContextMenu()
  await workspace.renameEntry(path, kind)
}

async function onDelete(): Promise<void> {
  const menu = contextMenu.value
  if (menu?.kind !== 'file' && menu?.kind !== 'directory') return

  const { path, kind } = menu
  workspace.closeContextMenu()
  await workspace.deleteEntry(path, kind)
}

function onDocumentClick(event: MouseEvent): void {
  if ((event.target as HTMLElement).closest('.file-tree-menu')) return
  workspace.closeContextMenu()
}

function onDocumentKeyDown(event: KeyboardEvent): void {
  if (event.key === 'Escape') workspace.closeContextMenu()
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
  <div
    v-if="contextMenu"
    class="file-tree-menu"
    :style="{ left: `${contextMenu.x}px`, top: `${contextMenu.y}px` }"
    role="menu"
    @click.stop
  >
    <template v-if="contextMenu.kind === 'file'">
      <button type="button" role="menuitem" @click="void onOpen()">打开</button>
      <div class="file-tree-menu__sep" aria-hidden="true" />
      <button type="button" role="menuitem" @click="void onRename()">重命名</button>
      <button type="button" role="menuitem" class="file-tree-menu__danger" @click="void onDelete()">
        删除
      </button>
    </template>

    <template v-else>
      <button type="button" role="menuitem" @click="void onCreateFile()">新建文件</button>
      <button type="button" role="menuitem" @click="void onCreateFolder()">新建文件夹</button>
      <button type="button" role="menuitem" @click="onRefresh()">刷新</button>
      <template v-if="contextMenu.kind === 'directory'">
        <div class="file-tree-menu__sep" aria-hidden="true" />
        <button type="button" role="menuitem" @click="void onRename()">重命名</button>
        <button type="button" role="menuitem" class="file-tree-menu__danger" @click="void onDelete()">
          删除
        </button>
      </template>
    </template>
  </div>
</template>

<style scoped>
.file-tree-menu {
  position: fixed;
  z-index: 200;
  min-width: 168px;
  padding: 4px;
  border: 1px solid var(--ide-border);
  border-radius: 6px;
  background: var(--ide-dropdown-bg, #252526);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.45);
}

.file-tree-menu button {
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

.file-tree-menu button:hover {
  background: var(--ide-accent);
  color: #fff;
}

.file-tree-menu button.file-tree-menu__danger {
  color: var(--ide-error);
}

.file-tree-menu button.file-tree-menu__danger:hover {
  background: color-mix(in srgb, var(--ide-error) 18%, var(--ide-dropdown-bg));
  color: var(--ide-error);
}

.file-tree-menu__sep {
  height: 1px;
  margin: 4px 0;
  background: var(--ide-border);
}
</style>
