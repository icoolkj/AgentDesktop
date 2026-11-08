<script setup lang="ts">
defineProps<{
  title: string
  /** Optional actions slot on the right */
}>()

defineEmits<{
  collapse: []
}>()
</script>

<template>
  <header class="panel-header">
    <span class="panel-header__title">{{ title }}</span>
    <div class="panel-header__actions">
      <slot />
      <button
        type="button"
        class="panel-header__btn"
        title="收起面板"
        aria-label="收起面板"
        @click="$emit('collapse')"
      >
        <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
          <path fill="currentColor" d="M5 4l6 4-6 4V4z" />
        </svg>
      </button>
    </div>
  </header>
</template>

<style scoped>
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  height: var(--ide-panel-header-height, 35px);
  padding: 0 8px 0 12px;
  border-bottom: 1px solid var(--ide-border);
  background: var(--ide-sidebar-bg);
  flex-shrink: 0;
  user-select: none;
}

.panel-header__title {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--ide-muted);
  white-space: nowrap;
}

.panel-header__actions {
  display: flex;
  align-items: center;
  gap: 2px;
  min-width: 0;
  flex: 1;
  justify-content: flex-end;
}

.panel-header__btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--ide-muted);
  cursor: pointer;
}

.panel-header__btn:hover {
  background: var(--ide-hover-overlay);
  color: var(--ide-fg);
}
</style>
