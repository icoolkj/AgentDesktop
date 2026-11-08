<script setup lang="ts">
import { computed } from 'vue'

import { useGitStore } from '@renderer/stores/gitStore'
import { defaultPushTarget } from '@renderer/utils/gitSidebarUtils'

const gitStore = useGitStore()

const syncStatus = computed(() => gitStore.info?.syncStatus)
const branch = computed(() => gitStore.info?.branch ?? '—')
const remotes = computed(() => gitStore.info?.remotes ?? [])

const pushTarget = computed(() =>
  defaultPushTarget(gitStore.info?.branch, syncStatus.value?.trackingBranch),
)

const canPush = computed(() => (syncStatus.value?.ahead ?? 0) > 0)

const syncHint = computed(() => {
  const ahead = syncStatus.value?.ahead ?? 0
  const behind = syncStatus.value?.behind ?? 0
  if (!syncStatus.value?.trackingBranch) return '未设置 upstream'
  if (ahead === 0 && behind === 0) return '已同步'
  const parts: string[] = []
  if (ahead > 0) parts.push(`↑${ahead}`)
  if (behind > 0) parts.push(`↓${behind}`)
  return parts.join(' ')
})

function onFetch() {
  void gitStore.fetchRemote(remotes.value[0]?.name)
}

function onPull() {
  void gitStore.pull(pushTarget.value.remote, pushTarget.value.branch)
}

function onPush() {
  void gitStore.push(pushTarget.value.remote, pushTarget.value.branch)
}
</script>

<template>
  <div class="git-sync-view">
    <section class="git-sync-view__card">
      <div class="git-sync-view__branch-row">
        <span class="git-sync-view__label">分支</span>
        <span class="git-sync-view__branch">{{ branch }}</span>
      </div>
      <div v-if="syncStatus?.trackingBranch" class="git-sync-view__branch-row">
        <span class="git-sync-view__label">跟踪</span>
        <span class="git-sync-view__tracking">{{ syncStatus.trackingBranch }}</span>
      </div>
      <p class="git-sync-view__hint">{{ syncHint }}</p>
    </section>

    <section class="git-sync-view__actions">
      <button
        type="button"
        class="git-sync-view__btn"
        title="Fetch — 获取远程更新（不合并到当前分支）"
        :disabled="gitStore.operating || !remotes.length"
        @click="onFetch"
      >
        Fetch
      </button>
      <button
        type="button"
        class="git-sync-view__btn"
        title="Pull — 拉取并合并到当前分支"
        :disabled="gitStore.operating || !remotes.length"
        @click="onPull"
      >
        Pull
      </button>
      <button
        type="button"
        class="git-sync-view__btn"
        :class="{ 'git-sync-view__btn--emphasis': canPush }"
        title="Push — 推送到远程"
        :disabled="gitStore.operating || !remotes.length"
        @click="onPush"
      >
        Push
      </button>
    </section>

    <section class="git-sync-view__remotes">
      <h3>远程</h3>
      <p v-if="!remotes.length" class="git-sync-view__empty">无 remote</p>
      <div v-for="remote in remotes" :key="remote.name" class="git-sync-view__remote">
        <span class="git-sync-view__remote-name">{{ remote.name }}</span>
        <code class="git-sync-view__remote-url" :title="remote.fetchUrl">{{ remote.fetchUrl }}</code>
      </div>
    </section>
  </div>
</template>

<style scoped>
.git-sync-view {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.git-sync-view__card {
  padding: 10px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--ide-border);
}

.git-sync-view__branch-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.git-sync-view__label {
  min-width: 36px;
  font-size: 11px;
  color: var(--ide-muted);
}

.git-sync-view__branch,
.git-sync-view__tracking {
  font-size: 12px;
  color: var(--ide-fg);
  font-family: ui-monospace, monospace;
}

.git-sync-view__hint {
  margin: 8px 0 0;
  font-size: 11px;
  color: var(--ide-muted);
}

.git-sync-view__actions {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
}

.git-sync-view__btn {
  padding: 8px 6px;
  border: 1px solid var(--ide-border);
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.04);
  color: var(--ide-fg);
  font-size: 12px;
  cursor: pointer;
}

.git-sync-view__btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.08);
}

.git-sync-view__btn--emphasis {
  border-color: rgba(0, 122, 204, 0.45);
  color: #9cdcfe;
}

.git-sync-view__btn:disabled {
  opacity: 0.45;
  cursor: default;
}

.git-sync-view__remotes h3 {
  margin: 0 0 6px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--ide-muted);
}

.git-sync-view__remote {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: 8px;
}

.git-sync-view__remote-name {
  font-size: 11px;
  font-weight: 600;
  color: var(--ide-accent);
}

.git-sync-view__remote-url {
  font-size: 10px;
  color: var(--ide-muted);
  word-break: break-all;
}

.git-sync-view__empty {
  margin: 0;
  font-size: 11px;
  color: var(--ide-muted);
}
</style>
