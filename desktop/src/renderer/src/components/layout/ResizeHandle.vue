<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'

const props = withDefaults(
  defineProps<{
    /** x = column resize (width), y = row resize (height) */
    axis?: 'x' | 'y'
  }>(),
  { axis: 'x' },
)

const emit = defineEmits<{
  resize: [delta: number]
  resizeEnd: []
  reset: []
}>()

const active = ref(false)

const isVertical = computed(() => props.axis === 'y')

function onMouseDown(event: MouseEvent): void {
  if (event.detail > 1) return

  event.preventDefault()
  active.value = true
  let last = props.axis === 'y' ? event.clientY : event.clientX

  const onMouseMove = (moveEvent: MouseEvent): void => {
    const current = props.axis === 'y' ? moveEvent.clientY : moveEvent.clientX
    const delta = current - last
    last = current
    emit('resize', delta)
  }

  const onMouseUp = (): void => {
    active.value = false
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
    emit('resizeEnd')
  }

  document.body.style.cursor = props.axis === 'y' ? 'row-resize' : 'col-resize'
  document.body.style.userSelect = 'none'
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
}

function onDoubleClick(): void {
  emit('reset')
}

onBeforeUnmount(() => {
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
})
</script>

<template>
  <div
    class="ide-resize-handle"
    :class="{ 'is-active': active, 'ide-resize-handle--y': isVertical }"
    role="separator"
    :aria-orientation="isVertical ? 'horizontal' : 'vertical'"
    aria-label="Drag to resize. Double-click to reset."
    :title="isVertical ? '拖动调整高度 · 双击最大化' : '拖动调整宽度 · 双击重置'"
    @mousedown="onMouseDown"
    @dblclick="onDoubleClick"
  />
</template>

<style scoped>
.ide-resize-handle {
  position: relative;
  flex-shrink: 0;
  width: 4px;
  margin: 0 -1px;
  cursor: col-resize;
  touch-action: none;
  z-index: 2;
  background: var(--ide-border);
}

.ide-resize-handle::after {
  content: '';
  position: absolute;
  inset: 0 -3px;
}

.ide-resize-handle:hover,
.ide-resize-handle.is-active {
  background: color-mix(in srgb, var(--ide-accent) 55%, var(--ide-border));
}

.ide-resize-handle--y {
  width: 100%;
  height: 4px;
  margin: 0;
  cursor: row-resize;
}

.ide-resize-handle--y::after {
  inset: -3px 0;
}
</style>
