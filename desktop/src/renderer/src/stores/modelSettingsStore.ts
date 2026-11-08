import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import {
  DEFAULT_MODEL_PREFS,
  MODEL_PREFS_STORAGE_KEY,
  type ChatModelSelection,
  type LlmModelInfo,
  type ModelPreferences,
} from '@renderer/types/modelSettings'
import { useSettingsStore } from '@renderer/stores/settingsStore'

/** CC-style: catalog cached for the session; refresh only on explicit action or TTL expiry. */
const MODEL_CATALOG_SESSION_KEY = 'agent-desktop.model-catalog'
const CATALOG_TTL_MS = 10 * 60 * 1000

interface ModelCatalogSnapshot {
  models: LlmModelInfo[]
  defaultModel?: string
  fetchedAt: number
}

function loadPrefs(): ModelPreferences {
  try {
    const raw = localStorage.getItem(MODEL_PREFS_STORAGE_KEY)
    if (!raw) return { ...DEFAULT_MODEL_PREFS, enabled: {}, custom: [] }
    const parsed = JSON.parse(raw) as Partial<ModelPreferences>
    return {
      chatSelection:
        parsed.chatSelection === 'auto' || typeof parsed.chatSelection === 'string'
          ? parsed.chatSelection
          : 'auto',
      lastManualSelection:
        typeof parsed.lastManualSelection === 'string' ? parsed.lastManualSelection : undefined,
      enabled: parsed.enabled && typeof parsed.enabled === 'object' ? parsed.enabled : {},
      custom: Array.isArray(parsed.custom) ? parsed.custom.filter(Boolean) : [],
    }
  } catch {
    return { ...DEFAULT_MODEL_PREFS, enabled: {}, custom: [] }
  }
}

function persistPrefs(prefs: ModelPreferences): void {
  try {
    localStorage.setItem(MODEL_PREFS_STORAGE_KEY, JSON.stringify(prefs))
  } catch {
    // ignore
  }
}

function readSessionCatalog(): ModelCatalogSnapshot | null {
  try {
    const raw = sessionStorage.getItem(MODEL_CATALOG_SESSION_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<ModelCatalogSnapshot>
    if (!Array.isArray(parsed.models) || typeof parsed.fetchedAt !== 'number') return null
    return {
      models: parsed.models.filter(
        (item): item is LlmModelInfo =>
          item != null && typeof item === 'object' && typeof item.id === 'string',
      ),
      defaultModel: typeof parsed.defaultModel === 'string' ? parsed.defaultModel : undefined,
      fetchedAt: parsed.fetchedAt,
    }
  } catch {
    return null
  }
}

function writeSessionCatalog(snapshot: ModelCatalogSnapshot): void {
  try {
    sessionStorage.setItem(MODEL_CATALOG_SESSION_KEY, JSON.stringify(snapshot))
  } catch {
    // ignore
  }
}

function shortModelLabel(id: string): string {
  if (id.length <= 28) return id
  const slash = id.indexOf('/')
  if (slash > 0 && slash < id.length - 1) {
    return id.slice(slash + 1)
  }
  return `${id.slice(0, 24)}…`
}

function sameModelList(a: LlmModelInfo[], b: LlmModelInfo[]): boolean {
  if (a.length !== b.length) return false
  for (let i = 0; i < a.length; i++) {
    if (a[i]?.id !== b[i]?.id) return false
    if (a[i]?.ownedBy !== b[i]?.ownedBy) return false
  }
  return true
}

export const useModelSettingsStore = defineStore('modelSettings', () => {
  const prefs = ref<ModelPreferences>(loadPrefs())
  const remoteModels = ref<LlmModelInfo[]>([])
  const defaultModel = ref<string>('')
  const catalogFetchedAt = ref<number | null>(null)
  const catalogLoading = ref(false)
  const catalogError = ref<string | null>(null)

  let catalogFetchPromise: Promise<boolean> | null = null

  function hydrateCatalogFromSession(): void {
    const cached = readSessionCatalog()
    if (!cached) return
    if (Date.now() - cached.fetchedAt > CATALOG_TTL_MS) return
    remoteModels.value = cached.models
    if (cached.defaultModel) defaultModel.value = cached.defaultModel
    catalogFetchedAt.value = cached.fetchedAt
  }

  hydrateCatalogFromSession()

  const chatSelection = computed(() => prefs.value.chatSelection)

  const autoEnabled = computed(() => prefs.value.chatSelection === 'auto')

  const chatModelLabel = computed(() =>
    autoEnabled.value ? 'Auto' : shortModelLabel(prefs.value.chatSelection),
  )

  const catalogReady = computed(
    () => remoteModels.value.length > 0 || Boolean(defaultModel.value),
  )

  const enabledModelIds = computed(() => {
    const ids = new Set<string>()
    for (const model of remoteModels.value) {
      if (isModelEnabled(model.id)) ids.add(model.id)
    }
    for (const id of prefs.value.custom) {
      if (isModelEnabled(id)) ids.add(id)
    }
    if (defaultModel.value && isModelEnabled(defaultModel.value)) {
      ids.add(defaultModel.value)
    }
    const sel = prefs.value.chatSelection
    if (typeof sel === 'string' && sel !== 'auto') {
      ids.add(sel)
    }
    const last = prefs.value.lastManualSelection
    if (last) {
      ids.add(last)
    }
    return [...ids].sort((a, b) => a.localeCompare(b))
  })

  const composerOptions = computed(() => {
    const options: { id: ChatModelSelection; label: string; hint?: string }[] = [
      {
        id: 'auto',
        label: 'Auto',
        hint: defaultModel.value ? `默认 · ${shortModelLabel(defaultModel.value)}` : '使用设置中的默认模型',
      },
    ]
    for (const id of enabledModelIds.value) {
      options.push({ id, label: shortModelLabel(id), hint: id })
    }
    return options
  })

  const manualModelOptions = computed(() =>
    enabledModelIds.value.map(id => ({
      id,
      label: shortModelLabel(id),
      hint: id,
    })),
  )

  function isModelEnabled(id: string): boolean {
    const stored = prefs.value.enabled[id]
    return stored !== false
  }

  function isCatalogFresh(): boolean {
    if (catalogFetchedAt.value == null) return false
    return Date.now() - catalogFetchedAt.value < CATALOG_TTL_MS
  }

  function setRemoteModels(models: LlmModelInfo[], serverDefault?: string): void {
    const nextDefault = serverDefault ?? defaultModel.value
    if (sameModelList(remoteModels.value, models) && nextDefault === defaultModel.value) {
      return
    }

    remoteModels.value = models
    if (serverDefault) defaultModel.value = serverDefault

    const nextEnabled = { ...prefs.value.enabled }
    for (const model of models) {
      if (nextEnabled[model.id] === undefined) {
        nextEnabled[model.id] = true
      }
    }
    if (serverDefault && nextEnabled[serverDefault] === undefined) {
      nextEnabled[serverDefault] = true
    }
    prefs.value = { ...prefs.value, enabled: nextEnabled }
    persistPrefs(prefs.value)
  }

  function applyCatalog(models: LlmModelInfo[], serverDefault?: string): void {
    setRemoteModels(models, serverDefault)
    const fetchedAt = Date.now()
    catalogFetchedAt.value = fetchedAt
    catalogError.value = null
    writeSessionCatalog({
      models: [...models],
      defaultModel: serverDefault ?? (defaultModel.value || undefined),
      fetchedAt,
    })
  }

  /**
   * Load CPA model catalog once per session (10m TTL). Dedupes concurrent calls.
   * Composer reads from cache; Settings「刷新」uses `{ force: true }`.
   */
  async function ensureModelCatalog(options?: { force?: boolean }): Promise<boolean> {
    const force = options?.force ?? false
    if (!force && catalogReady.value && isCatalogFresh()) {
      return true
    }
    if (catalogFetchPromise) {
      return catalogFetchPromise
    }

    catalogFetchPromise = (async () => {
      catalogLoading.value = true
      catalogError.value = null
      try {
        const settings = useSettingsStore()
        if (!settings.llm) {
          await settings.loadLlmSettings()
        }
        const response = await settings.loadAvailableModels()
        if (response) {
          applyCatalog(response.models ?? [], response.defaultModel)
          if (response.error) {
            catalogError.value = response.error
          }
          return true
        }
        if (settings.llm?.model) {
          applyCatalog([], settings.llm.model)
          return true
        }
        catalogError.value = settings.error ?? '无法加载模型列表'
        return false
      } finally {
        catalogLoading.value = false
        catalogFetchPromise = null
      }
    })()

    return catalogFetchPromise
  }

  function setChatSelection(selection: ChatModelSelection): void {
    if (selection === 'auto') {
      prefs.value = { ...prefs.value, chatSelection: 'auto' }
    } else {
      prefs.value = {
        ...prefs.value,
        chatSelection: selection,
        lastManualSelection: selection,
      }
    }
    persistPrefs(prefs.value)
  }

  function setAutoEnabled(enabled: boolean): void {
    if (enabled) {
      setChatSelection('auto')
      return
    }
    const last = prefs.value.lastManualSelection
    const fallback =
      (last && isModelEnabled(last) ? last : undefined) ??
      enabledModelIds.value[0] ??
      defaultModel.value
    if (fallback) {
      setChatSelection(fallback)
    }
  }

  function toggleModelEnabled(id: string, enabled: boolean): void {
    prefs.value = {
      ...prefs.value,
      enabled: { ...prefs.value.enabled, [id]: enabled },
    }
    persistPrefs(prefs.value)
    if (!enabled && prefs.value.chatSelection === id) {
      setChatSelection('auto')
    }
  }

  function addCustomModel(id: string): boolean {
    const trimmed = id.trim()
    if (!trimmed) return false
    const custom = prefs.value.custom.includes(trimmed)
      ? prefs.value.custom
      : [...prefs.value.custom, trimmed]
    prefs.value = {
      ...prefs.value,
      custom,
      enabled: { ...prefs.value.enabled, [trimmed]: true },
    }
    persistPrefs(prefs.value)
    return true
  }

  function removeCustomModel(id: string): void {
    prefs.value = {
      ...prefs.value,
      custom: prefs.value.custom.filter(entry => entry !== id),
    }
    persistPrefs(prefs.value)
    if (prefs.value.chatSelection === id) {
      setChatSelection('auto')
    }
  }

  function resolveSendModel(): string | undefined {
    if (prefs.value.chatSelection === 'auto') return undefined
    return prefs.value.chatSelection
  }

  function allModelRows(): { id: string; ownedBy?: string; custom: boolean }[] {
    const map = new Map<string, { id: string; ownedBy?: string; custom: boolean }>()
    for (const model of remoteModels.value) {
      map.set(model.id, { id: model.id, ownedBy: model.ownedBy, custom: false })
    }
    for (const id of prefs.value.custom) {
      if (!map.has(id)) map.set(id, { id, custom: true })
    }
    return [...map.values()].sort((a, b) => a.id.localeCompare(b.id))
  }

  return {
    prefs,
    remoteModels,
    defaultModel,
    catalogFetchedAt,
    catalogLoading,
    catalogError,
    catalogReady,
    chatSelection,
    autoEnabled,
    chatModelLabel,
    enabledModelIds,
    composerOptions,
    manualModelOptions,
    isModelEnabled,
    setRemoteModels,
    ensureModelCatalog,
    setChatSelection,
    setAutoEnabled,
    toggleModelEnabled,
    addCustomModel,
    removeCustomModel,
    resolveSendModel,
    allModelRows,
    shortModelLabel,
  }
})
