import { nextTick, onMounted, onUnmounted, type Ref, watch, type WatchSource } from 'vue'
import { storeToRefs } from 'pinia'
import { useAppearanceStore } from '@renderer/stores/appearanceStore'
import { onColorThemeChange } from '@renderer/modules/appearance/store'
import {
  invalidateMermaidTheme,
  renderMermaidIn,
  resetMermaidNodes,
} from '@renderer/utils/mermaidRender'

/** Render `.md-mermaid` blocks inside a container after v-html updates. */
export function useMermaidContainer(
  root: Ref<HTMLElement | null>,
  watchSources: WatchSource<unknown>[],
): void {
  const appearanceStore = useAppearanceStore()
  const { colorTheme } = storeToRefs(appearanceStore)

  let offTheme: (() => void) | null = null

  async function refreshMermaid() {
    await nextTick()
    await new Promise<void>(resolve => {
      requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
    })
    await renderMermaidIn(root.value, colorTheme.value)
  }

  function onThemeChanged() {
    invalidateMermaidTheme()
    resetMermaidNodes(root.value)
    void refreshMermaid()
  }

  watch(
    watchSources,
    () => {
      resetMermaidNodes(root.value)
      void refreshMermaid()
    },
    { flush: 'post' },
  )

  onMounted(() => {
    offTheme = onColorThemeChange(onThemeChanged)
    void refreshMermaid()
  })

  onUnmounted(() => {
    offTheme?.()
  })
}
