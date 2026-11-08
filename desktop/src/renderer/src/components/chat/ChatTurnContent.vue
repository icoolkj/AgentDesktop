<script setup lang="ts">
import { computed, ref, toRef } from 'vue'
import type { ChatMessage } from '@renderer/stores/chatStore'
import ChatUserMessage from '@renderer/components/chat/ChatUserMessage.vue'
import ChatToolPanel from '@renderer/components/chat/ChatToolPanel.vue'
import ChatAgentRunSummary from '@renderer/components/chat/ChatAgentRunSummary.vue'
import ChatSessionChanges from '@renderer/components/chat/ChatSessionChanges.vue'
import { useMermaidContainer } from '@renderer/composables/useMermaidContainer'
import { renderSimpleMarkdown } from '@renderer/utils/renderMarkdown'
import { formatMessageTime } from '@renderer/utils/sessionFormat'
import '@renderer/styles/chat-diagram.css'

const props = defineProps<{
  user: ChatMessage | null
  assistants: ChatMessage[]
}>()

const root = ref<HTMLElement | null>(null)
const assistants = toRef(props, 'assistants')
const assistantContent = computed(() => assistants.value.map(m => m.content).join('\0'))

useMermaidContainer(root, [assistantContent])
</script>

<template>
  <div ref="root" class="turn-content">
    <div v-if="user" class="msg msg-user">
      <ChatUserMessage
        :content="user.content"
        :attachments="user.attachments"
        :created-at="user.createdAt"
      />
      <span v-if="user.createdAt" class="msg-meta">{{ formatMessageTime(user.createdAt) }}</span>
    </div>

    <div v-for="(msg, idx) in assistants" :key="idx" class="msg msg-agent">
      <ChatAgentRunSummary
        v-if="msg.tools?.length"
        :tools="msg.tools"
        :file-changes="msg.fileChanges"
      />
      <ChatToolPanel
        v-if="msg.tools?.length"
        :tools="msg.tools"
        :default-expanded="true"
        :show-run-summary="false"
      />
      <ChatSessionChanges
        v-if="msg.fileChanges?.length"
        :changes="msg.fileChanges"
        :default-expanded="true"
        :show-actions="false"
      />
      <div class="msg-agent__body md" v-html="renderSimpleMarkdown(msg.content)" />
      <span v-if="msg.createdAt" class="msg-meta">{{ formatMessageTime(msg.createdAt) }}</span>
    </div>
  </div>
</template>

<style scoped>
.turn-content {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.msg-user {
  align-self: flex-end;
  max-width: 92%;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.msg-agent {
  align-self: stretch;
  display: flex;
  flex-direction: column;
  gap: 10px;
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
