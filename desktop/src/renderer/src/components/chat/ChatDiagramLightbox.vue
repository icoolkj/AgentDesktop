<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { diagramLightbox } from '@renderer/utils/diagramLightbox'

const { open, svgHtml, loading, error, title, closeDiagram } = diagramLightbox

const viewportRef = ref<HTMLElement | null>(null)
const baseWidth = ref(960)
const baseHeight = ref(540)
const zoom = ref(1)
const panX = ref(0)
const panY = ref(0)
const isDragging = ref(false)

let dragStartX = 0
let dragStartY = 0
let panStartX = 0
let panStartY = 0

const MIN_ZOOM = 0.5
const MAX_ZOOM = 4
const ZOOM_STEP = 0.25

const zoomLabel = computed(() => `${Math.round(zoom.value * 100)}%`)

const sizerStyle = computed(() => ({
  width: `${Math.ceil(baseWidth.value * zoom.value + Math.abs(panX.value))}px`,
  height: `${Math.ceil(baseHeight.value * zoom.value + Math.abs(panY.value))}px`,
}))

const canvasStyle = computed(() => ({
  width: `${baseWidth.value}px`,
  height: `${baseHeight.value}px`,
  transform: `translate(${panX.value}px, ${panY.value}px) scale(${zoom.value})`,
}))

function clampZoom(value: number): number {
  return Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, value))
}

function setZoom(value: number): void {
  zoom.value = clampZoom(value)
}

function zoomIn(): void {
  setZoom(zoom.value + ZOOM_STEP)
}

function zoomOut(): void {
  setZoom(zoom.value - ZOOM_STEP)
}

function resetZoom(): void {
  zoom.value = 1
  panX.value = 0
  panY.value = 0
}

function measureSvg(): void {
  const svg = viewportRef.value?.querySelector('svg')
  if (!svg) return

  const viewBox = svg.viewBox?.baseVal
  if (viewBox?.width > 0 && viewBox?.height > 0) {
    baseWidth.value = viewBox.width
    baseHeight.value = viewBox.height
    return
  }

  const rect = svg.getBoundingClientRect()
  baseWidth.value = Math.max(rect.width, 640)
  baseHeight.value = Math.max(rect.height, 360)
}

function fitToWidth(): void {
  const viewport = viewportRef.value
  if (!viewport) return
  measureSvg()
  const available = Math.max(viewport.clientWidth - 32, 320)
  setZoom(clampZoom(available / baseWidth.value))
  panX.value = 0
  panY.value = 0
}

function openWithReadableZoom(): void {
  resetZoom()
  void nextTick(() => {
    measureSvg()
    fitToWidth()
    if (zoom.value < 1) setZoom(1)
  })
}

function onWheel(event: WheelEvent): void {
  if (!open.value) return
  if (!event.ctrlKey && !event.metaKey) return
  event.preventDefault()
  const delta = event.deltaY > 0 ? -ZOOM_STEP : ZOOM_STEP
  setZoom(zoom.value + delta)
}

function onPointerDown(event: PointerEvent): void {
  if (!open.value || event.button !== 0) return
  isDragging.value = true
  dragStartX = event.clientX
  dragStartY = event.clientY
  panStartX = panX.value
  panStartY = panY.value
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
}

function onPointerMove(event: PointerEvent): void {
  if (!isDragging.value) return
  panX.value = panStartX + (event.clientX - dragStartX)
  panY.value = panStartY + (event.clientY - dragStartY)
}

function onPointerUp(event: PointerEvent): void {
  if (!isDragging.value) return
  isDragging.value = false
  ;(event.currentTarget as HTMLElement).releasePointerCapture(event.pointerId)
}

function onKeyDown(event: KeyboardEvent): void {
  if (!open.value) return
  if (event.key === 'Escape') {
    closeDiagram()
    return
  }
  if (event.key === '+' || event.key === '=') {
    event.preventDefault()
    zoomIn()
  }
  if (event.key === '-') {
    event.preventDefault()
    zoomOut()
  }
  if (event.key === '0') {
    event.preventDefault()
    openWithReadableZoom()
  }
}

watch(open, isOpen => {
  document.body.style.overflow = isOpen ? 'hidden' : ''
})

watch(svgHtml, html => {
  if (open.value && html) openWithReadableZoom()
})

onMounted(() => {
  window.addEventListener('keydown', onKeyDown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeyDown)
  document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="diagram-lightbox" @click.self="closeDiagram">
      <div class="diagram-lightbox__panel" role="dialog" aria-modal="true" :aria-label="title">
        <header class="diagram-lightbox__header">
          <span class="diagram-lightbox__title">{{ title }}</span>
          <div class="diagram-lightbox__tools">
            <button type="button" class="diagram-lightbox__tool" title="缩小 (−)" @click="zoomOut">−</button>
            <span class="diagram-lightbox__zoom">{{ zoomLabel }}</span>
            <button type="button" class="diagram-lightbox__tool" title="放大 (+)" @click="zoomIn">+</button>
            <button type="button" class="diagram-lightbox__tool diagram-lightbox__tool--text" @click="resetZoom">
              100%
            </button>
            <button type="button" class="diagram-lightbox__tool diagram-lightbox__tool--text" @click="setZoom(1.5)">
              150%
            </button>
            <button type="button" class="diagram-lightbox__tool diagram-lightbox__tool--text" @click="setZoom(2)">
              200%
            </button>
            <button type="button" class="diagram-lightbox__tool diagram-lightbox__tool--text" @click="fitToWidth">
              适应宽度
            </button>
          </div>
          <button type="button" class="diagram-lightbox__close" title="关闭 (Esc)" @click="closeDiagram">✕</button>
        </header>

        <div
          ref="viewportRef"
          class="diagram-lightbox__body"
          :class="{ 'diagram-lightbox__body--dragging': isDragging }"
          @wheel="onWheel"
          @pointerdown="onPointerDown"
          @pointermove="onPointerMove"
          @pointerup="onPointerUp"
          @pointercancel="onPointerUp"
        >
          <p v-if="loading" class="diagram-lightbox__status">正在渲染原尺寸图表…</p>
          <p v-else-if="error" class="diagram-lightbox__status diagram-lightbox__status--error">{{ error }}</p>
          <div v-else class="diagram-lightbox__sizer" :style="sizerStyle">
            <div class="diagram-lightbox__canvas" :style="canvasStyle" v-html="svgHtml" />
          </div>
        </div>

        <p class="diagram-lightbox__hint">
          原尺寸 SVG · Ctrl + 滚轮缩放 · 拖拽平移 · 150% / 200% 更清晰 · Esc 关闭
        </p>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.diagram-lightbox {
  position: fixed;
  inset: 0;
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: color-mix(in srgb, var(--ide-shadow) 55%, transparent);
  backdrop-filter: blur(4px);
}

.diagram-lightbox__panel {
  display: flex;
  flex-direction: column;
  width: min(98vw, 1400px);
  max-height: 94vh;
  border-radius: 12px;
  border: 1px solid var(--ide-border);
  background: var(--ide-surface-bg);
  box-shadow: 0 16px 48px var(--ide-shadow);
  overflow: hidden;
}

.diagram-lightbox__header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--ide-border);
  background: var(--ide-tab-bar-bg);
}

.diagram-lightbox__title {
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--ide-fg);
  flex-shrink: 0;
}

.diagram-lightbox__tools {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
  justify-content: center;
  flex-wrap: wrap;
}

.diagram-lightbox__tool {
  min-width: 28px;
  height: 28px;
  padding: 0 8px;
  border-radius: 6px;
  border: 1px solid var(--ide-border);
  background: var(--ide-button-bg);
  color: var(--ide-fg);
  font-size: 0.78rem;
  cursor: pointer;
}

.diagram-lightbox__tool--text {
  min-width: auto;
  font-size: 0.68rem;
}

.diagram-lightbox__tool:hover {
  background: var(--ide-hover-overlay);
  border-color: color-mix(in srgb, var(--ide-accent) 40%, var(--ide-border));
}

.diagram-lightbox__zoom {
  min-width: 44px;
  text-align: center;
  font-size: 0.72rem;
  font-family: ui-monospace, monospace;
  color: var(--ide-muted);
}

.diagram-lightbox__close {
  border: none;
  background: transparent;
  color: var(--ide-muted);
  font-size: 1rem;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  flex-shrink: 0;
}

.diagram-lightbox__close:hover {
  background: var(--ide-hover-overlay);
  color: var(--ide-fg);
}

.diagram-lightbox__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 16px;
  background: var(--ide-code-bg);
  cursor: grab;
  touch-action: none;
}

.diagram-lightbox__body--dragging {
  cursor: grabbing;
  user-select: none;
}

.diagram-lightbox__sizer {
  position: relative;
  min-width: 100%;
  min-height: 100%;
}

.diagram-lightbox__canvas {
  display: inline-block;
  transform-origin: top left;
  will-change: transform;
}

.diagram-lightbox__canvas :deep(svg) {
  display: block;
  max-width: none !important;
  width: auto !important;
  height: auto !important;
}

.diagram-lightbox__status {
  margin: 0;
  padding: 24px;
  text-align: center;
  font-size: 0.82rem;
  color: var(--ide-muted);
}

.diagram-lightbox__status--error {
  color: var(--ide-error);
}

.diagram-lightbox__hint {
  margin: 0;
  padding: 8px 14px;
  font-size: 0.68rem;
  color: var(--ide-muted);
  border-top: 1px solid var(--ide-border);
  background: var(--ide-surface-bg);
}
</style>
