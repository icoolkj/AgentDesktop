<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'

import PanelSectionHeader from '@renderer/components/layout/PanelSectionHeader.vue'
import GitChangesView from '@renderer/components/sidebar/git/GitChangesView.vue'
import GitCommitView from '@renderer/components/sidebar/git/GitCommitView.vue'
import GitLogView from '@renderer/components/sidebar/git/GitLogView.vue'
import GitSyncView from '@renderer/components/sidebar/git/GitSyncView.vue'
import { useWorkspaceStore } from '@renderer/stores/workspaceStore'
import { useGitStore } from '@renderer/stores/gitStore'
import type { GitPanelView } from '@renderer/utils/gitSidebarUtils'

defineEmits<{
  collapse: []
}>()

const workspace = useWorkspaceStore()
const gitStore = useGitStore()
const { rootPath, loading: workspaceLoading } = storeToRefs(workspace)

const localError = ref<string | null>(null)

const tabs: { id: GitPanelView; label: string; badge?: () => number }[] = [
  { id: 'changes', label: '变更', badge: () => gitStore.totalChangeCount },
  { id: 'commit', label: '提交', badge: () => gitStore.stagedChanges.length },
  { id: 'log', label: '日志' },
  { id: 'sync', label: '同步' },
]

const branchLabel = computed(() => {
  const branch = gitStore.info?.branch
  if (!branch) return ''
  const sync = gitStore.info?.syncStatus
  if (!sync?.trackingBranch) return branch
  const parts: string[] = [branch]
  if (sync.ahead > 0) parts.push(`↑${sync.ahead}`)
  if (sync.behind > 0) parts.push(`↓${sync.behind}`)
  return parts.join(' ')
})

async function refresh() {
  if (!rootPath.value) {
    // Workspace may still be restoring (App.vue / explorer). Not an error state.
    localError.value = null
    return
  }

  localError.value = null

  try {
    await gitStore.refresh('full')
    if (gitStore.error) {
      localError.value = gitStore.error
    }
  } catch (e) {
    localError.value = e instanceof Error ? e.message : String(e)
  }
}

function selectView(view: GitPanelView) {
  gitStore.activeView = view
  if (view === 'sync') {
    void gitStore.refresh('sync')
  }
}

onMounted(() => {
  if (rootPath.value) void refresh()
})

watch(rootPath, (path, prev) => {
  if (path && path !== prev) {
    void refresh()
  }
  if (!path) {
    localError.value = null
  }
})
</script>

<template>
  <aside class="git-sidebar">
    <PanelSectionHeader title="源代码管理" @collapse="$emit('collapse')">
      <span v-if="branchLabel" class="git-sidebar__branch-pill" :title="branchLabel">{{ branchLabel }}</span>
      <button
        type="button"
        class="git-sidebar__icon-btn"
        title="刷新"
        :disabled="gitStore.loading || gitStore.operating"
        @click="refresh"
      >
        ↻
      </button>
    </PanelSectionHeader>

    <nav class="git-sidebar__tabs" aria-label="Git 视图">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        type="button"
        class="git-sidebar__tab"
        :class="{ active: gitStore.activeView === tab.id }"
        @click="selectView(tab.id)"
      >
        {{ tab.label }}
        <span v-if="tab.badge?.()" class="git-sidebar__tab-badge">{{ tab.badge() }}</span>
      </button>
    </nav>

    <div class="git-sidebar__body">
      <p v-if="localError" class="git-sidebar__error">{{ localError }}</p>
      <p v-if="gitStore.operating" class="git-sidebar__busy">
        {{ gitStore.operatingLabel ?? 'Git' }} 进行中…
      </p>

      <template v-if="gitStore.info?.isRepo">
        <GitChangesView v-if="gitStore.activeView === 'changes'" />
        <GitCommitView v-else-if="gitStore.activeView === 'commit'" />
        <GitLogView v-else-if="gitStore.activeView === 'log'" />
        <GitSyncView v-else-if="gitStore.activeView === 'sync'" />
      </template>

      <p v-else-if="rootPath && !gitStore.loading" class="git-sidebar__muted">
        当前文件夹不是 Git 仓库
      </p>
      <p v-else-if="!rootPath" class="git-sidebar__muted">
        {{ workspaceLoading ? '正在打开工作区…' : '打开文件夹后查看 Git 状态' }}
      </p>
      <p v-else-if="gitStore.loading" class="git-sidebar__muted">加载 Git 状态…</p>
    </div>
  </aside>
</template>

<style scoped>
.git-sidebar {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--ide-sidebar-bg);
}

.git-sidebar__branch-pill {
  max-width: 120px;
  margin-right: 4px;
  padding: 2px 6px;
  border-radius: 999px;
  background: var(--ide-hover-overlay);
  font-size: 10px;
  color: var(--ide-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: ui-monospace, monospace;
}

.git-sidebar__icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--ide-muted);
  cursor: pointer;
  font-size: 14px;
}

.git-sidebar__icon-btn:hover:not(:disabled) {
  background: var(--ide-hover-overlay);
  color: var(--ide-fg);
}

.git-sidebar__icon-btn:disabled {
  opacity: 0.45;
  cursor: default;
}

.git-sidebar__tabs {
  display: flex;
  gap: 0;
  padding: 0 8px;
  border-bottom: 1px solid var(--ide-border);
  background: color-mix(in srgb, var(--ide-border) 35%, var(--ide-sidebar-bg));
}

.git-sidebar__tab {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 8px 4px;
  border: none;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--ide-muted);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}

.git-sidebar__tab:hover {
  color: var(--ide-fg);
}

.git-sidebar__tab.active {
  color: var(--ide-fg);
  border-bottom-color: var(--ide-accent);
}

.git-sidebar__tab-badge {
  min-width: 16px;
  padding: 0 4px;
  border-radius: 999px;
  background: var(--ide-hover-overlay);
  font-size: 10px;
  font-weight: 500;
}

.git-sidebar__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 10px;
}

.git-sidebar__muted,
.git-sidebar__busy {
  margin: 0;
  font-size: 11px;
  color: var(--ide-muted);
  line-height: 1.5;
}

.git-sidebar__busy {
  margin-bottom: 8px;
  padding: 6px 8px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--ide-accent) 12%, transparent);
  color: var(--ide-accent);
}

.git-sidebar__error {
  margin: 0 0 10px;
  padding: 8px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--ide-error) 10%, transparent);
  color: var(--ide-error);
  font-size: 11px;
}
</style>
