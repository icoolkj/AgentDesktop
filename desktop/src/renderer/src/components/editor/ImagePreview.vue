<script setup lang="ts">
defineProps<{
  src: string
  alt: string
  fileSize?: number
}>()
</script>

<template>
  <div class="image-preview">
    <div class="image-preview__toolbar">
      <span v-if="fileSize != null" class="image-preview__meta">Image preview</span>
    </div>
    <div class="image-preview__body">
      <img :src="src" :alt="alt" class="image-preview__img" />
    </div>
  </div>
</template>

<style scoped>
.image-preview {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--ide-editor-bg, #1e1e1e);
}

.image-preview__toolbar {
  padding: 6px 12px;
  border-bottom: 1px solid var(--ide-border, #3c3c3c);
  color: var(--ide-muted, #969696);
  font-size: 12px;
}

.image-preview__body {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  min-height: 0;
  padding: 16px;
  overflow: auto;
  background:
    linear-gradient(45deg, #2a2a2a 25%, transparent 25%) 0 0 / 16px 16px,
    linear-gradient(-45deg, #2a2a2a 25%, transparent 25%) 0 8px / 16px 16px,
    linear-gradient(45deg, transparent 75%, #2a2a2a 75%) 8px -8px / 16px 16px,
    linear-gradient(-45deg, transparent 75%, #2a2a2a 75%) 8px 0 / 16px 16px,
    #1e1e1e;
}

.image-preview__img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  border-radius: 4px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.35);
}
</style>
