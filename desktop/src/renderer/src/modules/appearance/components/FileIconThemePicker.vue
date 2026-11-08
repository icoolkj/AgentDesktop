<script setup lang="ts">
import { storeToRefs } from 'pinia'

import { FILE_ICON_THEMES } from '@renderer/modules/appearance/colorThemes'
import { useAppearanceStore } from '@renderer/modules/appearance/store'
import type { FileIconThemeId } from '@renderer/modules/appearance/types'

const emit = defineEmits<{
  select: [id: FileIconThemeId]
}>()

const appearance = useAppearanceStore()
const { fileIconThemeId } = storeToRefs(appearance)

function pick(id: FileIconThemeId) {
  appearance.setFileIconTheme(id)
  emit('select', id)
}
</script>

<template>
  <div class="icon-theme-picker">
    <button
      v-for="theme in FILE_ICON_THEMES"
      :key="theme.id"
      type="button"
      class="icon-theme-picker__item"
      :class="{ active: fileIconThemeId === theme.id }"
      @click="pick(theme.id)"
    >
      <span class="icon-theme-picker__label">{{ theme.label }}</span>
      <span class="icon-theme-picker__desc">{{ theme.description }}</span>
    </button>
  </div>
</template>

<style scoped>
.icon-theme-picker {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 240px;
}

.icon-theme-picker__item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  width: 100%;
  padding: 8px 10px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--ide-fg);
  text-align: left;
  cursor: pointer;
}

.icon-theme-picker__item:hover {
  background: var(--ide-hover-overlay);
}

.icon-theme-picker__item.active {
  background: var(--ide-list-active-bg);
  color: var(--ide-list-active-fg);
}

.icon-theme-picker__label {
  font-size: 13px;
}

.icon-theme-picker__desc {
  font-size: 11px;
  opacity: 0.75;
}
</style>
