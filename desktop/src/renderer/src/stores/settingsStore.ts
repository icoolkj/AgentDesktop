import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

import type {
  ExtensionInfo,
  FeatureSettings,
  HooksSettings,
  PluginInstallResult,
  SkillInstallResult,
  SkillsInfo,
  MarketplaceInfo,
} from '@renderer/types/featureSettings'
import type { LlmSettings } from '@renderer/types/settings'
import type { LlmModelsResponse } from '@renderer/types/modelSettings'
import type { McpSettings, McpTestResult } from '@renderer/types/mcpSettings'
import { API_KEY_MASK } from '@renderer/types/settings'
import {
  describeFetchFailure,
  httpBaseFromWsUrl,
  loadStoredBackendHttpBase,
  normalizeHttpBase,
  saveStoredBackendHttpBase,
} from '@renderer/utils/agentHttp'
import { useAgentStore } from '@renderer/stores/agentStore'

export const useSettingsStore = defineStore('settings', () => {
  const llm = ref<LlmSettings | null>(null)
  const mcp = ref<McpSettings | null>(null)
  const features = ref<FeatureSettings | null>(null)
  const extensions = ref<ExtensionInfo | null>(null)
  const skillsInfo = ref<SkillsInfo | null>(null)
  const marketplaceInfo = ref<MarketplaceInfo | null>(null)
  const loading = ref(false)
  const saving = ref(false)
  const testing = ref(false)
  const error = ref<string | null>(null)
  const success = ref<string | null>(null)
  const backendReachable = ref<boolean | null>(null)

  const agent = useAgentStore()
  const manualBackendUrl = ref(loadStoredBackendHttpBase())

  const httpBaseFromAgent = computed(() => httpBaseFromWsUrl(agent.wsUrl))

  const effectiveHttpBase = computed(() => {
    return normalizeHttpBase(manualBackendUrl.value) ?? httpBaseFromAgent.value
  })

  watch(httpBaseFromAgent, value => {
    if (value) {
      manualBackendUrl.value = value
      saveStoredBackendHttpBase(value)
    }
  })

  async function ensureAgentConnected(): Promise<void> {
    if (agent.status !== 'connected') {
      await agent.reconnect()
    }
    syncBackendFromAgent()
  }

  function setManualBackendUrl(value: string): void {
    manualBackendUrl.value = value
    backendReachable.value = null
  }

  function persistManualBackendUrl(): boolean {
    const normalized = normalizeHttpBase(manualBackendUrl.value)
    if (!normalized) {
      error.value = 'Enter a valid backend URL, e.g. http://127.0.0.1:8080'
      return false
    }
    manualBackendUrl.value = normalized
    saveStoredBackendHttpBase(normalized)
    backendReachable.value = null
    return true
  }

  function syncBackendFromAgent(): void {
    const fromAgent = httpBaseFromAgent.value
    if (!fromAgent) return
    manualBackendUrl.value = fromAgent
    saveStoredBackendHttpBase(fromAgent)
    backendReachable.value = null
  }

  async function testBackendConnection(): Promise<boolean> {
    error.value = null
    success.value = null

    if (!persistManualBackendUrl()) return false

    const base = effectiveHttpBase.value
    if (!base) {
      error.value = 'Set the Agent backend URL first.'
      return false
    }

    testing.value = true
    try {
      const response = await fetch(`${base}/health`)
      if (!response.ok) {
        throw new Error(`Health check failed (${response.status})`)
      }
      backendReachable.value = true
      success.value = `Backend reachable at ${base}`
      return true
    } catch (e) {
      backendReachable.value = false
      error.value = describeFetchFailure(e, base)
      return false
    } finally {
      testing.value = false
    }
  }

  async function loadLlmSettings(): Promise<void> {
    error.value = null
    success.value = null

    await ensureAgentConnected()

    if (!persistManualBackendUrl() && !effectiveHttpBase.value) {
      error.value =
        'Agent backend URL is missing. Start the backend in IDEA, or enter http://127.0.0.1:PORT below.'
      return
    }

    const base = effectiveHttpBase.value
    if (!base) {
      error.value = 'Enter a valid Agent backend URL.'
      return
    }

    loading.value = true
    try {
      const response = await fetch(`${base}/api/settings/llm`)
      if (!response.ok) {
        if (response.status === 404) {
          throw new Error(
            'Settings API not found. Restart AgentServerApplication with the latest code.',
          )
        }
        throw new Error(`Failed to load settings (${response.status})`)
      }
      llm.value = (await response.json()) as LlmSettings
      backendReachable.value = true
    } catch (e) {
      backendReachable.value = false
      error.value = describeFetchFailure(e, base)
    } finally {
      loading.value = false
    }
  }

  async function saveLlmSettings(form: LlmSettings, apiKeyInput: string): Promise<boolean> {
    error.value = null
    success.value = null

    if (!persistManualBackendUrl() && !effectiveHttpBase.value) {
      error.value = 'Agent backend URL is missing.'
      return false
    }

    const base = effectiveHttpBase.value
    if (!base) {
      error.value = 'Enter a valid Agent backend URL.'
      return false
    }

    saving.value = true
    try {
      const payload: Partial<LlmSettings> = {
        enabled: form.enabled,
        provider: form.provider,
        baseUrl: form.baseUrl,
        model: form.model,
        temperature: form.temperature,
        maxTokens: form.maxTokens,
        timeoutSeconds: form.timeoutSeconds,
        streamToolTurns: form.streamToolTurns,
        streamAnswerTurns: form.streamAnswerTurns,
      }

      if (apiKeyInput.trim() && apiKeyInput !== API_KEY_MASK) {
        payload.apiKey = apiKeyInput.trim()
      }

      const response = await fetch(`${base}/api/settings/llm`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })

      if (!response.ok) {
        const text = await response.text()
        throw new Error(text || `Failed to save settings (${response.status})`)
      }

      llm.value = (await response.json()) as LlmSettings
      backendReachable.value = true
      success.value = 'Settings saved. LLM config applies immediately on the backend.'
      return true
    } catch (e) {
      backendReachable.value = false
      error.value = describeFetchFailure(e, base)
      return false
    } finally {
      saving.value = false
    }
  }

  async function loadMcpSettings(): Promise<McpSettings | null> {
    error.value = null
    success.value = null
    await ensureAgentConnected()
    if (!persistManualBackendUrl() && !effectiveHttpBase.value) {
      error.value = 'Agent backend URL is missing.'
      return null
    }
    const base = effectiveHttpBase.value
    if (!base) return null

    loading.value = true
    try {
      const response = await fetch(`${base}/api/settings/mcp`)
      if (!response.ok) throw new Error(`Failed to load MCP settings (${response.status})`)
      mcp.value = (await response.json()) as McpSettings
      backendReachable.value = true
      return mcp.value
    } catch (e) {
      backendReachable.value = false
      error.value = describeFetchFailure(e, base)
      return null
    } finally {
      loading.value = false
    }
  }

  async function saveMcpSettings(form: McpSettings): Promise<boolean> {
    error.value = null
    success.value = null
    const base = effectiveHttpBase.value
    if (!base) {
      error.value = 'Agent backend URL is missing.'
      return false
    }

    saving.value = true
    try {
      const response = await fetch(`${base}/api/settings/mcp`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(form),
      })
      if (!response.ok) {
        const text = await response.text()
        throw new Error(text || `Failed to save MCP settings (${response.status})`)
      }
      mcp.value = (await response.json()) as McpSettings
      backendReachable.value = true
      success.value = 'MCP 配置已保存，工具列表将在下次 Agent 请求时刷新。'
      return true
    } catch (e) {
      backendReachable.value = false
      error.value = describeFetchFailure(e, base)
      return false
    } finally {
      saving.value = false
    }
  }

  async function testMcpServer(serverName: string): Promise<McpTestResult | null> {
    error.value = null
    const base = effectiveHttpBase.value
    if (!base) return null

    testing.value = true
    try {
      const response = await fetch(`${base}/api/settings/mcp/test/${encodeURIComponent(serverName)}`, {
        method: 'POST',
      })
      if (!response.ok) throw new Error(`Test failed (${response.status})`)
      return (await response.json()) as McpTestResult
    } catch (e) {
      error.value = describeFetchFailure(e, base)
      return null
    } finally {
      testing.value = false
    }
  }

  async function loadAvailableModels(): Promise<LlmModelsResponse | null> {
    error.value = null
    const base = effectiveHttpBase.value
    if (!base) {
      error.value = 'Agent backend URL is missing.'
      return null
    }

    loading.value = true
    try {
      const response = await fetch(`${base}/api/settings/llm/models`)
      if (!response.ok) {
        const text = await response.text()
        throw new Error(text || `Failed to load models (${response.status})`)
      }
      backendReachable.value = true
      return (await response.json()) as LlmModelsResponse
    } catch (e) {
      backendReachable.value = false
      error.value = describeFetchFailure(e, base)
      return null
    } finally {
      loading.value = false
    }
  }

  async function saveDefaultModel(modelId: string): Promise<boolean> {
    await loadLlmSettings()
    if (!llm.value) return false
    return saveLlmSettings({ ...llm.value, model: modelId }, API_KEY_MASK)
  }

  async function loadFeatureSettings(): Promise<FeatureSettings | null> {
    error.value = null
    success.value = null
    await ensureAgentConnected()
    const base = effectiveHttpBase.value
    if (!base) {
      error.value = 'Agent backend URL is missing.'
      return null
    }

    loading.value = true
    try {
      const response = await fetch(`${base}/api/settings/features`)
      if (!response.ok) throw new Error(`Failed to load feature settings (${response.status})`)
      features.value = (await response.json()) as FeatureSettings
      backendReachable.value = true
      return features.value
    } catch (e) {
      backendReachable.value = false
      error.value = describeFetchFailure(e, base)
      return null
    } finally {
      loading.value = false
    }
  }

  async function saveFeatureSettings(form: FeatureSettings): Promise<boolean> {
    error.value = null
    success.value = null
    const base = effectiveHttpBase.value
    if (!base) {
      error.value = 'Agent backend URL is missing.'
      return false
    }

    saving.value = true
    try {
      const response = await fetch(`${base}/api/settings/features`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(form),
      })
      if (!response.ok) {
        const text = await response.text()
        throw new Error(text || `Failed to save feature settings (${response.status})`)
      }
      features.value = (await response.json()) as FeatureSettings
      backendReachable.value = true
      success.value = '扩展引擎配置已保存（MySQL + Redis 缓存）。'
      return true
    } catch (e) {
      backendReachable.value = false
      error.value = describeFetchFailure(e, base)
      return false
    } finally {
      saving.value = false
    }
  }

  async function loadSkillsInfo(workspaceRoot?: string | null): Promise<SkillsInfo | null> {
    const base = effectiveHttpBase.value
    if (!base) return null

    error.value = null
    try {
      const params = workspaceRoot ? `?workspaceRoot=${encodeURIComponent(workspaceRoot)}` : ''
      const response = await fetch(`${base}/api/skills${params}`)
      if (!response.ok) throw new Error(`Failed to load skills (${response.status})`)
      skillsInfo.value = (await response.json()) as SkillsInfo
      return skillsInfo.value
    } catch (e) {
      error.value = describeFetchFailure(e, base)
      return null
    }
  }

  async function installSkill(
    sourcePath: string,
    scope: 'user-ad' | 'user' | 'workspace',
    workspaceRoot?: string | null,
  ): Promise<SkillInstallResult | null> {
    error.value = null
    success.value = null
    const base = effectiveHttpBase.value
    if (!base) {
      error.value = 'Agent backend URL is missing.'
      return null
    }
    saving.value = true
    try {
      const response = await fetch(`${base}/api/skills/install`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sourcePath, scope, workspaceRoot: workspaceRoot ?? undefined }),
      })
      const body = (await response.json()) as SkillInstallResult
      if (!response.ok) {
        throw new Error(body.message || `Install failed (${response.status})`)
      }
      success.value = `Skill ${body.name ?? ''} 已安装到 ${body.installedPath ?? ''}`
      return body
    } catch (e) {
      error.value = describeFetchFailure(e, base)
      return null
    } finally {
      saving.value = false
    }
  }

  async function installCatalogSkill(
    catalogId: string,
    scope: 'user-ad' | 'user' | 'workspace',
    workspaceRoot?: string | null,
  ): Promise<SkillInstallResult | null> {
    error.value = null
    success.value = null
    const base = effectiveHttpBase.value
    if (!base) {
      error.value = 'Agent backend URL is missing.'
      return null
    }
    saving.value = true
    try {
      const response = await fetch(`${base}/api/skills/catalog/install`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ catalogId, scope, workspaceRoot: workspaceRoot ?? undefined }),
      })
      const body = (await response.json()) as SkillInstallResult
      if (!response.ok) {
        throw new Error(body.message || `Install failed (${response.status})`)
      }
      success.value = `Skill ${body.name ?? ''} 已安装`
      return body
    } catch (e) {
      error.value = describeFetchFailure(e, base)
      return null
    } finally {
      saving.value = false
    }
  }

  async function uninstallSkill(
    name: string,
    scope: string,
    workspaceRoot?: string | null,
  ): Promise<boolean> {
    error.value = null
    success.value = null
    const base = effectiveHttpBase.value
    if (!base) {
      error.value = 'Agent backend URL is missing.'
      return false
    }
    saving.value = true
    try {
      const params = new URLSearchParams({ name, scope })
      if (workspaceRoot) params.set('workspaceRoot', workspaceRoot)
      const response = await fetch(`${base}/api/skills?${params}`, { method: 'DELETE' })
      const body = (await response.json()) as SkillInstallResult
      if (!response.ok) {
        throw new Error(body.message || `Uninstall failed (${response.status})`)
      }
      success.value = `已卸载 Skill ${name}`
      return true
    } catch (e) {
      error.value = describeFetchFailure(e, base)
      return false
    } finally {
      saving.value = false
    }
  }

  async function loadMarketplaceInfo(workspaceRoot?: string | null): Promise<MarketplaceInfo | null> {
    const base = effectiveHttpBase.value
    if (!base) {
      marketplaceInfo.value = {
        marketplace: 'claude-plugins-official',
        source: '',
        plugins: [],
        loadError: 'Agent backend URL is missing.',
      }
      return marketplaceInfo.value
    }
    try {
      const params = workspaceRoot ? `?workspaceRoot=${encodeURIComponent(workspaceRoot)}` : ''
      const response = await fetch(`${base}/api/plugins/marketplace${params}`)
      if (!response.ok) throw new Error(`Failed to load marketplace (${response.status})`)
      marketplaceInfo.value = (await response.json()) as MarketplaceInfo
      return marketplaceInfo.value
    } catch (e) {
      marketplaceInfo.value = {
        marketplace: 'claude-plugins-official',
        source: '',
        plugins: [],
        loadError: describeFetchFailure(e, base),
      }
      return marketplaceInfo.value
    }
  }

  async function installMarketplacePlugin(
    name: string,
    scope: 'user' | 'workspace' = 'user',
    workspaceRoot?: string | null,
        installMethod = 'github',
  ): Promise<PluginInstallResult | null> {
    error.value = null
    success.value = null
    const base = effectiveHttpBase.value
    if (!base) {
      error.value = 'Agent backend URL is missing.'
      return null
    }
    saving.value = true
    try {
      const response = await fetch(`${base}/api/plugins/marketplace/install`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name,
          scope,
          workspaceRoot: workspaceRoot ?? undefined,
          installMethod,
        }),
      })
      const body = (await response.json()) as PluginInstallResult
      if (!response.ok) {
        throw new Error(body.message || `Install failed (${response.status})`)
      }
      success.value = `插件 ${body.name ?? name} 已安装（含 skills）`
      return body
    } catch (e) {
      error.value = describeFetchFailure(e, base)
      return null
    } finally {
      saving.value = false
    }
  }

  async function loadExtensionInfo(workspaceRoot?: string | null): Promise<ExtensionInfo | null> {
    const base = effectiveHttpBase.value
    if (!base) return null

    loading.value = true
    try {
      const params = workspaceRoot ? `?workspaceRoot=${encodeURIComponent(workspaceRoot)}` : ''
      const response = await fetch(`${base}/api/extensions${params}`)
      if (!response.ok) throw new Error(`Failed to load extensions (${response.status})`)
      extensions.value = (await response.json()) as ExtensionInfo
      return extensions.value
    } catch (e) {
      error.value = describeFetchFailure(e, base)
      return null
    } finally {
      loading.value = false
    }
  }

  async function installPlugin(
    sourcePath: string,
    scope: 'user' | 'workspace',
    workspaceRoot?: string | null,
  ): Promise<PluginInstallResult | null> {
    error.value = null
    success.value = null
    const base = effectiveHttpBase.value
    if (!base) {
      error.value = 'Agent backend URL is missing.'
      return null
    }
    saving.value = true
    try {
      const response = await fetch(`${base}/api/extensions/plugins/install`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sourcePath, scope, workspaceRoot: workspaceRoot ?? undefined }),
      })
      const body = (await response.json()) as PluginInstallResult
      if (!response.ok) {
        throw new Error(body.message || `Install failed (${response.status})`)
      }
      success.value = `插件 ${body.name ?? ''} 已安装到 ${body.installedPath ?? ''}`
      return body
    } catch (e) {
      error.value = describeFetchFailure(e, base)
      return null
    } finally {
      saving.value = false
    }
  }

  async function uninstallPlugin(
    name: string,
    scope: 'user' | 'workspace',
    workspaceRoot?: string | null,
  ): Promise<boolean> {
    error.value = null
    success.value = null
    const base = effectiveHttpBase.value
    if (!base) {
      error.value = 'Agent backend URL is missing.'
      return false
    }
    saving.value = true
    try {
      const params = new URLSearchParams({ name, scope })
      if (workspaceRoot) params.set('workspaceRoot', workspaceRoot)
      const response = await fetch(`${base}/api/extensions/plugins?${params}`, { method: 'DELETE' })
      const body = (await response.json()) as PluginInstallResult
      if (!response.ok) {
        throw new Error(body.message || `Uninstall failed (${response.status})`)
      }
      success.value = `已卸载插件 ${name}`
      return true
    } catch (e) {
      error.value = describeFetchFailure(e, base)
      return false
    } finally {
      saving.value = false
    }
  }

  async function loadHooksSettings(): Promise<HooksSettings | null> {
    const base = effectiveHttpBase.value
    if (!base) return null
    loading.value = true
    try {
      const response = await fetch(`${base}/api/settings/hooks`)
      if (!response.ok) throw new Error(`Failed to load hooks (${response.status})`)
      return (await response.json()) as HooksSettings
    } catch (e) {
      error.value = describeFetchFailure(e, base)
      return null
    } finally {
      loading.value = false
    }
  }

  async function saveHooksSettings(hooks: HooksSettings['hooks']): Promise<boolean> {
    error.value = null
    success.value = null
    const base = effectiveHttpBase.value
    if (!base) {
      error.value = 'Agent backend URL is missing.'
      return false
    }
    saving.value = true
    try {
      const response = await fetch(`${base}/api/settings/hooks`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ hooks }),
      })
      if (!response.ok) {
        const text = await response.text()
        throw new Error(text || `Failed to save hooks (${response.status})`)
      }
      success.value = '全局 Hooks 已保存（MySQL + Redis 缓存）。'
      return true
    } catch (e) {
      error.value = describeFetchFailure(e, base)
      return false
    } finally {
      saving.value = false
    }
  }

  return {
    llm,
    mcp,
    features,
    extensions,
    skillsInfo,
    marketplaceInfo,
    loading,
    saving,
    testing,
    error,
    success,
    manualBackendUrl,
    httpBaseFromAgent,
    effectiveHttpBase,
    backendReachable,
    setManualBackendUrl,
    persistManualBackendUrl,
    syncBackendFromAgent,
    ensureAgentConnected,
    testBackendConnection,
    loadLlmSettings,
    saveLlmSettings,
    loadMcpSettings,
    saveMcpSettings,
    testMcpServer,
    loadAvailableModels,
    saveDefaultModel,
    loadFeatureSettings,
    saveFeatureSettings,
    loadExtensionInfo,
    loadSkillsInfo,
    installSkill,
    installCatalogSkill,
    uninstallSkill,
    loadMarketplaceInfo,
    installMarketplacePlugin,
    installPlugin,
    uninstallPlugin,
    loadHooksSettings,
    saveHooksSettings,
  }
})
