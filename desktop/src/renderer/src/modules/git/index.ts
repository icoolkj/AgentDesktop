/** Git feature module — renderer store + sidebar helpers. */
export { useGitStore, type GitRefreshScope } from '@renderer/stores/gitStore'
export {
  badgeForKind,
  colorClassForKind,
  decorationKindFromStatus,
  type GitDecorationKind,
  type GitTreeDecoration,
} from '@renderer/utils/gitTreeDecorations'
export { isLikelyIgnoredPath } from '@renderer/utils/gitPathHeuristics'
export { sortFileChanges, type GitPanelView } from '@renderer/utils/gitSidebarUtils'
