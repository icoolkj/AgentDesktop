<script setup lang="ts">
import { onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import ChatMessages from '@renderer/components/chat/ChatMessages.vue'
import ChatComposer from '@renderer/components/chat/ChatComposer.vue'
import ChatRewindDialog from '@renderer/components/chat/ChatRewindDialog.vue'
import ChatDiagramLightbox from '@renderer/components/chat/ChatDiagramLightbox.vue'
import PermissionDialog from '@renderer/components/chat/PermissionDialog.vue'
import '@renderer/styles/chat-diagram.css'
import '@renderer/styles/chat-markdown.css'
import PanelSectionHeader from '@renderer/components/layout/PanelSectionHeader.vue'
import { useChatStore } from '@renderer/stores/chatStore'
import { useWorkspaceStore } from '@renderer/stores/workspaceStore'
import { formatSessionLabel } from '@renderer/utils/sessionFormat'

defineEmits<{
  collapse: []
}>()

const chatStore = useChatStore()
const workspaceStore = useWorkspaceStore()
const { sessions, sessionId, permissionRequest, rewindDialogOpen, rewindBusy, messages } =
  storeToRefs(chatStore)

function sessionLabel(session: (typeof sessions.value)[number]) {
  const scoped = Boolean(workspaceStore.rootPath)
  return formatSessionLabel(
    session.title,
    session.id,
    session.updatedAt,
    scoped ? undefined : session.workspace,
  )
}

function onSessionChange(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  if (value === '__new__') {
    chatStore.newSession()
    return
  }
  void chatStore.loadSession(value)
}

function onRefresh() {
  chatStore.forceResetChat()
  void chatStore.syncCurrentSession(true)
}

onMounted(() => {
  void chatStore.syncCurrentSession()
})
</script>

<template>
  <aside class="chat-panel">
    <PanelSectionHeader title="Agent" @collapse="$emit('collapse')">
      <button type="button" class="chat-panel__icon-btn" title="刷新会话" @click="onRefresh">
        <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
          <path
            fill="currentColor"
            d="M8 2.5a5.5 5.5 0 0 1 5.24 3.8H11v1.5h4V2.5h-1.5v1.2A7 7 0 1 0 8 15V13.5a5.5 5.5 0 0 1 0-11z"
          />
        </svg>
      </button>
      <select class="chat-panel__select" :value="sessionId" @change="onSessionChange">
        <option value="__new__">+ 新对话</option>
        <option v-for="session in sessions" :key="session.id" :value="session.id">
          {{ sessionLabel(session) }}
        </option>
      </select>
    </PanelSectionHeader>

    <div class="chat-panel__body">
      <ChatMessages />
    </div>
    <ChatComposer />

    <PermissionDialog
      v-if="permissionRequest"
      :request="permissionRequest"
      @reply="(allowed, remember) => chatStore.replyPermission(allowed, remember)"
      @dismiss="chatStore.dismissPermissionRequest()"
    />

    <ChatRewindDialog
      :open="rewindDialogOpen"
      :messages="messages"
      :busy="rewindBusy"
      @close="chatStore.closeRewindDialog()"
      @confirm="payload => chatStore.executeRewind(payload.messageUuid, payload.mode)"
    />

    <ChatDiagramLightbox />
  </aside>
</template>

<style scoped>
.chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 0;
  background: var(--ide-sidebar-bg);
  border-left: 1px solid var(--ide-border);
}

.chat-panel__select {
  flex: 1;
  min-width: 0;
  max-width: 160px;
  height: 22px;
  padding: 0 6px;
  border-radius: 4px;
  border: 1px solid var(--ide-border);
  background: var(--ide-input-bg);
  color: var(--ide-fg);
  font-size: 11px;
}

.chat-panel__select:focus {
  outline: 1px solid var(--ide-accent);
  outline-offset: -1px;
}

.chat-panel__icon-btn {
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
  flex-shrink: 0;
}

.chat-panel__icon-btn:hover {
  background: var(--ide-hover-overlay);
  color: var(--ide-fg);
}

.chat-panel__body {
  flex: 1;
  overflow: auto;
  padding: 12px;
  min-height: 0;
}
</style>
