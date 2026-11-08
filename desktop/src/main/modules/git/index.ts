/** Git feature module — main process implementations. */
export { loadGitInfo, type GitInfoDto, type GitInfoLoadScope } from '../../utils/gitInfo'
export {
  gitStage,
  gitStageAll,
  gitUnstage,
  gitUnstageAll,
  gitCommit,
  gitFetch,
  gitPull,
  gitPush,
  gitDiscard,
  type GitDiscardScope,
} from '../../utils/gitOperations'
export { loadGitLog, type GitLogEntryDto, type GitLogResultDto } from '../../utils/gitLog'
export { loadGitFileBaseline, type GitFileBaselineDto } from '../../utils/gitFileBaseline'
export { resolveGitRootFromWorkspace, runGit, type GitOpResult } from '../../utils/gitRunner'
