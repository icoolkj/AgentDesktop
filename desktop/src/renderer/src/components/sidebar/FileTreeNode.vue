<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import FileTreeNode from './FileTreeNode.vue'
import FileTreeChevron from './FileTreeChevron.vue'
import FileTreeIcon from './FileTreeIcon.vue'
import type { TreeNode } from '@renderer/stores/workspaceStore'
import { useEditorStore } from '@renderer/stores/editorStore'
import { useGitStore } from '@renderer/stores/gitStore'
import { useAppearanceStore } from '@renderer/stores/appearanceStore'
import { useWorkspaceStore } from '@renderer/stores/workspaceStore'
import { useSessionChangesStore } from '@renderer/stores/sessionChangesStore'
import { iconKindForEntry } from '@renderer/utils/fileTreeIcons'

const props = defineProps<{
  node: TreeNode
  depth: number
}>()

const workspace = useWorkspaceStore()
const editor = useEditorStore()
const gitStore = useGitStore()
const appearance = useAppearanceStore()
const sessionChanges = useSessionChangesStore()
const { revealTick, revealTargetPath, selectedPath } = storeToRefs(workspace)
const { info: gitInfo } = storeToRefs(gitStore)
const { fileIconThemeId } = storeToRefs(appearance)

const rowRef = ref<HTMLElement | null>(null)

const isSelected = computed(() => {
  if (!selectedPath.value) return false
  return (
    selectedPath.value.replace(/\\/g, '/').toLowerCase() ===
    props.node.path.replace(/\\/g, '/').toLowerCase()
  )
})

const isAgentFileModified = computed(
  () => props.node.kind === 'file' && sessionChanges.isChanged(props.node.path),
)

const isAgentDirModified = computed(
  () => props.node.kind === 'directory' && sessionChanges.hasChangedUnder(props.node.path),
)

const gitDecoration = computed(() => {
  // 读取 ref 以建立响应式依赖（git 信息刷新时重算装饰）
  // eslint-disable-next-line @typescript-eslint/no-unused-expressions
  gitInfo.value
  return gitStore.decorationForPath(props.node.path, props.node.kind)
})

const rowClasses = computed(() => {
  const classes: Record<string, boolean> = {
    'is-selected': isSelected.value,
    'is-modified': isAgentFileModified.value,
    'is-dir-modified': isAgentDirModified.value,
  }
  if (gitDecoration.value) {
    classes[gitDecoration.value.colorClass] = true
    if (props.node.kind === 'directory' && gitDecoration.value.dirDot) {
      classes[`is-git-dir-${gitDecoration.value.dirDot}`] = true
    }
  }
  return classes
})

const statusBadge = computed(() => gitDecoration.value?.badge ?? null)

const iconKind = computed(() =>
  iconKindForEntry(props.node.name, props.node.kind, props.node.expanded),
)

const indentPx = computed(() => 8 + (props.depth + 1) * 12)

function onClick(node: TreeNode) {
  workspace.selectPath(node.path, node.kind)
  if (node.kind === 'file') {
    void editor.openFile(node.path, { reveal: false })
  }
}

function onToggle(node: TreeNode, event: Event) {
  event.stopPropagation()
  void workspace.toggleExpand(node)
}

watch(revealTick, async () => {
  if (!revealTargetPath.value) return
  const target = revealTargetPath.value.replace(/\\/g, '/').toLowerCase()
  const self = props.node.path.replace(/\\/g, '/').toLowerCase()
  if (target !== self) return
  await nextTick()
  rowRef.value?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
})
</script>

<template>
  <div
    ref="rowRef"
    class="tree-node"
    :data-tree-path="node.path"
    :style="{ paddingLeft: `${indentPx}px` }"
    @contextmenu="workspace.openContextMenu($event, node.path, node.kind)"
  >
    <button
      v-if="node.kind === 'directory'"
      type="button"
      class="tree-node__chevron-btn"
      :aria-expanded="node.expanded"
      @click="onToggle(node, $event)"
    >
      <FileTreeChevron :expanded="node.expanded" :loading="node.loading" />
    </button>
    <span v-else class="tree-node__chevron-spacer" />

    <button type="button" class="tree-node__row" :class="rowClasses" @click="onClick(node)">
      <FileTreeIcon
        v-if="fileIconThemeId !== 'none'"
        :kind="iconKind"
        :variant="fileIconThemeId"
      />
      <span class="tree-node__name">{{ node.name }}</span>
      <span
        v-if="node.kind === 'directory' && gitDecoration?.dirDot"
        class="tree-node__dot"
        :class="`tree-node__dot--${gitDecoration.dirDot}`"
        title="含未提交变更"
      />
      <span v-else-if="isAgentDirModified" class="tree-node__dot tree-node__dot--modified" title="含 Agent 修改" />
      <span v-if="statusBadge" class="tree-node__badge" :class="`tree-node__badge--${gitDecoration?.kind}`">
        {{ statusBadge }}
      </span>
    </button>
  </div>

  <template v-if="node.kind === 'directory' && node.expanded && node.children">
    <FileTreeNode
      v-for="child in node.children"
      :key="child.path"
      :node="child"
      :depth="depth + 1"
    />
  </template>
</template>
