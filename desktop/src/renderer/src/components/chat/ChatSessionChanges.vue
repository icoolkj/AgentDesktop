<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ChangeListItem, ChangeTreeNode } from '@renderer/stores/sessionChangesStore'
import { useSessionChangesStore } from '@renderer/stores/sessionChangesStore'
import ChatChangeTreeNode from './ChatChangeTreeNode.vue'

const props = withDefaults(
  defineProps<{
    changes: ChangeListItem[]
    tree?: ChangeTreeNode[]
    defaultExpanded?: boolean
    showActions?: boolean
    /** Batch accept/reject — use ChatReviewBar in composer instead. */
    showBatchActions?: boolean
  }>(),
  { defaultExpanded: true, showActions: true, showBatchActions: false },
)

const changesStore = useSessionChangesStore()
const expanded = ref(props.defaultExpanded)
const expandedFile = ref<string | null>(null)
const busy = ref(false)

watch(
  () => props.defaultExpanded,
  value => {
    if (value) expanded.value = true
  },
)

const totalAdded = computed(() => props.changes.reduce((s, c) => s + c.added, 0))
const totalRemoved = computed(() => props.changes.reduce((s, c) => s + c.removed, 0))

const displayTree = computed((): ChangeTreeNode[] => {
  if (props.tree?.length) return props.tree
  return props.changes.map(c => ({
    kind: 'file' as const,
    name: c.relativePath.split(/[/\\]/).pop() ?? c.relativePath,
    path: c.path,
    changeKind: c.changeKind,
    added: c.added,
    removed: c.removed,
  }))
})

function togglePanel() {
  expanded.value = !expanded.value
}

function onToggleDiff(path: string) {
  expandedFile.value = expandedFile.value === path ? null : path
}

function findChange(path: string): ChangeListItem | undefined {
  return props.changes.find(c => c.path.toLowerCase() === path.toLowerCase())
}

async function runReview(action: () => void | Promise<void>) {
  if (busy.value) return
  busy.value = true
  try {
    await action()
  } finally {
    busy.value = false
  }
}

function onAccept(path: string) {
  void runReview(async () => {
    await changesStore.acceptChange(path)
  })
}

function onReject(path: string) {
  void runReview(async () => {
    await changesStore.rejectChange(path)
  })
}

function onAcceptAll() {
  void runReview(async () => {
    await changesStore.acceptAll()
  })
}

function onRejectAll() {
  void runReview(async () => {
    await changesStore.rejectAll()
  })
}
</script>

<template>
  <div v-if="changes.length" class="changes-panel">
    <button type="button" class="changes-panel__header" @click="togglePanel">
      <span class="changes-panel__chevron" :class="{ open: expanded }">›</span>
      <span class="changes-panel__title">本次修改 · {{ changes.length }} 个文件</span>
      <span v-if="showActions && !showBatchActions" class="changes-panel__foot-hint">确认见输入框上方</span>
      <span class="changes-panel__stats">
        <span class="stat stat--add">+{{ totalAdded }}</span>
        <span class="stat stat--del">−{{ totalRemoved }}</span>
      </span>
    </button>

    <div v-if="showActions && showBatchActions" class="changes-panel__toolbar">
      <button type="button" class="changes-panel__action changes-panel__action--accept" :disabled="busy" @click="onAcceptAll">
        全部接受
      </button>
      <button type="button" class="changes-panel__action changes-panel__action--reject" :disabled="busy" @click="onRejectAll">
        全部拒绝
      </button>
    </div>

    <div v-if="expanded" class="changes-panel__body">
      <ChatChangeTreeNode
        v-for="node in displayTree"
        :key="node.kind === 'file' ? node.path : node.path || node.name"
        :node="node"
        :depth="0"
        :expanded-file="expandedFile"
        :find-change="findChange"
        :show-actions="showActions"
        :busy="busy"
        @toggle-diff="onToggleDiff"
        @accept="onAccept"
        @reject="onReject"
      />
    </div>
  </div>
</template>

<style scoped>
.changes-panel {
  align-self: stretch;
  border-radius: 8px;
  background: color-mix(in srgb, var(--ide-success) 10%, var(--ide-surface-bg));
  border: 1px solid color-mix(in srgb, var(--ide-success) 28%, var(--ide-border));
  overflow: hidden;
  margin-bottom: 8px;
}

.changes-panel__header {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border: none;
  background: transparent;
  color: var(--ide-muted);
  font-size: 0.75rem;
  cursor: pointer;
  text-align: left;
}

.changes-panel__header:hover {
  background: var(--ide-hover-overlay);
}

.changes-panel__chevron {
  display: inline-block;
  transition: transform 0.15s ease;
  color: var(--ide-success);
}

.changes-panel__chevron.open {
  transform: rotate(90deg);
}

.changes-panel__title {
  color: var(--ide-fg);
  font-weight: 600;
  flex-shrink: 0;
}

.changes-panel__foot-hint {
  font-size: 0.6rem;
  color: var(--ide-muted);
  margin-left: 6px;
}

.changes-panel__stats {
  margin-left: auto;
  display: flex;
  gap: 6px;
  font-family: ui-monospace, monospace;
  font-size: 0.68rem;
}

.stat--add {
  color: var(--ide-success);
}

.stat--del {
  color: var(--ide-error);
}

.changes-panel__toolbar {
  display: flex;
  gap: 8px;
  padding: 0 10px 8px;
}

.changes-panel__action {
  padding: 3px 10px;
  border-radius: 5px;
  border: 1px solid var(--ide-border);
  background: transparent;
  font-size: 0.68rem;
  cursor: pointer;
}

.changes-panel__action--accept {
  color: var(--ide-success);
  border-color: color-mix(in srgb, var(--ide-success) 40%, var(--ide-border));
}

.changes-panel__action--reject {
  color: var(--ide-error);
  border-color: color-mix(in srgb, var(--ide-error) 40%, var(--ide-border));
}

.changes-panel__action:disabled {
  opacity: 0.5;
  cursor: default;
}

.changes-panel__body {
  border-top: 1px solid color-mix(in srgb, var(--ide-success) 28%, var(--ide-border));
  padding: 4px 0 8px;
  max-height: 280px;
  overflow: auto;
}
</style>
