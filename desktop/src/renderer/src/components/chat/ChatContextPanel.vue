<script setup lang="ts">
import { computed } from 'vue'
import type { ContextBreakdown, ContextBreakdownCategory } from '@agent-desktop/shared-types'

const props = defineProps<{
  breakdown: ContextBreakdown | null
  expanded?: boolean
  compact?: boolean
}>()

defineEmits<{
  toggle: []
  close: []
}>()

function formatChars(chars: number): string {
  if (chars >= 1_000_000) return `${(chars / 1_000_000).toFixed(1)}M`
  if (chars >= 1000) return `${(chars / 1000).toFixed(1)}K`
  return String(chars)
}

const visibleCategories = computed(() =>
  (props.breakdown?.categories ?? []).filter(c => c.id !== 'free' && c.chars > 0),
)

const barCategories = computed(() =>
  visibleCategories.value.filter(c => c.id !== 'buffer'),
)

function segmentWidth(cat: ContextBreakdownCategory): string {
  const max = props.breakdown?.maxChars ?? 1
  const pct = Math.max(0, Math.min(100, (cat.chars / max) * 100))
  return `${pct}%`
}

function categoryPercent(cat: ContextBreakdownCategory): number {
  const max = props.breakdown?.maxChars ?? 1
  return max > 0 ? Math.round((cat.chars / max) * 100) : 0
}
</script>

<template>
  <div v-if="breakdown" class="ctx-panel" :class="{ 'ctx-panel--compact': compact }">
    <header class="ctx-panel__header">
      <button type="button" class="ctx-panel__title-btn" @click="$emit('toggle')">
        <span class="ctx-panel__title">Context</span>
        <span class="ctx-panel__badge">{{ breakdown.percent }}% Full</span>
      </button>
      <span class="ctx-panel__tokens">
        ~{{ formatChars(breakdown.totalChars) }} / {{ formatChars(breakdown.maxChars) }}
      </span>
      <button v-if="!compact" type="button" class="ctx-panel__close" title="关闭" @click="$emit('close')">
        ✕
      </button>
    </header>

    <div v-if="expanded !== false" class="ctx-panel__body">
      <div class="ctx-panel__bar" role="img" :aria-label="`Context ${breakdown.percent}% used`">
        <span
          v-for="cat in barCategories"
          :key="cat.id"
          class="ctx-panel__seg"
          :style="{ width: segmentWidth(cat), backgroundColor: cat.color }"
          :title="`${cat.label}: ${formatChars(cat.chars)}`"
        />
      </div>

      <ul class="ctx-panel__legend">
        <li v-for="cat in visibleCategories" :key="cat.id" class="ctx-panel__row">
          <span class="ctx-panel__dot" :style="{ backgroundColor: cat.color }" />
          <span class="ctx-panel__label">{{ cat.label }}</span>
          <span class="ctx-panel__value">{{ formatChars(cat.chars) }}</span>
          <span class="ctx-panel__pct">{{ categoryPercent(cat) }}%</span>
        </li>
      </ul>

      <p v-if="breakdown.model" class="ctx-panel__model">{{ breakdown.model }}</p>
    </div>
  </div>
</template>

<style scoped>
.ctx-panel {
  border-radius: 10px;
  border: 1px solid var(--ide-border);
  background: var(--ide-surface-bg);
  overflow: hidden;
  margin-bottom: 8px;
}

.ctx-panel--compact .ctx-panel__body {
  padding-top: 6px;
  padding-bottom: 8px;
}

.ctx-panel__header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-bottom: 1px solid var(--ide-border);
}

.ctx-panel__title-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0;
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font: inherit;
}

.ctx-panel__title {
  font-size: 0.78rem;
  font-weight: 600;
}

.ctx-panel__badge {
  font-size: 0.65rem;
  color: var(--ide-muted);
}

.ctx-panel__tokens {
  margin-left: auto;
  font-size: 0.65rem;
  font-family: ui-monospace, monospace;
  color: var(--ide-welcome-fg);
}

.ctx-panel__close {
  border: none;
  background: transparent;
  color: var(--ide-muted);
  cursor: pointer;
  font-size: 0.85rem;
  line-height: 1;
  padding: 2px 4px;
}

.ctx-panel__body {
  padding: 10px 12px 12px;
}

.ctx-panel__bar {
  display: flex;
  height: 8px;
  border-radius: 4px;
  overflow: hidden;
  background: color-mix(in srgb, var(--ide-border) 60%, transparent);
  margin-bottom: 10px;
}

.ctx-panel__seg {
  height: 100%;
  min-width: 2px;
  transition: width 0.2s ease;
}

.ctx-panel__legend {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ctx-panel__row {
  display: grid;
  grid-template-columns: 10px 1fr auto auto;
  gap: 8px;
  align-items: center;
  font-size: 0.68rem;
  color: var(--ide-fg);
}

.ctx-panel__dot {
  width: 8px;
  height: 8px;
  border-radius: 2px;
  flex-shrink: 0;
}

.ctx-panel__label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ctx-panel__value {
  font-family: ui-monospace, monospace;
  color: var(--ide-muted);
}

.ctx-panel__pct {
  width: 32px;
  text-align: right;
  color: var(--ide-welcome-fg);
}

.ctx-panel__model {
  margin: 8px 0 0;
  font-size: 0.62rem;
  color: var(--ide-welcome-fg);
}
</style>
