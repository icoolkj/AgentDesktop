<script setup lang="ts">
import { formatFileSize } from '@renderer/utils/fileKind'

defineProps<{
  name: string
  path: string
  mimeType?: string
  fileSize?: number
}>()

const emit = defineEmits<{
  openInSystem: []
}>()
</script>

<template>
  <div class="binary-file-view">
    <div class="binary-file-view__card">
      <div class="binary-file-view__icon" aria-hidden="true">📦</div>
      <h3 class="binary-file-view__name">{{ name }}</h3>
      <p class="binary-file-view__hint">This file type cannot be previewed in the editor.</p>
      <dl class="binary-file-view__meta">
        <div v-if="mimeType">
          <dt>Type</dt>
          <dd>{{ mimeType }}</dd>
        </div>
        <div v-if="fileSize != null">
          <dt>Size</dt>
          <dd>{{ formatFileSize(fileSize) }}</dd>
        </div>
        <div>
          <dt>Path</dt>
          <dd class="binary-file-view__path">{{ path }}</dd>
        </div>
      </dl>
      <button type="button" class="binary-file-view__action" @click="emit('openInSystem')">
        Open with system app
      </button>
    </div>
  </div>
</template>

<style scoped>
.binary-file-view {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 24px;
  background: var(--ide-editor-bg, #1e1e1e);
}

.binary-file-view__card {
  width: min(480px, 100%);
  padding: 28px 24px;
  text-align: center;
  border: 1px solid var(--ide-border, #3c3c3c);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.02);
}

.binary-file-view__icon {
  font-size: 40px;
  margin-bottom: 12px;
}

.binary-file-view__name {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--ide-fg, #cccccc);
  word-break: break-all;
}

.binary-file-view__hint {
  margin: 0 0 20px;
  color: var(--ide-muted, #969696);
  font-size: 13px;
}

.binary-file-view__meta {
  margin: 0 0 20px;
  text-align: left;
  font-size: 12px;
}

.binary-file-view__meta div {
  display: grid;
  grid-template-columns: 72px 1fr;
  gap: 8px;
  padding: 6px 0;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.binary-file-view__meta dt {
  color: var(--ide-muted, #969696);
}

.binary-file-view__meta dd {
  margin: 0;
  color: var(--ide-fg, #cccccc);
  word-break: break-all;
}

.binary-file-view__action {
  padding: 8px 16px;
  border: 1px solid var(--ide-accent, #3794ff);
  border-radius: 6px;
  background: rgba(55, 148, 255, 0.12);
  color: var(--ide-accent, #3794ff);
  font-size: 13px;
  cursor: pointer;
}

.binary-file-view__action:hover {
  background: rgba(55, 148, 255, 0.22);
}
</style>
