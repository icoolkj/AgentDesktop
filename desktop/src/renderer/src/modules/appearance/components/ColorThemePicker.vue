<script setup lang="ts">
import { storeToRefs } from 'pinia'

import { useAppearanceStore } from '@renderer/modules/appearance/store'
import type { ColorThemeId } from '@renderer/modules/appearance/types'

defineProps<{
  compact?: boolean
}>()

const emit = defineEmits<{
  select: [id: ColorThemeId]
}>()

const appearance = useAppearanceStore()
const { colorThemeId, lightThemes, darkThemes } = storeToRefs(appearance)

function pick(id: ColorThemeId) {
  appearance.setColorTheme(id)
  emit('select', id)
}
</script>

<template>
  <div class="theme-picker" :class="{ compact }">
    <div class="theme-picker__group">
      <div class="theme-picker__label">浅色主题</div>
      <button
        v-for="theme in lightThemes"
        :key="theme.id"
        type="button"
        class="theme-picker__item"
        :class="{ active: colorThemeId === theme.id }"
        :data-testid="`theme-${theme.id}`"
        @click="pick(theme.id)"
      >
        {{ theme.label }}
      </button>
    </div>
    <div class="theme-picker__group">
      <div class="theme-picker__label">深色主题</div>
      <button
        v-for="theme in darkThemes"
        :key="theme.id"
        type="button"
        class="theme-picker__item"
        :class="{ active: colorThemeId === theme.id }"
        :data-testid="`theme-${theme.id}`"
        @click="pick(theme.id)"
      >
        {{ theme.label }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.theme-picker {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 220px;
  max-height: min(420px, 60vh);
  overflow: auto;
}

.theme-picker.compact {
  max-height: none;
}

.theme-picker__group {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.theme-picker__label {
  padding: 4px 10px 2px;
  font-size: 11px;
  text-align: left;
  color: var(--ide-muted);
}

.theme-picker__item {
  display: block;
  width: 100%;
  padding: 6px 10px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--ide-fg);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.theme-picker__item:hover {
  background: var(--ide-hover-overlay);
}

.theme-picker__item.active {
  background: var(--ide-list-active-bg);
  color: var(--ide-list-active-fg);
}
</style>
