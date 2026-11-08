<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { Terminal } from '@xterm/xterm'
import { FitAddon } from '@xterm/addon-fit'
import { useSurvivingUnmount } from '@renderer/composables/useSurvivingUnmount'
import { onColorThemeChange, useAppearanceStore } from '@renderer/stores/appearanceStore'
import { useWorkspaceStore } from '@renderer/stores/workspaceStore'
import { buildXtermTheme } from '@renderer/utils/xtermTheme'
import '@xterm/xterm/css/xterm.css'

const props = withDefaults(
  defineProps<{
    /** False when bottom panel hidden or Problems tab active — xterm must refit on show. */
    visible?: boolean
  }>(),
  { visible: true },
)

const containerRef = ref<HTMLDivElement | null>(null)
const workspace = useWorkspaceStore()
const appearance = useAppearanceStore()
const { colorTheme } = storeToRefs(appearance)

let term: Terminal | null = null
let fitAddon: FitAddon | null = null
let offData: (() => void) | null = null
let offExit: (() => void) | null = null
let offTheme: (() => void) | null = null
  let resizeObserver: ResizeObserver | null = null
  let resizeRaf = 0
  let refitRetries = 0
  let lastCols = 0
  let lastRows = 0
  let restartTimer: ReturnType<typeof setTimeout> | null = null

  const { bumpMount, scheduleTeardown } = useSurvivingUnmount()
  let mountGeneration = 0

function applyXtermTheme(): void {
  if (!term) return
  const theme = buildXtermTheme(colorTheme.value.tokens, colorTheme.value.category)
  term.options.theme = theme
}

function refitTerminal(): void {
  if (!fitAddon || !term || !containerRef.value) return
  if (resizeRaf) cancelAnimationFrame(resizeRaf)
  resizeRaf = requestAnimationFrame(() => {
    resizeRaf = 0
    if (!fitAddon || !term || !containerRef.value) return
    const { clientWidth, clientHeight } = containerRef.value
    if ((clientWidth < 2 || clientHeight < 2) && refitRetries < 8) {
      refitRetries += 1
      refitTerminal()
      return
    }
    refitRetries = 0
    fitAddon.fit()
    const cols = term.cols
    const rows = term.rows
    if (cols < 1 || rows < 1) return
    if (cols === lastCols && rows === lastRows) return
    lastCols = cols
    lastRows = rows
    void window.terminalApi.resize(cols, rows)
  })
}

function scheduleRefitWhenVisible(): void {
  void nextTick(() => {
    requestAnimationFrame(() => refitTerminal())
  })
}

async function startTerminal() {
  if (!containerRef.value || term) return

  const theme = buildXtermTheme(colorTheme.value.tokens, colorTheme.value.category)

  term = new Terminal({
    cursorBlink: true,
    fontSize: 12,
    fontFamily: 'Consolas, "Cascadia Mono", "Courier New", monospace',
    allowProposedApi: true,
    theme,
  })
  fitAddon = new FitAddon()
  term.loadAddon(fitAddon)
  term.open(containerRef.value)
  if (props.visible) {
    scheduleRefitWhenVisible()
  }

  await window.terminalApi.create(workspace.rootPath)
  term.onData(data => {
    void window.terminalApi.write(data)
  })

  offData = window.terminalApi.onData(data => term?.write(data))
  offExit = window.terminalApi.onExit(() => {
    term?.writeln('\r\n[进程已退出]')
    if (restartTimer) clearTimeout(restartTimer)
    restartTimer = setTimeout(() => {
      restartTimer = null
      void restartTerminal()
    }, 400)
  })

  resizeObserver = new ResizeObserver(() => {
    if (!props.visible) return
    refitTerminal()
  })
  resizeObserver.observe(containerRef.value)
}

async function restartTerminal() {
  if (!containerRef.value) return
  offData?.()
  offExit?.()
  offData = null
  offExit = null
  resizeObserver?.disconnect()
  resizeObserver = null
  term?.dispose()
  term = null
  fitAddon = null
  await startTerminal()
}

watch(colorTheme, () => {
  applyXtermTheme()
})

watch(
  () => workspace.rootPath,
  (next, prev) => {
    if (next && next !== prev && term) {
      void restartTerminal()
    }
  },
)

watch(
  () => props.visible,
  visible => {
    if (visible) scheduleRefitWhenVisible()
  },
)

function onBottomPanelHeightChange(): void {
  if (props.visible) refitTerminal()
}

defineExpose({ refit: onBottomPanelHeightChange })

onMounted(() => {
  mountGeneration = bumpMount()
  offTheme = onColorThemeChange(() => applyXtermTheme())
  void startTerminal()
})

onUnmounted(() => {
  const generation = mountGeneration
  if (restartTimer) {
    clearTimeout(restartTimer)
    restartTimer = null
  }
  offTheme?.()
  offTheme = null
  offData?.()
  offExit?.()
  resizeObserver?.disconnect()
  if (resizeRaf) cancelAnimationFrame(resizeRaf)
  term?.dispose()
  term = null
  fitAddon = null
  scheduleTeardown(generation, () => {
    void window.terminalApi.kill()
  })
})
</script>

<template>
  <div ref="containerRef" class="terminal-panel" data-testid="terminal-panel" />
</template>

<style scoped>
.terminal-panel {
  width: 100%;
  height: 100%;
  min-height: 160px;
  padding: 4px;
  background: var(--ide-terminal-bg);
  color: var(--ide-terminal-fg);
}

.terminal-panel :deep(.xterm),
.terminal-panel :deep(.xterm-viewport) {
  background: var(--ide-terminal-bg) !important;
}
</style>
