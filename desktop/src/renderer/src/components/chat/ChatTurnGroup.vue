<script setup lang="ts">
import { computed } from 'vue'
import type { ChatMessage } from '@renderer/stores/chatStore'
import ChatUserMessage from '@renderer/components/chat/ChatUserMessage.vue'
import ChatToolPanel from '@renderer/components/chat/ChatToolPanel.vue'
import ChatSessionChanges from '@renderer/components/chat/ChatSessionChanges.vue'
import { renderSimpleMarkdown } from '@renderer/utils/renderMarkdown'
import { formatMessageTime } from '@renderer/utils/sessionFormat'

const props = defineProps<{
  turnId: string
  user: ChatMessage | null
  assistants: ChatMessage[]
  expanded: boolean
  isActive: boolean
  compact?: boolean
}>()

const emit = defineEmits<{
  toggle: []
}>()

const summary = computed(() => {
  const userText = props.user?.content?.trim() ?? ''
  const assistantText = props.assistants[props.assistants.length - 1]?.content?.trim() ?? ''
  const attachCount = props.user?.attachments?.length ?? 0
  const parts: string[] = []
  if (userText) {
    parts.push(userText.length > 72 ? `${userText.slice(0, 69)}…` : userText)
  } else if (attachCount > 0) {
    parts.push(`${attachCount} 个附件`)
  }
  if (assistantText && !props.expanded) {
    const snippet = assistantText.replace(/\s+/g, ' ').slice(0, 80)
    if (snippet) parts.push(snippet + (assistantText.length > 80 ? '…' : ''))
  }
  return parts.join(' · ') || '（空消息）'
})

const attachmentCount = computed(() => props.user?.attachments?.length ?? 0)
</script>

<template>
  <section
    class="chat-turn"
    :class="{
      'chat-turn--active': isActive,
      'chat-turn--collapsed': !expanded,
      'chat-turn--compact': compact,
    }"
  >
    <button type="button" class="chat-turn__header" @click="emit('toggle')">
      <span class="chat-turn__chevron" :class="{ 'chat-turn__chevron--open': expanded }">›</span>
      <span class="chat-turn__summary">{{ summary }}</span>
      <span v-if="attachmentCount" class="chat-turn__badge">{{ attachmentCount }} 附件</span>
      <span v-if="user?.createdAt" class="chat-turn__time">{{ formatMessageTime(user.createdAt) }}</span>
    </button>

    <div v-show="expanded" class="chat-turn__body">
      <div v-if="user" class="msg msg-user">
        <ChatUserMessage
          :content="user.content"
          :attachments="user.attachments"
          :created-at="user.createdAt"
        />
      </div>

      <div v-for="(msg, idx) in assistants" :key="`${turnId}-a-${idx}`" class="msg msg-agent">
        <ChatSessionChanges
          v-if="msg.fileChanges?.length"
          :changes="msg.fileChanges"
          :default-expanded="false"
          :show-actions="false"
        />
        <ChatToolPanel v-if="msg.tools?.length" :tools="msg.tools" :default-expanded="false" />
        <div class="msg-agent__body md" v-html="renderSimpleMarkdown(msg.content)" />
        <span v-if="msg.createdAt" class="msg-meta">{{ formatMessageTime(msg.createdAt) }}</span>
      </div>
    </div>
  </section>
</template>

<style scoped>
.chat-turn {
  border: 1px solid var(--ide-border);
  border-radius: 10px;
  background: color-mix(in srgb, var(--ide-surface-bg) 92%, transparent);
  overflow: hidden;
}

.chat-turn--active {
  border-color: color-mix(in srgb, var(--ide-accent) 28%, var(--ide-border));
}

.chat-turn--collapsed .chat-turn__header {
  opacity: 0.92;
}

.chat-turn--compact .chat-turn__header {
  padding: 5px 8px;
}

.chat-turn--compact .chat-turn__summary {
  font-size: 0.68rem;
}

.chat-turn--compact .chat-turn__body {
  padding: 8px;
  gap: 8px;
}

.chat-turn__header {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border: none;
  background: color-mix(in srgb, var(--ide-sidebar-bg) 50%, transparent);
  color: var(--ide-fg);
  cursor: pointer;
  text-align: left;
  font: inherit;
}

.chat-turn__header:hover {
  background: var(--ide-hover-overlay);
}

.chat-turn__chevron {
  color: var(--ide-muted);
  transition: transform 0.15s ease;
  flex-shrink: 0;
}

.chat-turn__chevron--open {
  transform: rotate(90deg);
}

.chat-turn__summary {
  flex: 1;
  min-width: 0;
  font-size: 0.72rem;
  color: var(--ide-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-turn--active .chat-turn__summary {
  color: var(--ide-fg);
}

.chat-turn__badge {
  font-size: 0.62rem;
  padding: 1px 6px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--ide-accent) 12%, transparent);
  color: var(--ide-accent);
  flex-shrink: 0;
}

.chat-turn__time {
  font-size: 0.58rem;
  color: var(--ide-welcome-fg);
  flex-shrink: 0;
}

.chat-turn__body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 10px 10px 12px;
}

.msg-user {
  align-self: flex-end;
  max-width: 92%;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.msg-agent {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.msg-agent__body {
  font-size: 0.84rem;
  line-height: 1.6;
}

.msg-meta {
  font-size: 0.6rem;
  color: var(--ide-welcome-fg);
}
</style>
