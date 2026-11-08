<script setup lang="ts">
defineProps<{
  expanded?: boolean
  loading?: boolean
}>()
</script>

<template>
  <span class="tree-chevron" :class="{ 'tree-chevron--open': expanded }">
    <span v-if="loading" class="tree-chevron__loading">…</span>
    <svg v-else viewBox="0 0 16 16" class="tree-chevron__svg" aria-hidden="true">
      <path fill="currentColor" d="M6 4l4 4-4 4V4z" />
    </svg>
  </span>
</template>

<style scoped>
.tree-chevron {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  color: #8b949e;
  transition: transform 0.12s ease;
}

.tree-chevron--open {
  transform: rotate(90deg);
}

.tree-chevron__svg {
  width: 10px;
  height: 10px;
}

.tree-chevron__loading {
  font-size: 0.65rem;
  line-height: 1;
}
</style>
