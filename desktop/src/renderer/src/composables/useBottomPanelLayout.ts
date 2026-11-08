import { ref, watch } from 'vue'

import { useWorkspaceUiStore, type BottomPanelTab } from '@renderer/stores/workspaceUiStore'

export const BOTTOM_PANEL_DEFAULT_HEIGHT = 220
export const BOTTOM_PANEL_MIN_HEIGHT = 120
export const BOTTOM_PANEL_MIN_EDITOR_HEIGHT = 80
const PERSIST_DEBOUNCE_MS = 400

export function useBottomPanelLayout(getMaxHeight?: () => number) {
  const workspaceUi = useWorkspaceUiStore()
  const bottomPanelOpen = ref(true)
  const bottomPanelTab = ref<BottomPanelTab>('terminal')
  const bottomPanelHeight = ref(BOTTOM_PANEL_DEFAULT_HEIGHT)

  let persistReady = false
  let persistTimer: ReturnType<typeof setTimeout> | null = null

  function resolveMaxHeight(): number {
    const max = getMaxHeight?.()
    if (max != null && max > 0) return max
    return BOTTOM_PANEL_DEFAULT_HEIGHT
  }

  function clampHeight(value: number): number {
    return Math.max(BOTTOM_PANEL_MIN_HEIGHT, Math.min(resolveMaxHeight(), value))
  }

  function schedulePersist(): void {
    if (!persistReady) return
    if (persistTimer) clearTimeout(persistTimer)
    persistTimer = setTimeout(() => {
      persistTimer = null
      void window.desktopApi.setPanelLayout({
        bottomPanelHeight: bottomPanelHeight.value,
      })
    }, PERSIST_DEBOUNCE_MS)
  }

  function applyFromPrefs(): void {
    bottomPanelOpen.value = workspaceUi.bottomPanelOpenOnStartup
    bottomPanelTab.value = workspaceUi.bottomPanelTabOnStartup
  }

  async function load(): Promise<void> {
    await workspaceUi.init()
    const saved = await window.desktopApi.getPanelLayout()

    if (saved?.bottomPanelHeight != null) {
      bottomPanelHeight.value = clampHeight(saved.bottomPanelHeight)
    }

    applyFromPrefs()

    persistReady = true
  }

  watch([bottomPanelHeight], schedulePersist)

  function resizeBottomPanel(delta: number): void {
    bottomPanelHeight.value = clampHeight(bottomPanelHeight.value - delta)
  }

  function enforceHeightLimit(): void {
    bottomPanelHeight.value = clampHeight(bottomPanelHeight.value)
  }

  function maximizeBottomPanel(): void {
    bottomPanelHeight.value = resolveMaxHeight()
  }

  function resetBottomPanelHeight(): void {
    bottomPanelHeight.value = clampHeight(BOTTOM_PANEL_DEFAULT_HEIGHT)
  }

  function openBottomPanel(tab: BottomPanelTab = 'terminal'): void {
    bottomPanelTab.value = tab
    bottomPanelOpen.value = true
  }

  function toggleBottomPanel(tab: BottomPanelTab = 'terminal'): void {
    if (bottomPanelOpen.value && bottomPanelTab.value === tab) {
      bottomPanelOpen.value = false
      return
    }
    openBottomPanel(tab)
  }

  function onBottomPanelTabChange(tab: BottomPanelTab): void {
    bottomPanelTab.value = tab
    bottomPanelOpen.value = true
  }

  return {
    bottomPanelOpen,
    bottomPanelTab,
    bottomPanelHeight,
    load,
    applyFromPrefs,
    resizeBottomPanel,
    enforceHeightLimit,
    maximizeBottomPanel,
    resetBottomPanelHeight,
    openBottomPanel,
    toggleBottomPanel,
    onBottomPanelTabChange,
  }
}
