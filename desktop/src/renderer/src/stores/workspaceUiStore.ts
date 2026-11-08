import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

export type BottomPanelTab = 'terminal' | 'problems'

export interface WorkspaceUiPrefs {
  bottomPanelOpenOnStartup: boolean
  bottomPanelTabOnStartup: BottomPanelTab
  autoScanProblemsOnOpen: boolean
}

export const DEFAULT_WORKSPACE_UI_PREFS: WorkspaceUiPrefs = {
  bottomPanelOpenOnStartup: true,
  bottomPanelTabOnStartup: 'terminal',
  autoScanProblemsOnOpen: false,
}

function normalizePrefs(input: Partial<WorkspaceUiPrefs> | null | undefined): WorkspaceUiPrefs {
  const tab = input?.bottomPanelTabOnStartup
  return {
    bottomPanelOpenOnStartup:
      input?.bottomPanelOpenOnStartup ?? DEFAULT_WORKSPACE_UI_PREFS.bottomPanelOpenOnStartup,
    bottomPanelTabOnStartup: tab === 'problems' ? 'problems' : 'terminal',
    autoScanProblemsOnOpen:
      input?.autoScanProblemsOnOpen ?? DEFAULT_WORKSPACE_UI_PREFS.autoScanProblemsOnOpen,
  }
}

export const useWorkspaceUiStore = defineStore('workspaceUi', () => {
  const prefs = ref<WorkspaceUiPrefs>({ ...DEFAULT_WORKSPACE_UI_PREFS })
  const loaded = ref(false)
  let loadPromise: Promise<void> | null = null

  const autoScanProblemsOnOpen = computed(() => prefs.value.autoScanProblemsOnOpen)
  const bottomPanelOpenOnStartup = computed(() => prefs.value.bottomPanelOpenOnStartup)
  const bottomPanelTabOnStartup = computed(() => prefs.value.bottomPanelTabOnStartup)

  async function init(): Promise<void> {
    if (loaded.value) return
    if (loadPromise) return loadPromise
    loadPromise = (async () => {
      const saved = await window.desktopApi.getWorkspaceUiPrefs()
      prefs.value = normalizePrefs(saved)
      loaded.value = true
    })()
    return loadPromise
  }

  async function updatePrefs(patch: Partial<WorkspaceUiPrefs>): Promise<void> {
    await init()
    prefs.value = normalizePrefs({ ...prefs.value, ...patch })
    await window.desktopApi.setWorkspaceUiPrefs(prefs.value)
  }

  return {
    prefs,
    loaded,
    autoScanProblemsOnOpen,
    bottomPanelOpenOnStartup,
    bottomPanelTabOnStartup,
    init,
    updatePrefs,
  }
})
