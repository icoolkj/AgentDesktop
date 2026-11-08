import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import type { ColorThemeId, FileIconThemeId, ColorThemeDefinition } from './types'
import {
  applyColorThemeById,
  COLOR_THEMES,
  FILE_ICON_THEMES,
  getColorTheme,
  loadAppearancePrefs,
  saveAppearancePrefs,
} from './colorThemes'
import { monaco } from '@renderer/utils/monacoSetup'

type ThemeListener = (monacoTheme: 'vs' | 'vs-dark') => void
type ColorThemeListener = (theme: ColorThemeDefinition) => void

const monacoThemeListeners = new Set<ThemeListener>()
const colorThemeListeners = new Set<ColorThemeListener>()

export function onMonacoThemeChange(listener: ThemeListener): () => void {
  monacoThemeListeners.add(listener)
  return () => {
    monacoThemeListeners.delete(listener)
  }
}

/** Subscribe to full color-theme changes (terminal, custom widgets, etc.). */
export function onColorThemeChange(listener: ColorThemeListener): () => void {
  colorThemeListeners.add(listener)
  return () => {
    colorThemeListeners.delete(listener)
  }
}

function notifyColorTheme(theme: ColorThemeDefinition): void {
  try {
    monaco.editor.setTheme(theme.monaco)
  } catch {
    /* monaco may not be loaded yet */
  }
  for (const listener of monacoThemeListeners) {
    listener(theme.monaco)
  }
  for (const listener of colorThemeListeners) {
    listener(theme)
  }
}

export const useAppearanceStore = defineStore('appearance', () => {
  const prefs = loadAppearancePrefs()
  const colorThemeId = ref<ColorThemeId>(prefs.colorThemeId)
  const fileIconThemeId = ref<FileIconThemeId>(prefs.fileIconThemeId)

  const colorTheme = computed(() => getColorTheme(colorThemeId.value))
  const fileIconTheme = computed(
    () => FILE_ICON_THEMES.find(theme => theme.id === fileIconThemeId.value) ?? FILE_ICON_THEMES[2]!,
  )
  const monacoTheme = computed(() => colorTheme.value.monaco)

  const lightThemes = computed(() => COLOR_THEMES.filter(theme => theme.category === 'light'))
  const darkThemes = computed(() => COLOR_THEMES.filter(theme => theme.category === 'dark'))
  const fileIconThemes = computed(() => FILE_ICON_THEMES)

  function persist(): void {
    saveAppearancePrefs({
      colorThemeId: colorThemeId.value,
      fileIconThemeId: fileIconThemeId.value,
    })
  }

  function setColorTheme(id: ColorThemeId): void {
    if (colorThemeId.value === id) return
    colorThemeId.value = id
    const theme = applyColorThemeById(id)
    notifyColorTheme(theme)
    persist()
  }

  function setFileIconTheme(id: FileIconThemeId): void {
    if (fileIconThemeId.value === id) return
    fileIconThemeId.value = id
    persist()
  }

  function init(): void {
    const theme = applyColorThemeById(colorThemeId.value)
    notifyColorTheme(theme)
  }

  return {
    colorThemeId,
    fileIconThemeId,
    colorTheme,
    fileIconTheme,
    monacoTheme,
    lightThemes,
    darkThemes,
    fileIconThemes,
    setColorTheme,
    setFileIconTheme,
    init,
  }
})
