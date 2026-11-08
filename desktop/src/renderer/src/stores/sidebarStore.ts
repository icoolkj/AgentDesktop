import { defineStore } from 'pinia'
import { ref } from 'vue'

export type SidebarPanel = 'explorer' | 'search' | 'git'

export const useSidebarStore = defineStore('sidebar', () => {
  const activePanel = ref<SidebarPanel>('explorer')

  function setPanel(panel: SidebarPanel): void {
    activePanel.value = panel
  }

  return {
    activePanel,
    setPanel,
  }
})
