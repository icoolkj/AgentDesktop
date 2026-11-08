<script setup lang="ts">
import { computed } from 'vue'
import { storeToRefs } from 'pinia'

import GitChangeRow from '@renderer/components/sidebar/git/GitChangeRow.vue'
import { useEditorStore } from '@renderer/stores/editorStore'
import { useGitStore } from '@renderer/stores/gitStore'
import { useWorkspaceStore } from '@renderer/stores/workspaceStore'
import { resolveAbsPath } from '@renderer/utils/gitSidebarUtils'

const gitStore = useGitStore()
const editor = useEditorStore()
const workspace = useWorkspaceStore()
const { rootPath } = storeToRefs(workspace)

const gitRoot = computed(() => gitStore.info?.gitRoot ?? rootPath.value ?? '')

function openChange(relPath: string) {
  void editor.openGitDiff(resolveAbsPath(relPath, gitRoot.value))
}

function stageOne(relPath: string) {
  void gitStore.stage([resolveAbsPath(relPath, gitRoot.value)])
}

function unstageOne(relPath: string) {
  void gitStore.unstage([resolveAbsPath(relPath, gitRoot.value)])
}

function discardStaged(relPath: string) {
  if (
    !window.confirm(
      '确定放弃此文件的所有更改（含暂存区），恢复为上次提交的版本？\n\n此操作不可撤销。',
    )
  ) {
    return
  }
  void gitStore.discard([resolveAbsPath(relPath, gitRoot.value)], 'head')
}

function discardUnstaged(relPath: string) {
  if (!window.confirm('确定放弃此文件在工作区的未暂存更改？\n\n已暂存的内容不会受影响。')) {
    return
  }
  void gitStore.discard([resolveAbsPath(relPath, gitRoot.value)], 'worktree')
}
</script>

<template>
  <div class="git-changes-view">
    <section class="git-changes-view__section">
      <div class="git-changes-view__header">
        <h3>已暂存 {{ gitStore.stagedChanges.length ? `(${gitStore.stagedChanges.length})` : '' }}</h3>
        <button
          type="button"
          class="git-changes-view__link"
          :disabled="gitStore.operating || !gitStore.stagedChanges.length"
          @click="gitStore.unstageAll()"
        >
          全部取消
        </button>
      </div>
      <p v-if="!gitStore.stagedChanges.length" class="git-changes-view__empty">暂无</p>
      <GitChangeRow
        v-for="change in gitStore.stagedChanges"
        :key="`staged:${change.path}`"
        :change="change"
        :git-root="gitRoot"
        show-unstage
        show-discard
        discard-title="放弃所有更改，恢复为上次提交版本"
        @open="openChange"
        @unstage="unstageOne"
        @discard="discardStaged"
      />
    </section>

    <section class="git-changes-view__section">
      <div class="git-changes-view__header">
        <h3>更改 {{ gitStore.unstagedChanges.length ? `(${gitStore.unstagedChanges.length})` : '' }}</h3>
        <button
          type="button"
          class="git-changes-view__link"
          :disabled="gitStore.operating || !gitStore.unstagedChanges.length"
          @click="gitStore.stageAll()"
        >
          全部暂存
        </button>
      </div>
      <p v-if="!gitStore.unstagedChanges.length" class="git-changes-view__empty">暂无</p>
      <GitChangeRow
        v-for="change in gitStore.unstagedChanges"
        :key="`unstaged:${change.path}`"
        :change="change"
        :git-root="gitRoot"
        show-stage
        show-discard
        discard-title="放弃工作区未暂存的更改"
        @open="openChange"
        @stage="stageOne"
        @discard="discardUnstaged"
      />
    </section>
  </div>
</template>

<style scoped>
.git-changes-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.git-changes-view__section {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.git-changes-view__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 2px;
}

.git-changes-view__header h3 {
  margin: 0;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--ide-muted);
}

.git-changes-view__link {
  padding: 0;
  border: none;
  background: transparent;
  color: var(--ide-accent);
  font-size: 11px;
  cursor: pointer;
}

.git-changes-view__link:hover:not(:disabled) {
  text-decoration: underline;
}

.git-changes-view__link:disabled {
  opacity: 0.45;
  cursor: default;
}

.git-changes-view__empty {
  margin: 0;
  padding: 6px 8px;
  font-size: 11px;
  color: var(--ide-muted);
}
</style>
