<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import ChatHistoryDetailModal from '@renderer/components/chat/ChatHistoryDetailModal.vue'
import { buildTurnPreview, HISTORY_PAGE_SIZE, type HistoryTurn } from '@renderer/utils/turnPreview'
import { formatMessageTime } from '@renderer/utils/sessionFormat'

export type { HistoryTurn }

interface PageRow {
  turn: HistoryTurn
  index: number
  preview: ReturnType<typeof buildTurnPreview>
}

const props = defineProps<{
  turns: HistoryTurn[]
}>()

const page = ref(0)
const selectedTurn = ref<HistoryTurn | null>(null)
const selectedIndex = ref(0)

const sortedTurns = computed(() => [...props.turns].reverse())

const totalPages = computed(() =>
  Math.max(1, Math.ceil(sortedTurns.value.length / HISTORY_PAGE_SIZE)),
)

const pageRows = computed((): PageRow[] => {
  const start = page.value * HISTORY_PAGE_SIZE
  return sortedTurns.value.slice(start, start + HISTORY_PAGE_SIZE).map((turn, idx) => {
    const globalIdx = props.turns.length - (start + idx)
    return {
      turn,
      index: globalIdx,
      preview: buildTurnPreview(turn.user, turn.assistants),
    }
  })
})

const pageLabel = computed(() => `${page.value + 1} / ${totalPages.value}`)

const selectedPreview = computed(() => {
  if (!selectedTurn.value) return null
  return buildTurnPreview(selectedTurn.value.user, selectedTurn.value.assistants)
})

watch(
  () => props.turns.length,
  () => {
    page.value = 0
    selectedTurn.value = null
  },
)

function openTurn(row: PageRow) {
  selectedTurn.value = row.turn
  selectedIndex.value = row.index
}

function closeDetail() {
  selectedTurn.value = null
}

function prevPage() {
  page.value = Math.max(0, page.value - 1)
  selectedTurn.value = null
}

function nextPage() {
  page.value = Math.min(totalPages.value - 1, page.value + 1)
  selectedTurn.value = null
}
</script>

<template>
  <div class="history-panel">
    <div class="history-panel__list-wrap">
      <ul class="history-panel__list">
        <li v-for="row in pageRows" :key="row.turn.id" class="history-panel__item">
          <button type="button" class="history-row" @click="openTurn(row)">
            <span class="history-row__index">#{{ row.index }}</span>

            <span class="history-row__main">
              <span class="history-row__title-row">
                <span v-if="row.preview.slashLabel" class="history-row__slash">
                  {{ row.preview.slashLabel }}
                </span>
                <span class="history-row__title">{{ row.preview.title }}</span>
              </span>
              <span v-if="row.preview.subtitle" class="history-row__subtitle">
                {{ row.preview.subtitle }}
              </span>
            </span>

            <span class="history-row__meta">
              <span v-if="row.preview.attachCount" class="history-row__badge">
                📎 {{ row.preview.attachCount }}
              </span>
              <span v-if="row.turn.user?.createdAt" class="history-row__time">
                {{ formatMessageTime(row.turn.user.createdAt) }}
              </span>
              <span class="history-row__open">查看 ›</span>
            </span>
          </button>
        </li>
      </ul>
    </div>

    <footer class="history-panel__pager">
      <button type="button" class="history-panel__page-btn" :disabled="page <= 0" @click="prevPage">
        ‹ 上一页
      </button>
      <span class="history-panel__page-label">{{ pageLabel }}</span>
      <button
        type="button"
        class="history-panel__page-btn"
        :disabled="page >= totalPages - 1"
        @click="nextPage"
      >
        下一页 ›
      </button>
    </footer>

    <ChatHistoryDetailModal
      v-if="selectedTurn && selectedPreview"
      :turn="selectedTurn"
      :index="selectedIndex"
      :preview="selectedPreview"
      @close="closeDetail"
    />
  </div>
</template>

<style scoped>
.history-panel {
  display: flex;
  flex-direction: column;
  min-height: 180px;
  max-height: min(340px, 42vh);
  border-radius: 10px;
  border: 1px solid var(--ide-border);
  background: var(--ide-surface-bg);
  overflow: hidden;
}

.history-panel__list-wrap {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.history-panel__list {
  list-style: none;
  margin: 0;
  padding: 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.history-panel__item {
  margin: 0;
}

.history-row {
  display: grid;
  grid-template-columns: 34px 1fr auto;
  gap: 8px;
  align-items: center;
  width: 100%;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: color-mix(in srgb, var(--ide-sidebar-bg) 50%, transparent);
  color: inherit;
  cursor: pointer;
  text-align: left;
  font: inherit;
  transition: border-color 0.12s ease, background 0.12s ease;
}

.history-row:hover {
  border-color: color-mix(in srgb, var(--ide-accent) 35%, var(--ide-border));
  background: color-mix(in srgb, var(--ide-accent) 5%, var(--ide-sidebar-bg));
}

.history-row__index {
  font-size: 0.62rem;
  font-family: ui-monospace, monospace;
  color: var(--ide-muted);
  text-align: center;
}

.history-row__main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.history-row__title-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.history-row__slash {
  flex-shrink: 0;
  font-size: 0.58rem;
  padding: 1px 5px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--ide-accent) 14%, transparent);
  color: var(--ide-accent);
  font-family: ui-monospace, monospace;
}

.history-row__title {
  font-size: 0.76rem;
  font-weight: 500;
  color: var(--ide-fg);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-row__subtitle {
  font-size: 0.66rem;
  color: var(--ide-muted);
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-row__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  flex-shrink: 0;
}

.history-row__badge {
  font-size: 0.58rem;
  color: var(--ide-muted);
}

.history-row__time {
  font-size: 0.56rem;
  color: var(--ide-welcome-fg);
}

.history-row__open {
  font-size: 0.58rem;
  color: var(--ide-accent);
  opacity: 0.85;
}

.history-row:hover .history-row__open {
  opacity: 1;
}

.history-panel__pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-top: 1px solid var(--ide-border);
  background: var(--ide-sidebar-bg);
  flex-shrink: 0;
  position: sticky;
  bottom: 0;
  z-index: 1;
  box-shadow: 0 -4px 12px color-mix(in srgb, var(--ide-bg, #000) 8%, transparent);
}

.history-panel__page-btn {
  padding: 5px 12px;
  border-radius: 6px;
  border: 1px solid var(--ide-border);
  background: var(--ide-button-bg);
  color: var(--ide-fg);
  font-size: 0.7rem;
  font-weight: 500;
  cursor: pointer;
}

.history-panel__page-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.history-panel__page-btn:not(:disabled):hover {
  background: var(--ide-hover-overlay);
  border-color: color-mix(in srgb, var(--ide-accent) 40%, var(--ide-border));
}

.history-panel__page-label {
  font-size: 0.7rem;
  color: var(--ide-fg);
  font-family: ui-monospace, monospace;
  font-weight: 500;
}
</style>
