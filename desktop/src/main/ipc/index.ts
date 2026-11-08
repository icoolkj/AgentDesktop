import { setupAgentIpcHandlers, notifyAgentDisconnected } from './agentIpcHandlers'
import { setupAppIpcHandlers } from './appIpcHandlers'
import { setupSecretsIpcHandlers } from './secretsIpcHandlers'
import { setupAiTrackingIpcHandlers, setupUserStateIpcHandlers } from './aiTrackingIpcHandlers'
import { setupTranscriptIpcHandlers } from './transcriptIpcHandlers'
import { registerTerminalIpc } from './terminalIpcHandlers'
import { setupWorkspaceIpcHandlers } from './workspaceIpcHandlers'

export function setupIpcHandlers(): void {
  setupAppIpcHandlers()
  setupSecretsIpcHandlers()
  setupAiTrackingIpcHandlers()
  setupUserStateIpcHandlers()
  setupAgentIpcHandlers()
  setupWorkspaceIpcHandlers()
  setupTranscriptIpcHandlers()
  registerTerminalIpc()
}

export { notifyAgentDisconnected }
