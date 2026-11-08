import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { router } from './router'
import { applyColorThemeById, loadAppearancePrefs } from '@renderer/utils/colorThemes'
import { useAppearanceStore } from '@renderer/stores/appearanceStore'
import { installE2eBridge } from './e2eBridge'
import './styles/index.css'

applyColorThemeById(loadAppearancePrefs().colorThemeId)

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)
useAppearanceStore(pinia).init()

if (import.meta.env.VITE_E2E === '1') {
  installE2eBridge(pinia)
}

app.mount('#app')
