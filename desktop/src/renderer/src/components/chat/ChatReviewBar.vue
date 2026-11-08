<script setup lang="ts">
import { computed, ref } from 'vue'
import { storeToRefs } from 'pinia'
import {
  reviewBarHint,
  reviewBarTitle,
} from '@renderer/utils/permissionModePolicy'
import type { PermissionMode } from '@agent-desktop/shared-types'
import { useSessionChangesStore } from '@renderer/stores/sessionChangesStore'

const props = withDefaults(
  defineProps<{
    permissionMode: PermissionMode
  }>(),
  { permissionMode: 'ask' },
)

const changesStore = useSessionChangesStore()
const { pendingChanges, hasPendingChanges } = storeToRefs(changesStore)

const expanded = ref(true)
const busy = ref(false)
const activeReviewPath = ref<string | null>(null)

const totalAdded = computed(() => pendingChanges.value.reduce((s, c) => s + c.added, 0))
const totalRemoved = computed(() => pendingChanges.value.reduce((s, c) => s + c.removed, 0))

const reviewTitle = computed(() =>
  reviewBarTitle(props.permissionMode, pendingChanges.value.length),
)

const reviewHint = computed(() => reviewBarHint(props.permissionMode))

async function run(action: () => void | Promise<void>) {
  if (busy.value) return
  busy.value = true
  try {
    await action()
  } finally {
    busy.value = false
  }
}

function onRejectAll() {
  void run(async () => {
    await changesStore.rejectAll()
    activeReviewPath.value = null
  })
}

function onAcceptAll() {
  void run(async () => {
    await changesStore.acceptAll()
    activeReviewPath.value = null
  })
}

function onOpenReview(path?: string) {
  const target = path ?? pendingChanges.value[0]?.path
  if (!target) return
  activeReviewPath.value = target
  expanded.value = true
  void changesStore.openPendingReview(target)
}

function onAcceptOne(path: string) {
  void run(async () => {
    await changesStore.acceptChange(path)
    if (activeReviewPath.value === path) {
      activeReviewPath.value = pendingChanges.value[0]?.path ?? null
    }
  })
}

function onRejectOne(path: string) {
  void run(async () => {
    await changesStore.rejectChange(path)
    if (activeReviewPath.value === path) {
      activeReviewPath.value = pendingChanges.value[0]?.path ?? null
    }
  })
}
</script>

<template>
  <div v-if="hasPendingChanges" class="review-bar">
    <div class="review-bar__main">
      <button type="button" class="review-bar__toggle" @click="expanded = !expanded">
        <span class="review-bar__chevron" :class="{ open: expanded }">›</span>
        <span class="review-bar__text">
          <strong>{{ reviewTitle }}</strong>
          <span class="review-bar__hint">{{ reviewHint }}</span>
        </span>
        <span class="review-bar__stats">
          <span class="stat stat--add">+{{ totalAdded }}</span>
          <span class="stat stat--del">−{{ totalRemoved }}</span>
        </span>
      </button>

      <div class="review-bar__actions">
        <button
          type="button"
          class="review-bar__btn review-bar__btn--ghost"
          data-testid="review-reject"
          :disabled="busy"
          title="撤销全部 Agent 改动"
          @click="onRejectAll"
        >
          全部拒绝
        </button>
        <button
          type="button"
          class="review-bar__btn"
          data-testid="review-open"
          :disabled="busy"
          title="在编辑器中查看 diff"
          @click="onOpenReview()"
        >
          查看 Diff
        </button>
        <button
          type="button"
          class="review-bar__btn review-bar__btn--primary"
          data-testid="review-accept"
          :disabled="busy"
          title="保留全部改动"
          @click="onAcceptAll"
        >
          全部接受
        </button>
      </div>
    </div>

    <ul v-if="expanded" class="review-bar__list">
      <li v-for="change in pendingChanges" :key="change.path" class="review-bar__item">
        <button type="button" class="review-bar__file" @click="onOpenReview(change.path)">
          <span class="review-bar__file-kind">{{
            change.changeKind === 'created' ? 'A' : change.changeKind === 'deleted' ? 'D' : 'M'
          }}</span>
          <span class="review-bar__file-path">{{ change.relativePath }}</span>
          <span class="review-bar__file-stats">
            <span v-if="change.added" class="stat stat--add">+{{ change.added }}</span>
            <span v-if="change.removed" class="stat stat--del">−{{ change.removed }}</span>
          </span>
        </button>
        <div class="review-bar__item-actions">
          <button
            type="button"
            class="review-bar__mini review-bar__mini--accept"
            :disabled="busy"
            @click="onAcceptOne(change.path)"
          >
            接受
          </button>
          <button
            type="button"
            class="review-bar__mini review-bar__mini--reject"
            :disabled="busy"
            @click="onRejectOne(change.path)"
          >
            拒绝
          </button>
        </div>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.review-bar {
  margin-bottom: 8px;
  border-radius: 10px;
  border: 1px solid color-mix(in srgb, var(--ide-success) 32%, var(--ide-border));
  background: color-mix(in srgb, var(--ide-success) 7%, var(--ide-surface-bg));
  overflow: hidden;
}

.review-bar__main {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
}

.review-bar__toggle {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
  padding: 0;
}

.review-bar__chevron {
  flex-shrink: 0;
  color: var(--ide-success);
  transition: transform 0.12s ease;
}

.review-bar__chevron.open {
  transform: rotate(90deg);
}

.review-bar__text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.review-bar__text strong {
  font-size: 0.76rem;
  color: var(--ide-fg);
}

.review-bar__hint {
  font-size: 0.62rem;
  color: var(--ide-muted);
}

.review-bar__stats {
  flex-shrink: 0;
  display: flex;
  gap: 6px;
  font-family: ui-monospace, monospace;
  font-size: 0.66rem;
}

.stat--add {
  color: var(--ide-success);
}

.stat--del {
  color: var(--ide-error);
}

.review-bar__actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 6px;
}

.review-bar__btn {
  padding: 5px 10px;
  border-radius: 6px;
  border: 1px solid var(--ide-border);
  background: var(--ide-surface-bg);
  color: var(--ide-fg);
  font-size: 0.68rem;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
}

.review-bar__btn:hover:not(:disabled) {
  background: var(--ide-hover-overlay);
}

.review-bar__btn--ghost {
  color: var(--ide-error);
  border-color: color-mix(in srgb, var(--ide-error) 35%, var(--ide-border));
}

.review-bar__btn--primary {
  background: var(--ide-button-primary-bg);
  border-color: var(--ide-button-primary-bg);
  color: var(--ide-button-primary-fg);
}

.review-bar__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.review-bar__list {
  margin: 0;
  padding: 0 8px 8px;
  list-style: none;
  border-top: 1px solid color-mix(in srgb, var(--ide-success) 22%, var(--ide-border));
  max-height: 160px;
  overflow: auto;
}

.review-bar__item {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
}

.review-bar__file {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 6px;
  border: none;
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
  text-align: left;
  font-family: ui-monospace, monospace;
  font-size: 0.66rem;
  color: var(--ide-fg);
}

.review-bar__file:hover {
  background: var(--ide-hover-overlay);
}

.review-bar__file-kind {
  width: 14px;
  flex-shrink: 0;
  font-weight: 700;
  color: var(--ide-accent);
}

.review-bar__file-path {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.review-bar__file-stats {
  flex-shrink: 0;
  display: flex;
  gap: 4px;
}

.review-bar__item-actions {
  flex-shrink: 0;
  display: flex;
  gap: 4px;
}

.review-bar__mini {
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px solid var(--ide-border);
  background: transparent;
  font-size: 0.6rem;
  cursor: pointer;
}

.review-bar__mini--accept {
  color: var(--ide-success);
}

.review-bar__mini--reject {
  color: var(--ide-error);
}

.review-bar__mini:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
