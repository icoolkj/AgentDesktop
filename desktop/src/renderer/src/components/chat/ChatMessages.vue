<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useChatStore, type ChatMessage } from '@renderer/stores/chatStore'
import { useAppearanceStore } from '@renderer/stores/appearanceStore'
import { onColorThemeChange } from '@renderer/modules/appearance/store'
import {
  cleanupStrayMermaidErrors,
  invalidateMermaidTheme,
  renderMermaidIn,
  resetMermaidNodes,
} from '@renderer/utils/mermaidRender'
import ChatTurnContent from '@renderer/components/chat/ChatTurnContent.vue'
import ChatHistoryPanel from '@renderer/components/chat/ChatHistoryPanel.vue'
import ChatHistoryBanner from '@renderer/components/chat/ChatHistoryBanner.vue'
import ChatActiveTurn from '@renderer/components/chat/ChatActiveTurn.vue'
import ChatAttachmentLightbox from '@renderer/components/chat/ChatAttachmentLightbox.vue'

interface ChatTurn {
  id: string
  user: ChatMessage | null
  assistants: ChatMessage[]
}

const chatStore = useChatStore()
const appearanceStore = useAppearanceStore()
const { messages, isSending, streamingText } = storeToRefs(chatStore)
const { colorTheme } = storeToRefs(appearanceStore)

const bottomAnchor = ref<HTMLElement | null>(null)
const messagesRoot = ref<HTMLElement | null>(null)
const historySectionRef = ref<HTMLElement | null>(null)
const historyExpanded = ref(false)

let offThemeChange: (() => void) | null = null

function buildTurns(msgs: ChatMessage[]): ChatTurn[] {
  const turns: ChatTurn[] = []
  let current: ChatTurn | null = null

  for (const msg of msgs) {
    if (msg.role === 'user') {
      if (current) turns.push(current)
      current = {
        id: msg.messageUuid ?? `turn-${turns.length}-${msg.createdAt ?? ''}`,
        user: msg,
        assistants: [],
      }
      continue
    }
    if (current) {
      current.assistants.push(msg)
    } else {
      turns.push({
        id: `assistant-${turns.length}`,
        user: null,
        assistants: [msg],
      })
    }
  }
  if (current) turns.push(current)
  return turns
}

const pendingUserMessage = computed(() => {
  if (!isSending.value || messages.value.length === 0) return null
  const last = messages.value[messages.value.length - 1]
  return last?.role === 'user' ? last : null
})

/** Completed messages — exclude in-flight user bubble (shown in ChatActiveTurn). */
const completedMessages = computed(() => {
  if (!pendingUserMessage.value) return messages.value
  return messages.value.slice(0, -1)
})

const completedTurns = computed(() => buildTurns(completedMessages.value))

const olderTurns = computed(() => {
  const turns = completedTurns.value
  if (pendingUserMessage.value) return turns
  if (turns.length <= 1) return []
  return turns.slice(0, -1)
})

const latestTurn = computed(() => {
  if (pendingUserMessage.value) return null
  const turns = completedTurns.value
  if (turns.length === 0) return null
  return turns[turns.length - 1] ?? null
})

watch(isSending, sending => {
  if (sending) historyExpanded.value = false
})

function toggleHistory() {
  historyExpanded.value = !historyExpanded.value
  if (historyExpanded.value) {
    void nextTick(() => {
      historySectionRef.value?.scrollIntoView({ block: 'start', behavior: 'smooth' })
    })
  }
}

async function scrollToBottom() {
  if (historyExpanded.value) return
  await nextTick()
  bottomAnchor.value?.scrollIntoView({ block: 'end', behavior: 'smooth' })
}

async function refreshMermaid() {
  await nextTick()
  await renderMermaidIn(messagesRoot.value, colorTheme.value)
}

function onThemeChanged() {
  invalidateMermaidTheme()
  resetMermaidNodes(messagesRoot.value)
  void refreshMermaid()
}

watch([messages, isSending, streamingText], () => {
  void scrollToBottom()
})

watch([messages, isSending, historyExpanded], () => {
  void refreshMermaid()
})

onMounted(() => {
  cleanupStrayMermaidErrors()
  offThemeChange = onColorThemeChange(onThemeChanged)
  void refreshMermaid()
})

onUnmounted(() => {
  offThemeChange?.()
})
</script>

<template>
  <div ref="messagesRoot" class="messages">
    <div v-if="messages.length === 0 && !isSending" class="empty">
      <p class="empty__title">Agent</p>
      <p class="empty__hint">
        在下方输入问题，@ 引用文件或文件夹。<br />
        Agent 会读取工作区并协助改代码。
      </p>
      <ul class="empty__tips">
        <li><kbd>Enter</kbd> 发送 · <kbd>Shift+Enter</kbd> 换行</li>
        <li><kbd>@</kbd> 引用上下文 · 底部切换 Agent / Ask / Plan</li>
      </ul>
    </div>

    <!-- Older history — hidden behind one banner -->
    <div v-if="olderTurns.length" ref="historySectionRef" class="messages__history">
      <ChatHistoryBanner
        :count="olderTurns.length"
        :expanded="historyExpanded"
        @toggle="toggleHistory"
      />

      <ChatHistoryPanel v-if="historyExpanded" :turns="olderTurns" />
    </div>

    <!-- Latest completed Q&A — visible when idle -->
    <ChatTurnContent
      v-if="latestTurn"
      :user="latestTurn.user"
      :assistants="latestTurn.assistants"
    />

    <!-- In-flight question + progress -->
    <ChatActiveTurn v-if="pendingUserMessage" :user="pendingUserMessage" />

    <div ref="bottomAnchor" class="scroll-anchor" aria-hidden="true" />
    <ChatAttachmentLightbox />
  </div>
</template>

<style scoped>
.messages {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.messages__history {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.scroll-anchor {
  height: 1px;
  flex-shrink: 0;
}

.empty {
  color: var(--ide-welcome-fg);
  text-align: center;
  margin-top: 40px;
  padding: 0 12px;
}

.empty__title {
  margin: 0 0 8px;
  font-size: 1rem;
  font-weight: 600;
  color: var(--ide-welcome-title);
}

.empty__hint {
  margin: 0 0 14px;
  font-size: 0.8rem;
  line-height: 1.55;
}

.empty__tips {
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: 0.72rem;
  line-height: 1.8;
  color: var(--ide-muted);
}

.empty__tips kbd {
  padding: 1px 5px;
  border-radius: 4px;
  border: 1px solid var(--ide-border);
  background: var(--ide-code-bg);
  font-size: 0.65rem;
  font-family: inherit;
}
</style>
