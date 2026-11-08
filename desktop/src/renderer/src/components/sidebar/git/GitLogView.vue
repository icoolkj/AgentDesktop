<script setup lang="ts">
import { onMounted } from 'vue'

import { useGitStore } from '@renderer/stores/gitStore'
import { formatGitLogDate } from '@renderer/utils/gitSidebarUtils'

const gitStore = useGitStore()

onMounted(() => {
  void gitStore.loadLog(true)
})

function onLoadMore() {
  void gitStore.loadMoreLog()
}

function onRefresh() {
  void gitStore.loadLog(true)
}
</script>

<template>
  <div class="git-log-view">
    <div class="git-log-view__header">
      <h3>提交历史</h3>
      <button
        type="button"
        class="git-log-view__link"
        title="刷新日志"
        :disabled="gitStore.logLoading"
        @click="onRefresh"
      >
        刷新
      </button>
    </div>

    <p v-if="gitStore.logError" class="git-log-view__error">{{ gitStore.logError }}</p>
    <p v-else-if="gitStore.logLoading && !gitStore.logEntries.length" class="git-log-view__empty">
      加载中…
    </p>
    <p v-else-if="!gitStore.logEntries.length" class="git-log-view__empty">暂无提交记录</p>

    <ul v-else class="git-log-view__list">
      <li v-for="entry in gitStore.logEntries" :key="entry.hash" class="git-log-view__item">
        <div class="git-log-view__row">
          <code class="git-log-view__hash" :title="entry.hash">{{ entry.shortHash }}</code>
          <span class="git-log-view__subject" :title="entry.subject">{{ entry.subject }}</span>
        </div>
        <div class="git-log-view__meta">
          <span>{{ entry.author }}</span>
          <span class="git-log-view__dot">·</span>
          <span :title="new Date(entry.date * 1000).toLocaleString()">
            {{ formatGitLogDate(entry.date) }}
          </span>
        </div>
        <p v-if="entry.refs" class="git-log-view__refs">{{ entry.refs }}</p>
      </li>
    </ul>

    <button
      v-if="gitStore.logHasMore"
      type="button"
      class="git-log-view__more"
      :disabled="gitStore.logLoading"
      @click="onLoadMore"
    >
      {{ gitStore.logLoading ? '加载中…' : '加载更多' }}
    </button>
  </div>
</template>

<style scoped>
.git-log-view {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.git-log-view__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.git-log-view__header h3 {
  margin: 0;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--ide-muted);
}

.git-log-view__link {
  padding: 0;
  border: none;
  background: transparent;
  color: var(--ide-accent);
  font-size: 11px;
  cursor: pointer;
}

.git-log-view__link:hover:not(:disabled) {
  text-decoration: underline;
}

.git-log-view__link:disabled {
  opacity: 0.45;
  cursor: default;
}

.git-log-view__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.git-log-view__item {
  padding: 8px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid transparent;
}

.git-log-view__item:hover {
  border-color: var(--ide-border);
  background: rgba(255, 255, 255, 0.05);
}

.git-log-view__row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
}

.git-log-view__hash {
  flex-shrink: 0;
  font-size: 11px;
  color: #9cdcfe;
  font-family: ui-monospace, monospace;
}

.git-log-view__subject {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  color: var(--ide-fg);
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.git-log-view__meta {
  margin-top: 4px;
  font-size: 10px;
  color: var(--ide-muted);
}

.git-log-view__dot {
  margin: 0 4px;
}

.git-log-view__refs {
  margin: 4px 0 0;
  font-size: 10px;
  color: #cca700;
  font-family: ui-monospace, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.git-log-view__empty {
  margin: 0;
  padding: 8px;
  font-size: 11px;
  color: var(--ide-muted);
}

.git-log-view__error {
  margin: 0;
  padding: 8px;
  border-radius: 4px;
  background: rgba(244, 135, 113, 0.08);
  color: #f48771;
  font-size: 11px;
}

.git-log-view__more {
  width: 100%;
  padding: 6px 10px;
  border: 1px solid var(--ide-border);
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.04);
  color: var(--ide-fg);
  font-size: 11px;
  cursor: pointer;
}

.git-log-view__more:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.08);
}

.git-log-view__more:disabled {
  opacity: 0.5;
  cursor: default;
}
</style>
