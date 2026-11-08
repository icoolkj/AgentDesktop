/**
 * Permission mode policy — aligned with Claude Code `PermissionMode` / `filesystem.ts`.
 *
 * AD UI name     → CC mode
 * ---------------------------
 * ask  (Ask)     → default      (prompt before write; no acceptEdits auto-allow)
 * auto (Agent)   → acceptEdits  (auto-allow writes in cwd; no post-hoc review gate)
 * plan (Plan)    → plan         (write tools blocked)
 *
 * @see claude_code_src-master/src/utils/permissions/PermissionMode.ts
 * @see claude_code_src-master/src/utils/permissions/filesystem.ts checkWritePermissionForTool
 */
import type { PermissionMode } from '@agent-desktop/shared-types'

export type CcPermissionMode = 'default' | 'acceptEdits' | 'plan' | 'bypassPermissions'

export function toCcPermissionMode(mode: PermissionMode): CcPermissionMode {
  if (mode === 'auto') return 'acceptEdits'
  if (mode === 'plan') return 'plan'
  return 'default'
}

/** CC has no post-turn Review queue — only fileHistory + /rewind. */
export function usesPostTurnReviewQueue(mode: PermissionMode): boolean {
  return mode === 'ask'
}

/** CC acceptEdits: writes land on disk; no mandatory accept step before next message. */
export function blocksSendOnPendingReview(mode: PermissionMode): boolean {
  return mode === 'ask'
}

/** After a successful agent turn, auto-clear pending (acceptEdits parity). */
export function autoAcknowledgeWritesOnTurnComplete(mode: PermissionMode): boolean {
  return mode === 'auto'
}

export function reviewBarTitle(mode: PermissionMode, fileCount: number): string {
  if (mode === 'auto') {
    return `Agent 已写入 ${fileCount} 个文件`
  }
  if (mode === 'ask') {
    return `${fileCount} 个文件待确认`
  }
  return `${fileCount} 个文件待处理`
}

export function reviewBarHint(mode: PermissionMode): string {
  if (mode === 'auto') {
    return '与 CC acceptEdits 一致：已写盘 · 接受=确认 · 拒绝=回滚 · 也可用 /rewind'
  }
  if (mode === 'ask') {
    return '写前已逐条授权 · 批次确认后可继续对话（Cursor 式增强）'
  }
  return '请处理后再发送'
}

export function sendBlockedMessage(mode: PermissionMode): string {
  if (mode === 'ask') {
    return 'Ask 模式：请在输入框上方「全部接受」或「全部拒绝」后再发送'
  }
  return '有待确认的文件修改'
}
