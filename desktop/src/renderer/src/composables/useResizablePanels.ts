import { onMounted, ref, watch } from 'vue'

import { PANEL_DEFAULTS, PANEL_LIMITS } from './useResizablePanels.constants'

export { PANEL_DEFAULTS, PANEL_LIMITS } from './useResizablePanels.constants'

interface PanelLayoutState {
  sidebar: number
  chat: number
  sidebarCollapsed: boolean
  chatCollapsed: boolean
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
}

function normalizeLayout(parsed: Partial<PanelLayoutState> | null): PanelLayoutState {
  return {
    sidebar: clamp(
      parsed?.sidebar ?? PANEL_DEFAULTS.sidebar,
      PANEL_LIMITS.sidebar.min,
      PANEL_LIMITS.sidebar.max,
    ),
    chat: clamp(parsed?.chat ?? PANEL_DEFAULTS.chat, PANEL_LIMITS.chat.min, PANEL_LIMITS.chat.max),
    sidebarCollapsed: parsed?.sidebarCollapsed ?? false,
    chatCollapsed: parsed?.chatCollapsed ?? false,
  }
}

const PANEL_PERSIST_DEBOUNCE_MS = 400

export function useResizablePanels() {
  const sidebarWidth = ref<number>(PANEL_DEFAULTS.sidebar)
  const chatWidth = ref<number>(PANEL_DEFAULTS.chat)
  const sidebarCollapsed = ref(false)
  const chatCollapsed = ref(false)
  let persistReady = false
  let persistTimer: ReturnType<typeof setTimeout> | null = null

  function persistNow(): void {
    if (!persistReady) return
    void window.desktopApi.setPanelLayout({
      sidebar: sidebarWidth.value,
      chat: chatWidth.value,
      sidebarCollapsed: sidebarCollapsed.value,
      chatCollapsed: chatCollapsed.value,
    })
  }

  function schedulePersist(): void {
    if (!persistReady) return
    if (persistTimer) clearTimeout(persistTimer)
    persistTimer = setTimeout(() => {
      persistTimer = null
      persistNow()
    }, PANEL_PERSIST_DEBOUNCE_MS)
  }

  function flushPanelLayout(): void {
    if (persistTimer) {
      clearTimeout(persistTimer)
      persistTimer = null
    }
    persistNow()
  }

  onMounted(async () => {
    const saved = await window.desktopApi.getPanelLayout()
    const layout = normalizeLayout(saved)
    sidebarWidth.value = layout.sidebar
    chatWidth.value = layout.chat
    sidebarCollapsed.value = layout.sidebarCollapsed
    chatCollapsed.value = layout.chatCollapsed
    persistReady = true
  })

  watch([sidebarWidth, chatWidth, sidebarCollapsed, chatCollapsed], schedulePersist)

  function resizeSidebar(delta: number): void {
    if (sidebarCollapsed.value) return
    sidebarWidth.value = clamp(
      sidebarWidth.value + delta,
      PANEL_LIMITS.sidebar.min,
      PANEL_LIMITS.sidebar.max,
    )
  }

  function resizeChat(delta: number): void {
    if (chatCollapsed.value) return
    chatWidth.value = clamp(
      chatWidth.value - delta,
      PANEL_LIMITS.chat.min,
      PANEL_LIMITS.chat.max,
    )
  }

  function resetSidebarWidth(): void {
    sidebarCollapsed.value = false
    sidebarWidth.value = PANEL_DEFAULTS.sidebar
  }

  function resetChatWidth(): void {
    chatCollapsed.value = false
    chatWidth.value = PANEL_DEFAULTS.chat
  }

  function collapseSidebar(): void {
    sidebarCollapsed.value = true
  }

  function expandSidebar(): void {
    sidebarCollapsed.value = false
  }

  function collapseChat(): void {
    chatCollapsed.value = true
  }

  function expandChat(): void {
    chatCollapsed.value = false
  }

  return {
    sidebarWidth,
    chatWidth,
    sidebarCollapsed,
    chatCollapsed,
    resizeSidebar,
    resizeChat,
    resetSidebarWidth,
    resetChatWidth,
    collapseSidebar,
    expandSidebar,
    collapseChat,
    expandChat,
    flushPanelLayout,
  }
}
