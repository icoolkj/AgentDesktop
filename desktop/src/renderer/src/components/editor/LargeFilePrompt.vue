<script setup lang="ts">
import { computed } from 'vue'

import { formatFileSize } from '@renderer/utils/fileKind'
import { textLoadPolicyMessage, type TextLoadPolicy } from '@renderer/utils/fileSizeLimits'

const props = defineProps<{
  name: string
  path: string
  fileSize?: number
  textLoadPolicy: TextLoadPolicy
}>()

const emit = defineEmits<{
  openAnyway: []
  openInSystem: []
}>()

const sizeLabel = computed(() => (props.fileSize != null ? formatFileSize(props.fileSize) : 'unknown size'))

const message = computed(() => textLoadPolicyMessage(props.textLoadPolicy, sizeLabel.value))

const canOpenInEditor = computed(() => props.textLoadPolicy === 'confirm-required')
</script>

<template>
  <div class="large-file-prompt">
    <div class="large-file-prompt__card">
      <div class="large-file-prompt__icon" aria-hidden="true">{{ canOpenInEditor ? '⚠️' : '🚫' }}</div>
      <h3 class="large-file-prompt__name">{{ name }}</h3>
      <p class="large-file-prompt__hint">{{ message }}</p>
      <dl class="large-file-prompt__meta">
        <div v-if="fileSize != null">
          <dt>Size</dt>
          <dd>{{ sizeLabel }}</dd>
        </div>
        <div>
          <dt>Path</dt>
          <dd>{{ path }}</dd>
        </div>
      </dl>
      <div class="large-file-prompt__actions">
        <button
          v-if="canOpenInEditor"
          type="button"
          class="large-file-prompt__action large-file-prompt__action--primary"
          @click="emit('openAnyway')"
        >
          Open in editor anyway
        </button>
        <button type="button" class="large-file-prompt__action" @click="emit('openInSystem')">
          Open with system app
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.large-file-prompt {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 24px;
  background: var(--ide-editor-bg, #1e1e1e);
}

.large-file-prompt__card {
  width: min(520px, 100%);
  padding: 28px 24px;
  text-align: center;
  border: 1px solid var(--ide-border, #3c3c3c);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.02);
}

.large-file-prompt__icon {
  font-size: 36px;
  margin-bottom: 12px;
}

.large-file-prompt__name {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--ide-fg, #cccccc);
  word-break: break-all;
}

.large-file-prompt__hint {
  margin: 0 0 20px;
  color: var(--ide-muted, #969696);
  font-size: 13px;
  line-height: 1.5;
}

.large-file-prompt__meta {
  margin: 0 0 20px;
  text-align: left;
  font-size: 12px;
}

.large-file-prompt__meta div {
  display: grid;
  grid-template-columns: 72px 1fr;
  gap: 8px;
  padding: 6px 0;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.large-file-prompt__meta dt {
  color: var(--ide-muted, #969696);
}

.large-file-prompt__meta dd {
  margin: 0;
  color: var(--ide-fg, #cccccc);
  word-break: break-all;
}

.large-file-prompt__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
}

.large-file-prompt__action {
  padding: 8px 16px;
  border: 1px solid var(--ide-border, #3c3c3c);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.04);
  color: var(--ide-fg, #cccccc);
  font-size: 13px;
  cursor: pointer;
}

.large-file-prompt__action:hover {
  background: rgba(255, 255, 255, 0.08);
}

.large-file-prompt__action--primary {
  border-color: var(--ide-accent, #3794ff);
  background: rgba(55, 148, 255, 0.12);
  color: var(--ide-accent, #3794ff);
}

.large-file-prompt__action--primary:hover {
  background: rgba(55, 148, 255, 0.22);
}
</style>
