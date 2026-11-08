<script setup lang="ts">
import { ACTIVITY_ICONS, type ActivityIconId } from '@renderer/utils/activityIcons'

defineProps<{
  icon: ActivityIconId
}>()

const meta = (id: ActivityIconId) => ACTIVITY_ICONS[id]
</script>

<template>
  <svg
    class="activity-bar-icon"
    viewBox="0 0 16 16"
    width="24"
    height="24"
    aria-hidden="true"
  >
    <template v-for="(el, i) in meta(icon).elements" :key="i">
      <path
        v-if="el.type === 'path'"
        fill="currentColor"
        :fill-rule="el.fillRule ?? 'nonzero'"
        :d="el.d"
      />
      <path
        v-else-if="el.type === 'stroke'"
        fill="none"
        stroke="currentColor"
        :stroke-width="el.strokeWidth ?? 1.35"
        stroke-linecap="round"
        stroke-linejoin="round"
        :d="el.d"
      />
      <circle v-else :cx="el.cx" :cy="el.cy" :r="el.r" fill="currentColor" />
    </template>
  </svg>
</template>

<style scoped>
.activity-bar-icon {
  display: block;
  flex-shrink: 0;
  shape-rendering: geometricPrecision;
}
</style>
