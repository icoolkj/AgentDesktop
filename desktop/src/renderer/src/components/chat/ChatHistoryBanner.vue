<script setup lang="ts">
defineProps<{
  count: number
  expanded: boolean
}>()

defineEmits<{
  toggle: []
}>()
</script>

<template>
  <button type="button" class="history-banner" @click="$emit('toggle')">
    <span class="history-banner__chevron" :class="{ 'history-banner__chevron--open': expanded }">›</span>
    <span class="history-banner__label">更早的对话</span>
    <span class="history-banner__hint">{{ expanded ? '点击收起 · 每页 5 条' : '点击展开 · 每页 5 条' }}</span>
    <span class="history-banner__count">{{ count }} 轮</span>
  </button>
</template>

<style scoped>
.history-banner {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 6px 10px;
  border: 1px dashed color-mix(in srgb, var(--ide-border) 80%, transparent);
  border-radius: 8px;
  background: transparent;
  color: var(--ide-muted);
  font-size: 0.72rem;
  cursor: pointer;
  text-align: left;
}

.history-banner:hover {
  background: var(--ide-hover-overlay);
  color: var(--ide-fg);
  border-color: var(--ide-border);
}

.history-banner__chevron {
  display: inline-block;
  transition: transform 0.15s ease;
  font-size: 0.85rem;
}

.history-banner__chevron--open {
  transform: rotate(90deg);
}

.history-banner__label {
  flex: 1;
}

.history-banner__hint {
  font-size: 0.62rem;
  color: var(--ide-welcome-fg);
}

.history-banner__count {
  font-size: 0.65rem;
  padding: 1px 7px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--ide-border) 60%, transparent);
}
</style>
