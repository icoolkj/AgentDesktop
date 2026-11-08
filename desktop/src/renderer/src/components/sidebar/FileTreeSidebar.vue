<script setup lang="ts">
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import FileTreeNode from './FileTreeNode.vue'
import FileTreeChevron from './FileTreeChevron.vue'
import FileTreeIcon from './FileTreeIcon.vue'
import FileTreeContextMenu from './FileTreeContextMenu.vue'
import PanelSectionHeader from '@renderer/components/layout/PanelSectionHeader.vue'
import Codicon from '@renderer/components/common/Codicon.vue'
import { EXPLORER_TOOLBAR_ICONS } from '@renderer/utils/codicons'
import { useWorkspaceStore } from '@renderer/stores/workspaceStore'
import { useEditorStore } from '@renderer/stores/editorStore'
import { useGitStore } from '@renderer/stores/gitStore'
import { useAppearanceStore } from '@renderer/stores/appearanceStore'
import { iconKindForEntry } from '@renderer/utils/fileTreeIcons'
import { fileNameFromPath } from '@renderer/utils/editorLanguage'

defineEmits<{
  collapse: []
}>()

const workspace = useWorkspaceStore()
const editor = useEditorStore()
const gitStore = useGitStore()
const appearance = useAppearanceStore()
const { rootPath, tree, loading, error, rootExpanded } = storeToRefs(workspace)
const { info: gitInfo } = storeToRefs(gitStore)
const { fileIconThemeId } = storeToRefs(appearance)

const rootGitDecoration = computed(() => {
  // 读取 ref 以建立响应式依赖（git 信息刷新时重算装饰）
  // eslint-disable-next-line @typescript-eslint/no-unused-expressions
  gitInfo.value
  return rootPath.value ? gitStore.decorationForPath(rootPath.value, 'directory') : null
})

const rootName = computed(() => (rootPath.value ? fileNameFromPath(rootPath.value) : ''))

const rootIconKind = computed(() =>
  iconKindForEntry(rootName.value || 'project', 'directory', rootExpanded.value),
)

function toggleRoot() {
  workspace.toggleRootExpanded()
}

async function onCreateFile() {
  const filePath = await workspace.createFile()
  if (filePath) await editor.openFile(filePath)
}
</script>

<template>
  <aside class="sidebar">
    <PanelSectionHeader title="资源管理器" @collapse="$emit('collapse')">
      <button
        type="button"
        class="sidebar__action"
        title="新建文件"
        :disabled="loading || !rootPath"
        @click="void onCreateFile()"
      >
        <Codicon :icon="EXPLORER_TOOLBAR_ICONS.newFile" :size="16" />
      </button>
      <button
        type="button"
        class="sidebar__action"
        title="新建文件夹"
        :disabled="loading || !rootPath"
        @click="void workspace.createFolder()"
      >
        <Codicon :icon="EXPLORER_TOOLBAR_ICONS.newFolder" :size="16" />
      </button>
      <button
        type="button"
        class="sidebar__action"
        title="刷新"
        :disabled="loading || !rootPath"
        @click="void workspace.refreshTree()"
      >
        <Codicon :icon="EXPLORER_TOOLBAR_ICONS.refresh" :size="16" />
      </button>
    </PanelSectionHeader>

    <p v-if="error" class="sidebar__error">{{ error }}</p>

    <div v-if="!rootPath" class="sidebar__empty">
      <p>打开文件夹以浏览项目</p>
      <button type="button" class="sidebar__open-btn" :disabled="loading" @click="void workspace.openFolder()">
        打开文件夹
      </button>
    </div>

    <div v-else class="sidebar__tree">
      <div
        class="tree-node tree-node--root"
        :title="rootPath"
        @contextmenu="workspace.openRootContextMenu($event)"
      >
        <button type="button" class="tree-node__chevron-btn" :aria-expanded="rootExpanded" @click="toggleRoot">
          <FileTreeChevron :expanded="rootExpanded" />
        </button>
        <button
          type="button"
          class="tree-node__row is-root"
          :class="rootGitDecoration?.colorClass"
          @click="toggleRoot"
        >
          <FileTreeIcon
            v-if="fileIconThemeId !== 'none'"
            :kind="rootIconKind"
            :variant="fileIconThemeId"
          />
          <span class="tree-node__name tree-node__name--root">{{ rootName }}</span>
          <span
            v-if="rootGitDecoration?.dirDot"
            class="tree-node__dot"
            :class="`tree-node__dot--${rootGitDecoration.dirDot}`"
            title="含未提交变更"
          />
        </button>
      </div>

      <template v-if="rootExpanded">
        <FileTreeNode v-for="node in tree" :key="node.path" :node="node" :depth="0" />
      </template>
    </div>

    <FileTreeContextMenu />
  </aside>
</template>

<style scoped>
.sidebar__action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--ide-fg);
  opacity: 0.72;
  cursor: pointer;
}

.sidebar__action:hover:not(:disabled) {
  background: var(--ide-hover-overlay);
  color: var(--ide-fg);
  opacity: 1;
}

.sidebar__action:disabled {
  opacity: 0.45;
  cursor: default;
}

.sidebar__open-btn {
  margin-top: 8px;
  padding: 6px 14px;
  border-radius: 4px;
  border: none;
  background: var(--ide-accent);
  color: var(--ide-on-accent);
  font-size: 13px;
  cursor: pointer;
}

.sidebar__open-btn:hover:not(:disabled) {
  filter: brightness(1.08);
}

.tree-node--root {
  padding-left: 8px;
}

.tree-node__name--root {
  color: var(--ide-fg);
  font-weight: 600;
}
</style>
