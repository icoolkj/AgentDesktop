import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { AgentEvent } from '@agent-desktop/shared-types'
import { useAgentStore } from '@renderer/stores/agentStore'
import { useEditorStore } from '@renderer/stores/editorStore'
import { useToastStore } from '@renderer/stores/toastStore'
import { useWorkspaceStore } from '@renderer/stores/workspaceStore'
import {
  applySelectionReplace,
  getSelectionAnchorRect,
  getSelectionOrCurrentLine,
  type EditorSelectionSnapshot,
  type SelectionAnchorRect,
} from '@renderer/utils/editorSearchBridge'
import { relativePathFromRoot } from '@renderer/utils/lineDiff'

export type InlineEditStatus = 'idle' | 'streaming' | 'done' | 'error'

export const useInlineEditStore = defineStore('inlineEdit', () => {
  const open = ref(false)
  const requestId = ref('')
  const instruction = ref('')
  const previewText = ref('')
  const finalReplacement = ref('')
  const status = ref<InlineEditStatus>('idle')
  const error = ref<string | null>(null)
  const selection = ref<EditorSelectionSnapshot | null>(null)
  const anchor = ref<SelectionAnchorRect | null>(null)

  let unsubscribeEvents: (() => void) | null = null

  function ensureEventSubscription() {
    if (unsubscribeEvents) return
    unsubscribeEvents = useAgentStore().subscribeEvents(handleAgentEvent)
  }

  function handleAgentEvent(event: AgentEvent) {
    if (event.type === 'inline_edit_delta') {
      if (event.requestId !== requestId.value) return
      previewText.value += event.delta
      status.value = 'streaming'
      return
    }
    if (event.type === 'inline_edit_done') {
      if (event.requestId !== requestId.value) return
      finalReplacement.value = event.replacement
      previewText.value = event.replacement
      status.value = 'done'
      return
    }
    if (event.type === 'inline_edit_error') {
      if (event.requestId !== requestId.value) return
      error.value = event.message
      status.value = 'error'
    }
  }

  function refreshAnchor() {
    const path = selection.value?.path
    anchor.value = getSelectionAnchorRect(path)
  }

  function openWidget() {
    const editorStore = useEditorStore()
    const path = editorStore.activePath
    if (!path) {
      useToastStore().show('请先打开一个文本文件')
      return
    }

    const tab = editorStore.activeTab
    if (!tab || tab.viewKind !== 'text' || tab.loading) {
      useToastStore().show('Inline Edit 仅适用于文本编辑器')
      return
    }

    const snapshot = getSelectionOrCurrentLine(path)
    if (!snapshot) {
      useToastStore().show('请将光标放在非空行，或选中一段代码')
      return
    }

    ensureEventSubscription()
    selection.value = snapshot
    anchor.value = getSelectionAnchorRect(path)
    requestId.value = crypto.randomUUID()
    instruction.value = ''
    previewText.value = ''
    finalReplacement.value = ''
    status.value = 'idle'
    error.value = null
    open.value = true
  }

  async function submit() {
    const instr = instruction.value.trim()
    if (!instr || !selection.value) return
    if (status.value === 'streaming') return

    const editorStore = useEditorStore()
    const workspace = useWorkspaceStore()
    const agentStore = useAgentStore()
    const sel = selection.value
    const tab = editorStore.activeTab

    status.value = 'streaming'
    previewText.value = ''
    finalReplacement.value = ''
    error.value = null
    requestId.value = crypto.randomUUID()

    const root = workspace.rootPath
    const relativePath = root ? relativePathFromRoot(root, sel.path) : sel.path

    try {
      if (agentStore.status !== 'connected') {
        await agentStore.reconnect()
      }
      await agentStore.invokeRpc(
        'inline.edit',
        {
          requestId: requestId.value,
          instruction: instr,
          filePath: sel.path,
          relativePath,
          language: tab?.language ?? '',
          selectionText: sel.text,
          startLine: sel.startLine,
          startColumn: sel.startColumn,
          endLine: sel.endLine,
          endColumn: sel.endColumn,
          workspaceRoot: root ?? undefined,
          editorDocuments: editorStore.getOpenDocumentSnapshots(),
        },
        120_000,
      )
    } catch (err) {
      if (status.value === 'streaming') {
        status.value = 'error'
        error.value = err instanceof Error ? err.message : String(err)
      }
    }
  }

  function accept() {
    const replacement = finalReplacement.value || previewText.value
    const sel = selection.value
    if (!sel || !replacement.trim()) return

    if (!applySelectionReplace(sel.path, sel, replacement)) {
      useToastStore().show('无法应用修改，请重试')
      return
    }
    close()
  }

  async function reject() {
    if (status.value === 'streaming') {
      try {
        await useAgentStore().invokeRpc('chat.cancel', {})
      } catch {
        // socket may already be idle
      }
    }
    close()
  }

  function close() {
    open.value = false
    instruction.value = ''
    previewText.value = ''
    finalReplacement.value = ''
    status.value = 'idle'
    error.value = null
    selection.value = null
    anchor.value = null
  }

  function cleanup() {
    unsubscribeEvents?.()
    unsubscribeEvents = null
  }

  return {
    open,
    requestId,
    instruction,
    previewText,
    finalReplacement,
    status,
    error,
    selection,
    anchor,
    openWidget,
    refreshAnchor,
    submit,
    accept,
    reject,
    close,
    cleanup,
  }
})
