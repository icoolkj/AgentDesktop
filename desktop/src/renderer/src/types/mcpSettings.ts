export interface McpToolInfo {
  serverName: string
  name: string
  description: string
}

export interface McpServerEntry {
  name: string
  transport: 'stdio' | 'http' | 'sse'
  command: string
  url: string
  args: string[]
  env: Record<string, string>
  headers: Record<string, string>
  cwd: string
  enabled: boolean
  connected?: boolean
  toolCount?: number
  error?: string | null
}

export interface McpSettings {
  configPath: string
  servers: McpServerEntry[]
}

export interface McpTestResult {
  ok: boolean
  message: string
  tools: McpToolInfo[]
}

export function emptyMcpServer(name = ''): McpServerEntry {
  return {
    name,
    transport: 'stdio',
    command: '',
    url: '',
    args: [],
    env: {},
    headers: {},
    cwd: '',
    enabled: true,
    connected: false,
    toolCount: 0,
    error: null,
  }
}

export function isRemoteTransport(transport: string): boolean {
  return transport === 'http' || transport === 'sse'
}
