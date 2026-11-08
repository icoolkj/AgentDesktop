<script setup lang="ts">
import { onMounted, onUnmounted, watch } from 'vue'
import { attachmentLightbox, closeAttachmentPreview } from '@renderer/utils/attachmentLightbox'

const { open, title, imageUrl } = attachmentLightbox

function onKeyDown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && open.value) {
    closeAttachmentPreview()
  }
}

watch(open, isOpen => {
  document.body.style.overflow = isOpen ? 'hidden' : ''
})

onMounted(() => {
  window.addEventListener('keydown', onKeyDown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeyDown)
  document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="attach-lightbox" @click.self="closeAttachmentPreview">
      <div class="attach-lightbox__panel" role="dialog" aria-modal="true" :aria-label="title">
        <header class="attach-lightbox__header">
          <span class="attach-lightbox__title">{{ title }}</span>
          <button type="button" class="attach-lightbox__close" title="关闭 (Esc)" @click="closeAttachmentPreview">
            ✕
          </button>
        </header>
        <div class="attach-lightbox__body">
          <img :src="imageUrl" :alt="title" class="attach-lightbox__img" />
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.attach-lightbox {
  position: fixed;
  inset: 0;
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: color-mix(in srgb, var(--ide-shadow) 55%, transparent);
  backdrop-filter: blur(4px);
}

.attach-lightbox__panel {
  display: flex;
  flex-direction: column;
  width: min(96vw, 960px);
  max-height: 92vh;
  border-radius: 12px;
  border: 1px solid var(--ide-border);
  background: var(--ide-surface-bg);
  overflow: hidden;
}

.attach-lightbox__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--ide-border);
}

.attach-lightbox__title {
  font-size: 0.82rem;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attach-lightbox__close {
  border: none;
  background: transparent;
  color: var(--ide-muted);
  cursor: pointer;
  font-size: 1rem;
  line-height: 1;
  padding: 4px;
}

.attach-lightbox__body {
  flex: 1;
  overflow: auto;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: var(--ide-code-bg);
}

.attach-lightbox__img {
  max-width: 100%;
  max-height: 75vh;
  object-fit: contain;
  border-radius: 6px;
}
</style>
