<script setup lang="ts">
import type { ChangeListItem, ChangeTreeNode } from '@renderer/stores/sessionChangesStore'
import { useEditorStore } from '@renderer/stores/editorStore'
import ChatChangeTreeNode from './ChatChangeTreeNode.vue'

const props = defineProps<{
  node: ChangeTreeNode
  depth: number
  expandedFile: string | null
  findChange: (path: string) => ChangeListItem | undefined
  showActions?: boolean
  busy?: boolean
}>()

const emit = defineEmits<{
  toggleDiff: [path: string]
  accept: [path: string]
  reject: [path: string]
}>()

const editor = useEditorStore()

function openFile(path: string) {
  void editor.openFile(path)
}
</script>

<template>
  <template v-if="node.kind === 'dir'">
    <div class="change-dir" :style="{ paddingLeft: `${props.depth * 14 + 8}px` }">
      <div class="change-dir__label">📁 {{ node.name }}</div>
      <ChatChangeTreeNode
        v-for="child in node.children"
        :key="child.kind === 'file' ? child.path : child.path || child.name"
        :node="child"
        :depth="props.depth + 1"
        :expanded-file="props.expandedFile"
        :find-change="props.findChange"
        :show-actions="props.showActions"
        :busy="props.busy"
        @toggle-diff="emit('toggleDiff', $event)"
        @accept="emit('accept', $event)"
        @reject="emit('reject', $event)"
      />
    </div>
  </template>

  <div v-else class="change-file-wrap">
    <div class="change-file-row" :style="{ paddingLeft: `${props.depth * 14 + 8}px` }">
      <button
        type="button"
        class="change-file"
        :class="{ 'change-file--open': props.expandedFile === node.path }"
        @click="emit('toggleDiff', node.path); openFile(node.path)"
      >
        <span class="change-file__icon">{{
          node.changeKind === 'created' ? 'A' : node.changeKind === 'deleted' ? 'D' : 'M'
        }}</span>
        <span class="change-file__name">{{ node.name }}</span>
        <span class="change-file__stats">
          <span v-if="node.added > 0" class="stat stat--add">+{{ node.added }}</span>
          <span v-if="node.removed > 0" class="stat stat--del">−{{ node.removed }}</span>
        </span>
      </button>
      <div v-if="showActions" class="change-file__actions">
        <button type="button" class="change-file__btn change-file__btn--accept" :disabled="busy" @click.stop="emit('accept', node.path)">
          接受
        </button>
        <button type="button" class="change-file__btn change-file__btn--reject" :disabled="busy" @click.stop="emit('reject', node.path)">
          拒绝
        </button>
      </div>
    </div>
    <pre v-if="props.expandedFile === node.path && props.findChange(node.path)" class="change-file__diff">{{
      props.findChange(node.path)!.diffPreview
    }}</pre>
  </div>
</template>

<style scoped>
.change-dir__label {
  font-size: 0.68rem;
  color: var(--ide-muted);
  padding: 4px 8px 2px;
  font-family: ui-monospace, monospace;
}

.change-file-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-right: 8px;
}

.change-file {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
  padding: 5px 8px;
  border: none;
  background: transparent;
  color: var(--ide-fg);
  font-size: 0.72rem;
  cursor: pointer;
  text-align: left;
}

.change-file:hover,
.change-file--open {
  background: var(--ide-hover-overlay);
  border-radius: 4px;
}

.change-file__icon {
  width: 16px;
  height: 16px;
  border-radius: 3px;
  background: color-mix(in srgb, var(--ide-success) 22%, var(--ide-code-bg));
  color: var(--ide-success);
  font-size: 0.6rem;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.change-file__name {
  font-family: ui-monospace, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.change-file__stats {
  margin-left: auto;
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.change-file__actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.change-file__btn {
  padding: 2px 8px;
  border-radius: 4px;
  border: 1px solid var(--ide-border);
  background: transparent;
  font-size: 0.62rem;
  cursor: pointer;
}

.change-file__btn--accept {
  color: var(--ide-success);
}

.change-file__btn--reject {
  color: var(--ide-error);
}

.change-file__btn:disabled {
  opacity: 0.45;
  cursor: default;
}

.stat--add {
  color: var(--ide-success);
  font-family: ui-monospace, monospace;
  font-size: 0.65rem;
}

.stat--del {
  color: var(--ide-error);
  font-family: ui-monospace, monospace;
  font-size: 0.65rem;
}

.change-file__diff {
  margin: 0 10px 6px 32px;
  padding: 8px;
  max-height: 180px;
  overflow: auto;
  border-radius: 6px;
  background: var(--ide-code-bg);
  border: 1px solid var(--ide-bubble-border);
  font-size: 0.65rem;
  color: var(--ide-fg);
  white-space: pre-wrap;
  line-height: 1.45;
}
</style>
