<script setup lang="ts">
import type { CodiconDefinition, CodiconElement } from '@renderer/utils/codicons'

withDefaults(
  defineProps<{
    icon: CodiconDefinition
    size?: number
  }>(),
  { size: 16 },
)

function strokeWidth(el: CodiconElement): number {
  return el.type === 'stroke' ? (el.strokeWidth ?? 1.35) : 1.35
}
</script>

<template>
  <svg
    class="codicon"
    :width="size"
    :height="size"
    viewBox="0 0 16 16"
    aria-hidden="true"
  >
    <template v-for="(el, index) in icon.elements" :key="index">
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
        :stroke-width="strokeWidth(el)"
        stroke-linecap="round"
        stroke-linejoin="round"
        :d="el.d"
      />
      <circle
        v-else
        :cx="el.cx"
        :cy="el.cy"
        :r="el.r"
        :fill="el.fill ?? 'currentColor'"
      />
    </template>
  </svg>
</template>

<style scoped>
.codicon {
  display: block;
  flex-shrink: 0;
}
</style>
