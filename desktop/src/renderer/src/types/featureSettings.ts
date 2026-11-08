export interface FeatureSettings {
  subagentsEnabled: boolean
  maxSubagentDepth: number
  maxSubagentTurns: number
  webFetchEnabled: boolean
  webFetchMaxBytes: number
  webSearchEnabled: boolean
  webSearchProvider: string
  bashSandboxEnabled: boolean
  bashSandboxMode: 'strict' | 'warn' | 'off'
  storage?: string
}

export interface SlashCommandInfo {
  name: string
  description: string
  source: string
}

export interface PluginInfo {
  name: string
  version: string
  description: string
  root: string
}

export interface SkillInfo {
  name: string
  description: string
  source: string
  sourceLabel?: string
  userInvocable: boolean
  modelInvocable: boolean
  installPath?: string | null
  managed?: boolean
  managedScope?: string
  forkContext?: boolean
  allowedTools?: string[]
  conditional?: boolean
}

export interface MarketplacePluginInfo {
  name: string
  description: string
  version: string
  source: string
  installed?: boolean | string
  installPath?: string | null
}

export interface MarketplaceInfo {
  marketplace: string
  source: string
  installMethods?: string[]
  defaultInstallMethod?: string
  installedCount?: number
  loadError?: string | null
  pluginDirs?: {
    user: string
    workspace: string | null
  }
  plugins: MarketplacePluginInfo[]
}

export interface SkillCatalogEntry {
  id: string
  name: string
  description: string
  installed?: boolean
}

export interface SkillsInfo {
  skills: SkillInfo[]
  skillsLoaded: boolean
  catalog: SkillCatalogEntry[]
  skillDirs: {
    userAd: string
    user: string
    workspace: string | null
  }
  usage?: {
    invokeTool: string
    slash: string
    listing: string
  }
}

export interface SkillInstallResult {
  ok: boolean
  message: string
  name?: string
  installedPath?: string
}

export interface ExtensionInfo {
  slashCommands: SlashCommandInfo[]
  plugins: PluginInfo[]
  skills: SkillInfo[]
  skillsLoaded: boolean
  hookCount: number
  features: {
    subagentsEnabled: boolean
    webFetchEnabled: boolean
    webSearchEnabled: boolean
    bashSandboxEnabled: boolean
    bashSandboxMode: string
  }
  persistence?: {
    features: string
    mcp: string
    skills: string
    slashCommands: string
    plugins?: string
  }
  pluginDirs?: {
    user: string
    workspace: string | null
  }
}

export interface HooksSettings {
  hooks: HookDefinitionWire[]
  storage?: string
}

export interface HookDefinitionWire {
  event: string
  matcher: string
  actions: Array<{ type: string; command: string; timeoutSeconds: number }>
}

export interface PluginInstallResult {
  ok: boolean
  message: string
  name?: string
  version?: string
  installedPath?: string
}

export function defaultFeatureSettings(): FeatureSettings {
  return {
    subagentsEnabled: true,
    maxSubagentDepth: 2,
    maxSubagentTurns: 8,
    webFetchEnabled: true,
    webFetchMaxBytes: 512 * 1024,
    webSearchEnabled: true,
    webSearchProvider: 'duckduckgo',
    bashSandboxEnabled: true,
    bashSandboxMode: 'strict',
  }
}
