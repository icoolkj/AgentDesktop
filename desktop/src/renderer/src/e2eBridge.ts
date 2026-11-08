import type { Pinia } from 'pinia'
import type { PermissionMode } from '@agent-desktop/shared-types'

import type { ColorThemeId } from '@renderer/modules/appearance/types'
import { useAgentSettingsStore } from '@renderer/stores/agentSettingsStore'
import { useAgentStore } from '@renderer/stores/agentStore'
import { useAppearanceStore } from '@renderer/stores/appearanceStore'
import { useChatStore } from '@renderer/stores/chatStore'
import { useSessionChangesStore } from '@renderer/stores/sessionChangesStore'
import { useWorkspaceStore } from '@renderer/stores/workspaceStore'
import { buildPersistAttachmentInputs } from '@renderer/utils/persistUserAttachments'

export interface E2eApi {
  openWorkspace: (path: string) => Promise<void>
  recordPendingChange: (input: {
    path: string
    content: string
    previousContent?: string
    changeKind?: 'modified' | 'created' | 'deleted'
  }) => Promise<void>
  acceptAllPending: () => Promise<void>
  setPermissionMode: (mode: PermissionMode) => void
  /** Wait until the agent WebSocket reaches `connected` (sendMessage returns early otherwise). */
  waitForAgentConnected: (timeoutMs?: number) => Promise<void>
  setColorTheme: (id: ColorThemeId) => void
  getTerminalBg: () => string
  isTerminalVisible: () => boolean
  getTerminalText: () => string
  /** Persist user message + attachment to JSONL and reload UI (A1 transcript replay). */
  replayPersistedAttachment: (input: {
    text: string
    imagePath: string
    imageName?: string
  }) => Promise<void>
}

declare global {
  interface Window {
    __e2e?: E2eApi
  }
}

export function installE2eBridge(pinia: Pinia): void {
  window.__e2e = {
    async openWorkspace(path: string) {
      await useWorkspaceStore(pinia).openWorkspaceAt(path, { persist: false })
    },
    async recordPendingChange(input) {
      const root = useWorkspaceStore(pinia).rootPath ?? input.path
      useSessionChangesStore(pinia).recordChange({
        path: input.path,
        workspaceRoot: root,
        changeKind: input.changeKind ?? 'modified',
        content: input.content,
        previousContent: input.previousContent,
      })
    },
    async acceptAllPending() {
      await useSessionChangesStore(pinia).acceptAll()
    },
    setPermissionMode(mode: PermissionMode) {
      useAgentSettingsStore(pinia).setPermissionMode(mode)
    },
    async waitForAgentConnected(timeoutMs = 15_000) {
      const agent = useAgentStore(pinia)
      const deadline = Date.now() + timeoutMs
      while (agent.status !== 'connected') {
        if (Date.now() > deadline) {
          throw new Error(
            `Agent not connected within ${timeoutMs}ms: status=${agent.status} error=${agent.error ?? 'none'} wsUrl=${agent.wsUrl ?? 'none'}`,
          )
        }
        await new Promise(resolve => setTimeout(resolve, 100))
      }
    },
    setColorTheme(id: ColorThemeId) {
      useAppearanceStore(pinia).setColorTheme(id)
    },
    getTerminalBg() {
      return getComputedStyle(document.documentElement).getPropertyValue('--ide-terminal-bg').trim()
    },
    isTerminalVisible() {
      return Boolean(document.querySelector('[data-testid="terminal-panel"]'))
    },
    getTerminalText() {
      return document.querySelector('[data-testid="terminal-panel"]')?.textContent ?? ''
    },
    async replayPersistedAttachment(input) {
      const chat = useChatStore(pinia)
      const ws = useWorkspaceStore(pinia).rootPath ?? null
      const sid = chat.sessionId
      const { dataUrl, mimeType } = await window.workspaceApi.readFileDataUrl(input.imagePath)
      const base64 = dataUrl.includes(',') ? dataUrl.split(',')[1]! : dataUrl
      const name = input.imageName ?? input.imagePath.replace(/\\/g, '/').split('/').pop() ?? 'image.png'
      const display = [{ id: crypto.randomUUID(), kind: 'image' as const, name, filePath: input.imagePath }]
      const uploads = [
        {
          kind: 'image' as const,
          path: input.imagePath,
          label: name,
          mimeType,
          base64,
        },
      ]
      const persist = buildPersistAttachmentInputs(display, uploads)
      await window.transcriptApi.setTitle(sid, ws, input.text.slice(0, 80) || name)
      await window.transcriptApi.appendUser(sid, ws, input.text, persist)
      await window.transcriptApi.flush(sid, ws)
      await chat.syncCurrentSession(true)
    },
  }
}
