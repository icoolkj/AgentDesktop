/** Workspace / explorer feature module. */
export { useWorkspaceStore } from '@renderer/stores/workspaceStore'
export { useSidebarStore } from '@renderer/stores/sidebarStore'
export { useExplorerPromptStore } from '@renderer/stores/explorerPromptStore'
export {
  listRecentWorkspaces,
  workspaceDisplayName,
  type RecentWorkspace,
} from '@renderer/utils/workspacePersistence'
export { normalizeWorkspacePath, sessionBelongsToWorkspace } from '@renderer/utils/workspaceSession'
