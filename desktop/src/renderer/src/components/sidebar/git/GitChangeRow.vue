<script setup lang="ts">
import type { GitChange } from '../../../../../preload/api/workspaceApi'
import {
  badgeForKind,
  decorationKindFromStatus,
} from '@renderer/utils/gitTreeDecorations'
import { changeName, toDisplayPath } from '@renderer/utils/gitSidebarUtils'

const props = defineProps<{
  change: GitChange
  gitRoot: string
  showStage?: boolean
  showUnstage?: boolean
  showDiscard?: boolean
  discardTitle?: string
  compact?: boolean
}>()

const emit = defineEmits<{
  open: [relPath: string]
  stage: [relPath: string]
  unstage: [relPath: string]
  discard: [relPath: string]
}>()

const decoKind = decorationKindFromStatus(props.change.status)
</script>

<template>
  <div class="git-change-row" :class="[`is-git-${decoKind}`, { compact }]">
    <button
      type="button"
      class="git-change-row__open"
      :title="toDisplayPath(change.path, gitRoot)"
      @click="emit('open', change.path)"
    >
      <span class="git-change-row__name">{{ changeName(change.path) }}</span>
      <span v-if="!compact" class="git-change-row__path">{{ toDisplayPath(change.path, gitRoot) }}</span>
    </button>

    <div class="git-change-row__actions">
      <button
        v-if="showStage"
        type="button"
        class="git-change-row__icon"
        title="暂存"
        aria-label="暂存"
        @click.stop="emit('stage', change.path)"
      >
        +
      </button>
      <button
        v-if="showUnstage"
        type="button"
        class="git-change-row__icon"
        title="取消暂存"
        aria-label="取消暂存"
        @click.stop="emit('unstage', change.path)"
      >
        −
      </button>
      <button
        v-if="showDiscard"
        type="button"
        class="git-change-row__icon git-change-row__icon--danger"
        :title="discardTitle ?? '还原更改'"
        aria-label="还原更改"
        @click.stop="emit('discard', change.path)"
      >
        ↶
      </button>
      <span class="tree-node__badge git-change-row__badge" :class="`tree-node__badge--${decoKind}`">
        {{ badgeForKind(decoKind) }}
      </span>
    </div>
  </div>
</template>

<style scoped>
.git-change-row {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  border-radius: 4px;
}

.git-change-row:hover {
  background: rgba(255, 255, 255, 0.05);
}

.git-change-row__open {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: 4px 4px 4px 6px;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
  color: inherit;
}

.git-change-row__name {
  font-size: 12px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.git-change-row__path {
  font-size: 10px;
  color: var(--ide-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.git-change-row__actions {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 2px 4px 2px 0;
  flex-shrink: 0;
}

.git-change-row__icon {
  width: 20px;
  height: 20px;
  padding: 0;
  border: none;
  border-radius: 3px;
  background: transparent;
  color: var(--ide-muted);
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  opacity: 0.35;
}

.git-change-row:hover .git-change-row__icon {
  opacity: 1;
}

.git-change-row__icon:hover {
  background: rgba(255, 255, 255, 0.1);
  color: var(--ide-fg);
}

.git-change-row__icon--danger:hover {
  color: var(--ide-error);
}

.git-change-row__badge {
  min-width: 16px;
  margin-left: 2px;
  text-align: center;
}

.git-change-row.is-git-modified .git-change-row__name,
.git-change-row.is-git-renamed .git-change-row__name {
  color: var(--ide-git-modified);
}

.git-change-row.is-git-untracked .git-change-row__name,
.git-change-row.is-git-added .git-change-row__name {
  color: var(--ide-git-untracked);
}

.git-change-row.is-git-deleted .git-change-row__name {
  color: var(--ide-git-deleted);
  text-decoration: line-through;
}

.git-change-row.compact .git-change-row__open {
  flex-direction: row;
  align-items: center;
  gap: 8px;
}

.git-change-row.compact .git-change-row__path {
  flex: 1;
}
</style>
