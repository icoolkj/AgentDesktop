<script setup lang="ts">
import type { ChatMessage } from '@renderer/stores/chatStore'
import ChatUserMessage from '@renderer/components/chat/ChatUserMessage.vue'
import ChatLiveProgress from '@renderer/components/chat/ChatLiveProgress.vue'

defineProps<{
  user: ChatMessage
}>()
</script>

<template>
  <section class="active-turn" aria-live="polite">
    <div class="active-turn__question">
      <ChatUserMessage :content="user.content" :attachments="user.attachments" />
    </div>

    <ChatLiveProgress />
  </section>
</template>

<style scoped>
.active-turn {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 2px 0 4px;
}

.active-turn__question {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-end;
}
</style>
