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

const canCommit = computed(
  () =>
    gitStore.stagedChanges.length > 0 &&
    gitStore.commitMessage.trim().length > 0 &&
    !gitStore.operating,
)

function onCommit() {
  void gitStore.commit()
}

function onKeydown(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key === 'Enter' && canCommit.value) {
    event.preventDefault()
    onCommit()
  }
}

function unstageOne(relPath: string) {
  void gitStore.unstage([resolveAbsPath(relPath, gitRoot.value)])
}

function openChange(relPath: string) {
  void editor.openGitDiff(resolveAbsPath(relPath, gitRoot.value))
}

function goChangesTab() {
  gitStore.activeView = 'changes'
}

function onStageAll() {
  void gitStore.stageAll()
}

function onUnstageAll() {
  void gitStore.unstageAll()
}
</script>

<template>
  <div class="git-commit-view">
    <label class="git-commit-view__label" for="git-commit-message">提交说明</label>
    <textarea
      id="git-commit-message"
      v-model="gitStore.commitMessage"
      class="git-commit-view__input"
      rows="4"
      placeholder="描述本次提交（Ctrl+Enter 提交）"
      @keydown="onKeydown"
    />

    <button
      type="button"
      class="git-commit-view__submit"
      :disabled="!canCommit"
      @click="onCommit"
    >
      {{ gitStore.operating ? '提交中…' : '提交' }}
    </button>

    <section class="git-commit-view__staged">
      <div class="git-commit-view__staged-header">
        <h3>将提交的文件 {{ gitStore.stagedChanges.length ? `(${gitStore.stagedChanges.length})` : '' }}</h3>
        <div class="git-commit-view__staged-actions">
          <button
            v-if="gitStore.unstagedChanges.length && !gitStore.stagedChanges.length"
            type="button"
            class="git-commit-view__link"
            :disabled="gitStore.operating"
            @click="goChangesTab"
          >
            去变更页暂存
          </button>
          <button
            v-else-if="gitStore.unstagedChanges.length"
            type="button"
            class="git-commit-view__link"
            :disabled="gitStore.operating"
            @click="onStageAll"
          >
            全部暂存
          </button>
          <button
            v-if="gitStore.stagedChanges.length"
            type="button"
            class="git-commit-view__link"
            :disabled="gitStore.operating"
            @click="onUnstageAll"
          >
            全部取消
          </button>
        </div>
      </div>
      <p v-if="!gitStore.stagedChanges.length" class="git-commit-view__empty">
        <template v-if="gitStore.unstagedChanges.length">
          暂无已暂存文件，「变更」页有 {{ gitStore.unstagedChanges.length }} 个待暂存更改
        </template>
        <template v-else>暂无</template>
      </p>
      <GitChangeRow
        v-for="change in gitStore.stagedChanges"
        :key="change.path"
        :change="change"
        :git-root="gitRoot"
        compact
        show-unstage
        @open="openChange"
        @unstage="unstageOne"
      />
    </section>
  </div>
</template>

<style scoped>
.git-commit-view {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.git-commit-view__label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--ide-muted);
}

.git-commit-view__input {
  width: 100%;
  min-height: 72px;
  padding: 8px;
  border: 1px solid var(--ide-border);
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.2);
  color: var(--ide-fg);
  font-size: 12px;
  font-family: inherit;
  resize: vertical;
}

.git-commit-view__input:focus {
  outline: none;
  border-color: var(--ide-accent);
}

.git-commit-view__submit {
  width: 100%;
  padding: 8px 12px;
  border: none;
  border-radius: 4px;
  background: var(--ide-accent);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.git-commit-view__submit:hover:not(:disabled) {
  filter: brightness(1.08);
}

.git-commit-view__submit:disabled {
  opacity: 0.45;
  cursor: default;
  background: rgba(255, 255, 255, 0.12);
  color: var(--ide-muted);
}

.git-commit-view__staged-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.git-commit-view__staged {
  margin-top: 6px;
}

.git-commit-view__staged-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.git-commit-view__staged-header h3 {
  margin: 0;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--ide-muted);
}

.git-commit-view__link {
  padding: 0;
  border: none;
  background: transparent;
  color: var(--ide-accent);
  font-size: 11px;
  cursor: pointer;
}

.git-commit-view__link:hover:not(:disabled) {
  text-decoration: underline;
}

.git-commit-view__link:disabled {
  opacity: 0.45;
  cursor: default;
}

.git-commit-view__empty {
  margin: 0;
  padding: 8px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.03);
  font-size: 11px;
  color: var(--ide-muted);
  line-height: 1.5;
}
</style>
