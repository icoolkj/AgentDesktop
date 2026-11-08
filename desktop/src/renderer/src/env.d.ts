/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_E2E?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}

import type { AgentApi } from '../../preload/api/agentApi'
import type { DesktopApi } from '../../preload/api/desktopApi'
import type { TerminalApi } from '../../preload/api/terminalApi'
import type { TranscriptApi } from '../../preload/api/transcriptApi'
import type { WorkspaceApi } from '../../preload/api/workspaceApi'
import type { SecretsApi } from '../../preload/api/secretsApi'

declare global {
  interface Window {
    agentApi: AgentApi
    desktopApi: DesktopApi
    terminalApi: TerminalApi
    workspaceApi: WorkspaceApi
    transcriptApi: TranscriptApi
    secretsApi: SecretsApi
    desktopEnv: { platform: NodeJS.Platform }
  }
}

export {}
