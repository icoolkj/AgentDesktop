<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useAgentStore } from '@renderer/stores/agentStore'
import { useChatStore } from '@renderer/stores/chatStore'
import { useWorkspaceStore } from '@renderer/stores/workspaceStore'
import { useAgentSettingsStore } from '@renderer/stores/agentSettingsStore'
import { useEditorStore } from '@renderer/stores/editorStore'
import type { PermissionMode, ChatAttachment } from '@agent-desktop/shared-types'
import type { ChatUserAttachment } from '@renderer/stores/chatStore'
import { extractAtQuery, insertAtPath } from '@renderer/utils/chatAttachments'
import {
  classifyAttachmentPath,
  createPendingId,
  maxPendingAttachments,
  pendingToChatAttachments,
  revokePendingPreviews,
  type PendingComposerAttachment,
} from '@renderer/utils/composerAttachments'
import { getEditorSelection } from '@renderer/utils/editorSearchBridge'
import { relativePathFromRoot } from '@renderer/utils/lineDiff'
import {
  extractSlashQuery,
  filterSlashCommands,
  insertSlashCommand,
  type SlashCommand,
} from '@renderer/utils/slashCommands'
import ChatContextPanel from '@renderer/components/chat/ChatContextPanel.vue'
import ChatReviewBar from '@renderer/components/chat/ChatReviewBar.vue'
import { useModelSettingsStore } from '@renderer/stores/modelSettingsStore'
import type { ChatModelSelection } from '@renderer/types/modelSettings'

const agentStore = useAgentStore()
const chatStore = useChatStore()
const workspace = useWorkspaceStore()
const agentSettings = useAgentSettingsStore()
const modelSettings = useModelSettingsStore()
const editorStore = useEditorStore()

const { status } = storeToRefs(agentStore)
const { isSending, contextStats, contextBreakdown } = storeToRefs(chatStore)
const { permissionMode } = storeToRefs(agentSettings)
const { activePath } = storeToRefs(editorStore)

const input = ref('')
const inputRef = ref<HTMLTextAreaElement | null>(null)
const agentModeWrapRef = ref<HTMLElement | null>(null)
const modelMenuWrapRef = ref<HTMLElement | null>(null)
const slashMenuRef = ref<HTMLElement | null>(null)
const atMenuRef = ref<HTMLElement | null>(null)
const atSuggestions = ref<
  {
    name: string
    path: string
    relativePath: string
    isSelection?: boolean
    kind?: 'file' | 'directory'
  }[]
>([])
const showAtMenu = ref(false)
const showSlashMenu = ref(false)
const slashSuggestions = ref<SlashCommand[]>([])
const slashSelectedIndex = ref(0)
const atSelectedIndex = ref(0)
const showModeMenu = ref(false)
const showModelMenu = ref(false)
const pendingAttachments = ref<PendingComposerAttachment[]>([])
const attachError = ref<string | null>(null)
const contextPanelOpen = ref(false)

const MODES: { id: PermissionMode; label: string; hint: string; icon: string }[] = [
  { id: 'auto', label: 'Agent', hint: '自动执行工具', icon: '∞' },
  { id: 'ask', label: 'Ask', hint: '写文件前询问', icon: '?' },
  { id: 'plan', label: 'Plan', hint: '只读规划', icon: '☰' },
]

const activeMode = computed(() => MODES.find(m => m.id === permissionMode.value) ?? MODES[1])

const { chatModelLabel, chatSelection, autoEnabled, manualModelOptions, defaultModel, catalogLoading, catalogError } =
  storeToRefs(modelSettings)
const modelSearchQuery = ref('')
const atSearchGeneration = ref(0)
let atSearchTimer: ReturnType<typeof setTimeout> | null = null

const activeSelection = computed(() => {
  if (!activePath.value) return null
  return getEditorSelection(activePath.value)
})

const selectionChipLabel = computed(() => {
  const sel = activeSelection.value
  if (!sel || !workspace.rootPath) return null
  const rel = relativePathFromRoot(workspace.rootPath, sel.path)
  return `${rel}:${sel.startLine}-${sel.endLine}`
})

const atMentions = computed(() => {
  const pattern = /@([^\s@]+)/g
  const seen = new Set<string>()
  const items: string[] = []
  let match: RegExpExecArray | null
  while ((match = pattern.exec(input.value)) !== null) {
    const token = match[1]?.trim()
    if (!token || seen.has(token.toLowerCase())) continue
    seen.add(token.toLowerCase())
    items.push(token)
  }
  return items
})

const filteredManualModels = computed(() => {
  const q = modelSearchQuery.value.trim().toLowerCase()
  if (!q) return manualModelOptions.value
  return manualModelOptions.value.filter(
    option =>
      option.label.toLowerCase().includes(q) ||
      option.hint?.toLowerCase().includes(q) ||
      option.id.toLowerCase().includes(q),
  )
})

const autoHint = computed(() => {
  if (defaultModel.value) {
    return `平衡质量与速度 · 默认 ${modelSettings.shortModelLabel(defaultModel.value)}`
  }
  return '平衡质量与速度 · 使用设置中的默认模型'
})

const contextBarClass = computed(() => {
  const percent = contextBreakdown.value?.percent ?? contextStats.value?.percent
  if (percent == null) return ''
  if (percent >= 95) return 'chat-composer__context--danger'
  if (percent >= 85) return 'chat-composer__context--warn'
  return ''
})

const contextPercent = computed(() => {
  if (contextBreakdown.value?.percent != null) return contextBreakdown.value.percent
  if (contextStats.value?.percent != null) return contextStats.value.percent
  return 0
})

const contextRingStroke = computed(() => {
  const pct = Math.min(100, Math.max(0, contextPercent.value))
  const radius = 7
  const circumference = 2 * Math.PI * radius
  return {
    dash: `${(pct / 100) * circumference} ${circumference}`,
    radius,
  }
})

const contextRingTitle = computed(() => {
  if (contextBreakdown.value) {
    return `Context ${contextBreakdown.value.percent}% · 点击查看详情`
  }
  if (contextStats.value?.percent != null) {
    return `Context ${contextStats.value.percent}% · 点击查看详情`
  }
  return 'Context 占用 · 点击查看 /context'
})

const canSend = () =>
  status.value === 'connected' &&
  !isSending.value &&
  (input.value.trim().length > 0 || pendingAttachments.value.length > 0)

function toggleContextPanel() {
  if (contextBreakdown.value) {
    contextPanelOpen.value = !contextPanelOpen.value
    return
  }
  contextPanelOpen.value = true
  if (status.value === 'connected' && !isSending.value) {
    void chatStore.sendMessage('/context')
  }
}

watch(slashSuggestions, () => {
  slashSelectedIndex.value = 0
})

watch(atSuggestions, () => {
  atSelectedIndex.value = 0
})

watch(
  () => workspace.rootPath,
  () => {
    showAtMenu.value = false
    atSuggestions.value = []
  },
)

function closeComposerMenus(options?: { keepAtMenu?: boolean; keepSlashMenu?: boolean }) {
  showModeMenu.value = false
  showModelMenu.value = false
  if (!options?.keepAtMenu) showAtMenu.value = false
  if (!options?.keepSlashMenu) showSlashMenu.value = false
}

function onDocumentMouseDown(event: MouseEvent) {
  const target = event.target
  if (!(target instanceof Node)) return

  const anyOpen = showModeMenu.value || showModelMenu.value || showAtMenu.value || showSlashMenu.value
  if (!anyOpen) return

  if (agentModeWrapRef.value?.contains(target)) return
  if (modelMenuWrapRef.value?.contains(target)) return

  if (showAtMenu.value) {
    if (atMenuRef.value?.contains(target)) return
    if (inputRef.value?.contains(target)) return
    showAtMenu.value = false
  }

  if (showSlashMenu.value) {
    if (slashMenuRef.value?.contains(target)) return
    if (inputRef.value?.contains(target)) return
    showSlashMenu.value = false
  }

  showModeMenu.value = false
  showModelMenu.value = false
}

function syncDocumentMenuListener() {
  const open = showModeMenu.value || showModelMenu.value || showAtMenu.value || showSlashMenu.value
  if (open) {
    document.addEventListener('mousedown', onDocumentMouseDown)
  } else {
    document.removeEventListener('mousedown', onDocumentMouseDown)
  }
}

watch([showModeMenu, showModelMenu, showAtMenu, showSlashMenu], syncDocumentMenuListener)

function onFocusComposerEvent() {
  focusInput()
}

function onInsertSlashEvent(event: Event) {
  const command = (event as CustomEvent<string>).detail
  if (!command) return
  const el = inputRef.value
  const cursor = el?.selectionStart ?? input.value.length
  const next = insertSlashCommand(input.value, cursor, command)
  input.value = next.text
  nextTickFocus(next.cursor)
}

onMounted(() => {
  window.addEventListener('agent:focus-composer', onFocusComposerEvent)
  window.addEventListener('agent:insert-slash', onInsertSlashEvent)
})

watch(
  () => status.value,
  next => {
    if (next === 'connected') {
      void modelSettings.ensureModelCatalog()
    }
  },
  { immediate: true },
)

onUnmounted(() => {
  window.removeEventListener('agent:focus-composer', onFocusComposerEvent)
  window.removeEventListener('agent:insert-slash', onInsertSlashEvent)
  document.removeEventListener('mousedown', onDocumentMouseDown)
  revokePendingPreviews(pendingAttachments.value)
  if (atSearchTimer) clearTimeout(atSearchTimer)
})

function pushAtSuggestion(
  items: typeof atSuggestions.value,
  entry: {
    name: string
    path: string
    relativePath: string
    isSelection?: boolean
    kind?: 'file' | 'directory'
  },
) {
  if (items.some(item => item.relativePath.toLowerCase() === entry.relativePath.toLowerCase())) return
  items.push(entry)
}

async function refreshAtSuggestions(query: string) {
  const root = workspace.rootPath
  const items: typeof atSuggestions.value = []

  const sel = activeSelection.value
  const q = query.toLowerCase()
  if (
    sel &&
    (!q || 'selection'.includes(q) || 'sel'.includes(q) || q.startsWith('s') || q.startsWith('选'))
  ) {
    pushAtSuggestion(items, {
      name: '当前选区',
      path: sel.path,
      relativePath: 'selection',
      isSelection: true,
      kind: 'file',
    })
  }

  if (!root) {
    atSuggestions.value = items
    showAtMenu.value = items.length > 0
    return
  }

  if (q.length === 0) {
    for (const tab of editorStore.tabs) {
      if (items.length >= 12) break
      pushAtSuggestion(items, {
        name: basename(tab.path),
        path: tab.path,
        relativePath: relativePathFromRoot(root, tab.path).replace(/\\/g, '/'),
        kind: 'file',
      })
    }
    try {
      const entries = await window.workspaceApi.readDirectory(root)
      for (const entry of entries) {
        if (items.length >= 12) break
        pushAtSuggestion(items, {
          name: entry.name,
          path: entry.path,
          relativePath: entry.name,
          kind: entry.kind === 'directory' ? 'directory' : 'file',
        })
      }
    } catch {
      // ignore unreadable root
    }
    atSuggestions.value = items.slice(0, 12)
    showAtMenu.value = items.length > 0
    return
  }

  atSuggestions.value = items
  showAtMenu.value = true

  const generation = ++atSearchGeneration.value
  if (atSearchTimer) clearTimeout(atSearchTimer)
  atSearchTimer = setTimeout(() => {
    void (async () => {
      try {
        const results = await window.workspaceApi.searchFiles(root, q, 24)
        if (generation !== atSearchGeneration.value) return
        const merged = [...items]
        for (const result of results) {
          if (merged.length >= 12) break
          pushAtSuggestion(merged, {
            name: result.name,
            path: result.path,
            relativePath: result.relativePath,
            kind: result.kind,
          })
        }
        atSuggestions.value = merged.slice(0, 12)
        showAtMenu.value = atSuggestions.value.length > 0
      } catch {
        if (generation !== atSearchGeneration.value) return
        showAtMenu.value = items.length > 0
      }
    })()
  }, 120)
}

async function onSubmit() {
  if (!canSend()) return
  const text = input.value
  attachError.value = null
  const displayAttachments: ChatUserAttachment[] = pendingAttachments.value.map(p => ({
    id: p.id,
    kind: p.kind,
    name: p.name,
    previewUrl: p.previewUrl,
    filePath: p.filePath,
  }))
  let uploads: ChatAttachment[] = []
  try {
    uploads = await pendingToChatAttachments(pendingAttachments.value, {
      model: modelSettings.resolveSendModel(),
    })
    for (const display of displayAttachments) {
      if (display.kind !== 'image') continue
      if (display.previewUrl?.startsWith('data:')) continue
      const upload = uploads.find(
        u =>
          u.kind === 'image' &&
          ((display.filePath && u.path === display.filePath) || u.label === display.name),
      )
      if (upload?.base64) {
        display.previewUrl = `data:${upload.mimeType || 'image/png'};base64,${upload.base64}`
      }
    }
  } catch (e) {
    attachError.value = e instanceof Error ? e.message : String(e)
    return
  }
  revokePendingPreviews(pendingAttachments.value)
  pendingAttachments.value = []
  const trimmed = text.trim()
  input.value = ''
  showAtMenu.value = false
  resetTextareaHeight()

  if (trimmed === '/context') {
    contextPanelOpen.value = true
    await chatStore.sendMessage('/context')
    return
  }

  await chatStore.sendMessage(text, uploads, displayAttachments)
}

function removePendingAttachment(id: string) {
  const item = pendingAttachments.value.find(p => p.id === id)
  if (item?.previewUrl?.startsWith('blob:')) {
    URL.revokeObjectURL(item.previewUrl)
  }
  pendingAttachments.value = pendingAttachments.value.filter(p => p.id !== id)
}

function basename(path: string): string {
  const parts = path.replace(/\\/g, '/').split('/')
  return parts[parts.length - 1] || path
}

async function onAttachClick() {
  attachError.value = null
  try {
    const paths = await window.workspaceApi.openChatFiles()
    for (const filePath of paths) {
      if (pendingAttachments.value.length >= maxPendingAttachments()) {
        attachError.value = `最多 ${maxPendingAttachments()} 个图片/PDF（Claude Code 限制）`
        break
      }
      const classified = classifyAttachmentPath(filePath)
      if (classified.kind === 'unsupported') {
        attachError.value = classified.error ?? '不支持的文件类型'
        continue
      }
      const name = basename(filePath)
      if (classified.kind === 'image') {
        const { dataUrl } = await window.workspaceApi.readFileDataUrl(filePath)
        pendingAttachments.value.push({
          id: createPendingId(),
          name,
          kind: 'image',
          filePath,
          previewUrl: dataUrl,
        })
      } else if (classified.kind === 'pdf') {
        pendingAttachments.value.push({
          id: createPendingId(),
          name,
          kind: 'pdf',
          filePath,
        })
      } else {
        pendingAttachments.value.push({
          id: createPendingId(),
          name,
          kind: 'text',
          filePath,
        })
      }
    }
  } catch (e) {
    attachError.value = e instanceof Error ? e.message : String(e)
  }
}

async function onPaste(event: ClipboardEvent) {
  const items = event.clipboardData?.items
  if (!items?.length) return

  for (const item of items) {
    if (!item.type.startsWith('image/')) continue
    event.preventDefault()
    if (pendingAttachments.value.length >= maxPendingAttachments()) {
      attachError.value = `最多 ${maxPendingAttachments()} 个附件`
      return
    }
    const file = item.getAsFile()
    if (!file) continue
    const name = file.name || `pasted-${Date.now()}.png`
    pendingAttachments.value.push({
      id: createPendingId(),
      name,
      kind: 'image',
      file,
      previewUrl: URL.createObjectURL(file),
    })
    return
  }
}

function onStop() {
  chatStore.cancelGeneration()
}

function pickAtSuggestion(relativePath: string) {
  const el = inputRef.value
  const cursor = el?.selectionStart ?? input.value.length
  const next = insertAtPath(input.value, cursor, relativePath)
  input.value = next.text
  showAtMenu.value = false
  void onInput()
  nextTickFocus(next.cursor)
}

function nextTickFocus(cursor: number) {
  requestAnimationFrame(() => {
    const el = inputRef.value
    if (!el) return
    el.focus()
    el.setSelectionRange(cursor, cursor)
  })
}

function pickSlashSuggestion(command: SlashCommand) {
  const el = inputRef.value
  const cursor = el?.selectionStart ?? input.value.length
  const next = insertSlashCommand(input.value, cursor, command.name)
  input.value = next.text
  showSlashMenu.value = false
  nextTickFocus(next.cursor)
}

function onKeydown(event: KeyboardEvent) {
  if (showSlashMenu.value && slashSuggestions.value.length) {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      slashSelectedIndex.value = (slashSelectedIndex.value + 1) % slashSuggestions.value.length
      return
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault()
      slashSelectedIndex.value =
        (slashSelectedIndex.value - 1 + slashSuggestions.value.length) % slashSuggestions.value.length
      return
    }
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      const item = slashSuggestions.value[slashSelectedIndex.value]
      if (item) pickSlashSuggestion(item)
      return
    }
    if (event.key === 'Escape') {
      showSlashMenu.value = false
      return
    }
  }

  if (showAtMenu.value && atSuggestions.value.length) {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      atSelectedIndex.value = (atSelectedIndex.value + 1) % atSuggestions.value.length
      return
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault()
      atSelectedIndex.value =
        (atSelectedIndex.value - 1 + atSuggestions.value.length) % atSuggestions.value.length
      return
    }
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      const item = atSuggestions.value[atSelectedIndex.value]
      if (item) pickAtSuggestion(item.relativePath)
      return
    }
    if (event.key === 'Escape') {
      showAtMenu.value = false
      return
    }
  }

  if (event.key === 'Escape') {
    if (showModeMenu.value || showModelMenu.value) {
      closeComposerMenus({ keepAtMenu: showAtMenu.value, keepSlashMenu: showSlashMenu.value })
      return
    }
  }

  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    void onSubmit()
  }
}

async function onInput() {
  const el = inputRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 180)}px`

  const cursor = el.selectionStart ?? input.value.length
  const slashQuery = extractSlashQuery(input.value, cursor)
  if (slashQuery != null) {
    slashSuggestions.value = filterSlashCommands(slashQuery)
    showSlashMenu.value = slashSuggestions.value.length > 0
    showAtMenu.value = false
    return
  }
  showSlashMenu.value = false

  const query = extractAtQuery(input.value, cursor)
  if (query != null) {
    await refreshAtSuggestions(query)
  } else {
    showAtMenu.value = false
  }
}

function resetTextareaHeight() {
  const el = inputRef.value
  if (!el) return
  el.style.height = 'auto'
}

function focusInput() {
  inputRef.value?.focus()
}

function setMode(mode: PermissionMode) {
  agentSettings.setPermissionMode(mode)
  showModeMenu.value = false
}

function toggleModeMenu() {
  showModeMenu.value = !showModeMenu.value
  if (showModeMenu.value) showModelMenu.value = false
}

function setChatModel(selection: ChatModelSelection) {
  modelSettings.setChatSelection(selection)
  showModelMenu.value = false
}

function toggleAutoModel() {
  modelSettings.setAutoEnabled(!autoEnabled.value)
}

function toggleModelMenu() {
  showModelMenu.value = !showModelMenu.value
  if (showModelMenu.value) {
    showModeMenu.value = false
    modelSearchQuery.value = ''
  }
}

</script>

<template>
  <div class="chat-composer">
    <ChatReviewBar :permission-mode="permissionMode" />

    <ChatContextPanel
      v-if="contextPanelOpen && contextBreakdown"
      :breakdown="contextBreakdown"
      :expanded="true"
      @toggle="toggleContextPanel"
      @close="contextPanelOpen = false"
    />

    <form class="chat-composer__card" @submit.prevent="onSubmit" @mousedown="focusInput">
      <div v-if="showSlashMenu" ref="slashMenuRef" class="chat-composer__at-menu">
        <button
          v-for="(item, idx) in slashSuggestions"
          :key="item.name"
          type="button"
          class="chat-composer__at-item"
          :class="{ 'chat-composer__at-item--active': idx === slashSelectedIndex }"
          @mousedown.prevent="pickSlashSuggestion(item)"
        >
          <span class="chat-composer__at-icon">/</span>
          <span>
            <strong>{{ item.name }}</strong>
            <span class="chat-composer__slash-desc">{{ item.description }}</span>
          </span>
        </button>
      </div>

      <div v-if="showAtMenu" ref="atMenuRef" class="chat-composer__at-menu">
        <button
          v-for="(item, idx) in atSuggestions"
          :key="item.path"
          type="button"
          class="chat-composer__at-item"
          :class="{ 'chat-composer__at-item--active': idx === atSelectedIndex }"
          @mousedown.prevent="pickAtSuggestion(item.relativePath)"
        >
          <span class="chat-composer__at-icon">{{ item.isSelection ? 'S' : item.name.includes('.') ? 'F' : 'D' }}</span>
          <span>{{ item.isSelection ? `@selection (${selectionChipLabel})` : item.relativePath }}</span>
        </button>
      </div>

      <div v-if="atMentions.length || pendingAttachments.length" class="chat-composer__chips">
        <span v-for="mention in atMentions" :key="mention" class="chat-composer__chip">@{{ mention }}</span>
        <span
          v-for="item in pendingAttachments"
          :key="item.id"
          class="chat-composer__chip chat-composer__chip--attach"
        >
          <img
            v-if="item.kind === 'image' && item.previewUrl"
            :src="item.previewUrl"
            alt=""
            class="chat-composer__chip-thumb"
          />
          <span>{{ item.kind === 'pdf' ? 'PDF · ' : '' }}{{ item.name }}</span>
          <button
            type="button"
            class="chat-composer__chip-remove"
            title="移除"
            @mousedown.prevent="removePendingAttachment(item.id)"
          >
            ×
          </button>
        </span>
      </div>

      <textarea
        ref="inputRef"
        v-model="input"
        rows="1"
        class="chat-composer__input"
        data-testid="chat-composer-input"
        placeholder="@ 引用 · / 命令 · Enter 发送"
        :disabled="status !== 'connected'"
        @keydown="onKeydown"
        @input="onInput"
        @paste="onPaste"
      />

      <div class="chat-composer__footer">
        <div class="chat-composer__footer-left">
          <div ref="agentModeWrapRef" class="chat-composer__mode-wrap">
            <button type="button" class="chat-composer__pill" @mousedown.prevent="toggleModeMenu">
              <span class="chat-composer__pill-icon">{{ activeMode.icon }}</span>
              <span>{{ activeMode.label }}</span>
              <span class="chat-composer__pill-chevron">▾</span>
            </button>
            <div v-if="showModeMenu" class="chat-composer__mode-menu">
              <button
                v-for="mode in MODES"
                :key="mode.id"
                type="button"
                class="chat-composer__mode-item"
                :class="{ 'chat-composer__mode-item--active': mode.id === permissionMode }"
                @mousedown.prevent="setMode(mode.id)"
              >
                <span class="chat-composer__mode-icon">{{ mode.icon }}</span>
                <span class="chat-composer__mode-text">
                  <strong>{{ mode.label }}</strong>
                  <small>{{ mode.hint }}</small>
                </span>
                <span v-if="mode.id === permissionMode" class="chat-composer__mode-check">✓</span>
              </button>
            </div>
          </div>

          <div ref="modelMenuWrapRef" class="chat-composer__mode-wrap">
            <button
              type="button"
              class="chat-composer__pill chat-composer__pill--model"
              title="选择模型"
              @mousedown.prevent="toggleModelMenu"
            >
              <span>{{ chatModelLabel }}</span>
              <span class="chat-composer__pill-chevron">▾</span>
            </button>
            <div v-if="showModelMenu" class="chat-composer__mode-menu chat-composer__model-menu">
              <div class="chat-composer__auto-row">
                <div class="chat-composer__auto-copy">
                  <strong>Auto</strong>
                  <small>{{ autoHint }}</small>
                </div>
                <button
                  type="button"
                  class="chat-composer__auto-toggle"
                  :class="{ 'chat-composer__auto-toggle--on': autoEnabled }"
                  role="switch"
                  :aria-checked="autoEnabled"
                  @mousedown.prevent="toggleAutoModel"
                >
                  <span class="chat-composer__auto-knob" />
                </button>
              </div>

              <template v-if="!autoEnabled">
                <input
                  v-model="modelSearchQuery"
                  type="search"
                  class="chat-composer__model-search"
                  placeholder="Search models"
                  @mousedown.stop
                  @click.stop
                />
                <button
                  v-for="option in filteredManualModels"
                  :key="option.id"
                  type="button"
                  class="chat-composer__mode-item"
                  :class="{ 'chat-composer__mode-item--active': option.id === chatSelection }"
                  @mousedown.prevent="setChatModel(option.id)"
                >
                  <span class="chat-composer__mode-text">
                    <strong>{{ option.label }}</strong>
                    <small v-if="option.hint">{{ option.hint }}</small>
                  </span>
                  <span v-if="option.id === chatSelection" class="chat-composer__mode-check">✓</span>
                </button>
                <p v-if="catalogLoading" class="chat-composer__model-empty">正在加载模型列表…</p>
                <p v-else-if="catalogError && !manualModelOptions.length" class="chat-composer__model-empty">
                  {{ catalogError }}
                </p>
                <p v-else-if="!filteredManualModels.length" class="chat-composer__model-empty">
                  {{ manualModelOptions.length ? '没有匹配的模型' : '暂无可用模型，请在设置 → Models 中刷新' }}
                </p>
              </template>
            </div>
          </div>
        </div>

        <div class="chat-composer__footer-right">
          <button
            type="button"
            class="chat-composer__ctx-ring-btn"
            :class="[
              contextBarClass,
              { 'chat-composer__ctx-ring-btn--active': contextPanelOpen },
            ]"
            :disabled="status !== 'connected'"
            :title="contextRingTitle"
            @mousedown.prevent="toggleContextPanel"
          >
            <svg viewBox="0 0 18 18" width="18" height="18" aria-hidden="true">
              <circle
                cx="9"
                cy="9"
                :r="contextRingStroke.radius"
                fill="none"
                stroke="currentColor"
                stroke-opacity="0.2"
                stroke-width="1.5"
              />
              <circle
                cx="9"
                cy="9"
                :r="contextRingStroke.radius"
                fill="none"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
                :stroke-dasharray="contextRingStroke.dash"
                transform="rotate(-90 9 9)"
              />
            </svg>
          </button>

          <button
            type="button"
            class="chat-composer__icon-btn"
            title="添加文件"
            :disabled="status !== 'connected'"
            @mousedown.prevent="onAttachClick"
          >
            <svg viewBox="0 0 16 16" width="15" height="15" aria-hidden="true">
              <path
                fill="none"
                stroke="currentColor"
                stroke-width="1.25"
                stroke-linecap="round"
                stroke-linejoin="round"
                d="M5.2 8.8 9.8 4.2a2.2 2.2 0 1 1 3.1 3.1L5.9 14.3a3.4 3.4 0 0 1-4.8-4.8l6.9-6.9a4.6 4.6 0 0 1 6.5 6.5L4.8 13.8"
              />
            </svg>
          </button>

          <button
            v-if="isSending"
            type="button"
            class="chat-composer__send chat-composer__send--stop"
            title="停止"
            @click="onStop"
          >
            <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
              <rect x="4" y="4" width="8" height="8" rx="1" fill="currentColor" />
            </svg>
          </button>
          <button
            v-else
            type="submit"
            class="chat-composer__send"
            data-testid="chat-composer-send"
            title="发送"
            :disabled="!canSend()"
          >
            <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
              <path fill="currentColor" d="M8 1.5 14.5 8 8 14.5 6.5 13l4.5-4.5H1.5V7h9.5L6.5 3 8 1.5z" />
            </svg>
          </button>
        </div>
      </div>
    </form>

    <p v-if="attachError" class="chat-composer__hint chat-composer__hint--error">{{ attachError }}</p>
    <p v-if="status !== 'connected'" class="chat-composer__hint">Agent 未连接，请等待重连或使用顶部「重连」</p>
  </div>
</template>

<style scoped>
.chat-composer {
  padding: 8px 10px 10px;
  border-top: 1px solid var(--ide-border);
  background: var(--ide-surface-bg);
  flex-shrink: 0;
}

.chat-composer__review {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
  padding: 6px 10px;
  border-radius: 8px;
  border: 1px solid color-mix(in srgb, var(--ide-accent) 30%, var(--ide-border));
  background: color-mix(in srgb, var(--ide-accent) 6%, var(--ide-surface-bg));
}

.chat-composer__review-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  color: var(--ide-fg);
  font-size: 0.72rem;
  cursor: pointer;
  padding: 0;
}

.chat-composer__review-chevron {
  display: inline-block;
  transition: transform 0.15s ease;
  color: var(--ide-muted);
}

.chat-composer__review-chevron--open {
  transform: rotate(90deg);
}

.chat-composer__review-actions {
  display: flex;
  gap: 6px;
}

.chat-composer__review-btn {
  padding: 3px 10px;
  border-radius: 6px;
  border: 1px solid var(--ide-border);
  background: transparent;
  color: var(--ide-fg);
  font-size: 0.68rem;
  cursor: pointer;
}

.chat-composer__review-btn:hover:not(:disabled) {
  background: var(--ide-hover-overlay);
}

.chat-composer__review-btn--primary {
  background: var(--ide-button-primary-bg);
  border-color: var(--ide-button-primary-bg);
  color: var(--ide-button-primary-fg);
}

.chat-composer__review-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.chat-composer__review-list {
  margin: 0 0 8px;
  padding: 6px 10px;
  list-style: none;
  border-radius: 8px;
  border: 1px solid var(--ide-border);
  background: var(--ide-input-bg);
  max-height: 80px;
  overflow: auto;
}

.chat-composer__review-list li {
  font-size: 0.68rem;
  font-family: ui-monospace, monospace;
  color: var(--ide-muted);
  padding: 2px 0;
}

.chat-composer__card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid var(--ide-button-border);
  background: var(--ide-input-bg);
  position: relative;
}

.chat-composer__card:focus-within {
  border-color: var(--ide-accent);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--ide-accent) 22%, transparent);
}

.chat-composer__slash-desc {
  display: block;
  font-size: 0.62rem;
  color: var(--ide-muted);
  font-weight: normal;
}

.chat-composer__at-menu {
  position: absolute;
  left: 8px;
  right: 8px;
  bottom: calc(100% + 6px);
  max-height: 200px;
  overflow: auto;
  border-radius: 10px;
  border: 1px solid var(--ide-border);
  background: var(--ide-surface-bg);
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.28);
  z-index: 10;
  padding: 4px;
}

.chat-composer__at-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 6px 8px;
  border: none;
  border-radius: 6px;
  background: transparent;
  text-align: left;
  font-size: 0.72rem;
  font-family: ui-monospace, monospace;
  color: var(--ide-fg);
  cursor: pointer;
}

.chat-composer__at-item:hover,
.chat-composer__at-item--active {
  background: var(--ide-hover-overlay);
}

.chat-composer__at-icon {
  width: 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  background: var(--ide-code-bg);
  font-size: 0.6rem;
  color: var(--ide-muted);
  flex-shrink: 0;
}

.chat-composer__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.chat-composer__chip {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 0.65rem;
  font-family: ui-monospace, monospace;
  background: color-mix(in srgb, var(--ide-accent) 12%, var(--ide-code-bg));
  color: var(--ide-accent);
  border: 1px solid color-mix(in srgb, var(--ide-accent) 25%, transparent);
}

.chat-composer__chip--attach {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding-right: 4px;
}

.chat-composer__chip-thumb {
  width: 18px;
  height: 18px;
  border-radius: 3px;
  object-fit: cover;
  flex-shrink: 0;
}

.chat-composer__chip-remove {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  padding: 0;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-size: 0.75rem;
  line-height: 1;
  opacity: 0.7;
}

.chat-composer__chip-remove:hover {
  opacity: 1;
  background: color-mix(in srgb, var(--ide-accent) 15%, transparent);
}

.chat-composer__input {
  width: 100%;
  min-height: 28px;
  max-height: 180px;
  padding: 0;
  border: none;
  background: transparent;
  color: inherit;
  font: inherit;
  line-height: 1.55;
  resize: none;
  outline: none;
}

.chat-composer__input:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.chat-composer__input::placeholder {
  color: var(--ide-welcome-fg);
}

.chat-composer__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 2px;
}

.chat-composer__footer-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
}

.chat-composer__mode-wrap {
  position: relative;
  flex-shrink: 0;
}

.chat-composer__pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  border: 1px solid var(--ide-border);
  background: var(--ide-surface-bg);
  color: var(--ide-fg);
  font-size: 0.68rem;
  cursor: pointer;
}

.chat-composer__pill:hover {
  background: var(--ide-hover-overlay);
}

.chat-composer__pill-icon {
  font-size: 0.72rem;
  color: var(--ide-accent);
}

.chat-composer__pill-chevron {
  font-size: 0.55rem;
  color: var(--ide-muted);
}

.chat-composer__mode-menu {
  position: absolute;
  left: 0;
  bottom: calc(100% + 6px);
  min-width: 180px;
  padding: 4px;
  border-radius: 10px;
  border: 1px solid var(--ide-border);
  background: var(--ide-surface-bg);
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.28);
  z-index: 10;
}

.chat-composer__mode-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 7px 8px;
  border: none;
  border-radius: 6px;
  background: transparent;
  text-align: left;
  cursor: pointer;
  color: var(--ide-fg);
}

.chat-composer__mode-item:hover,
.chat-composer__mode-item--active {
  background: var(--ide-hover-overlay);
}

.chat-composer__mode-icon {
  width: 20px;
  text-align: center;
  color: var(--ide-accent);
  flex-shrink: 0;
}

.chat-composer__mode-text {
  display: flex;
  flex-direction: column;
  gap: 1px;
  flex: 1;
  min-width: 0;
}

.chat-composer__mode-text strong {
  font-size: 0.72rem;
  font-weight: 600;
}

.chat-composer__mode-text small {
  font-size: 0.62rem;
  color: var(--ide-muted);
}

.chat-composer__mode-check {
  color: var(--ide-accent);
  font-size: 0.72rem;
}

.chat-composer__pill--model {
  max-width: 120px;
}

.chat-composer__pill--model span:first-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-composer__model-menu {
  min-width: 220px;
  max-height: 280px;
  overflow-y: auto;
}

.chat-composer__auto-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 8px 10px;
  border-bottom: 1px solid var(--ide-border);
  margin-bottom: 4px;
}

.chat-composer__auto-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.chat-composer__auto-copy strong {
  font-size: 0.74rem;
}

.chat-composer__auto-copy small {
  font-size: 0.62rem;
  color: var(--ide-muted);
  line-height: 1.35;
}

.chat-composer__auto-toggle {
  position: relative;
  width: 34px;
  height: 20px;
  padding: 0;
  border: none;
  border-radius: 999px;
  background: color-mix(in srgb, var(--ide-muted) 35%, var(--ide-border));
  cursor: pointer;
  flex-shrink: 0;
}

.chat-composer__auto-toggle--on {
  background: #3ecfad;
}

.chat-composer__auto-knob {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  border-radius: 999px;
  background: #fff;
  transition: transform 0.15s ease;
}

.chat-composer__auto-toggle--on .chat-composer__auto-knob {
  transform: translateX(14px);
}

.chat-composer__model-search {
  width: calc(100% - 8px);
  margin: 4px 4px 6px;
  padding: 6px 8px;
  border-radius: 8px;
  border: 1px solid var(--ide-border);
  background: var(--ide-input-bg);
  color: var(--ide-fg);
  font-size: 0.68rem;
}

.chat-composer__model-search:focus {
  outline: none;
  border-color: var(--ide-accent);
}

.chat-composer__model-empty {
  margin: 8px 10px;
  font-size: 0.65rem;
  color: var(--ide-muted);
}

.chat-composer__pill--ghost {
  opacity: 0.85;
}

.chat-composer__pill--ghost:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.chat-composer__context {
  font-size: 0.62rem;
  color: var(--ide-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-composer__context--warn {
  color: var(--ide-warning);
}

.chat-composer__context--danger {
  color: var(--ide-error);
}

.chat-composer__context-btn.chat-composer__context--warn {
  color: var(--ide-warning);
  border-color: color-mix(in srgb, var(--ide-warning) 40%, var(--ide-border));
}

.chat-composer__context-btn.chat-composer__context--danger {
  color: var(--ide-error);
  border-color: color-mix(in srgb, var(--ide-error) 40%, var(--ide-border));
}

.chat-composer__footer-right {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.chat-composer__ctx-ring-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--ide-muted);
  cursor: pointer;
}

.chat-composer__ctx-ring-btn:hover:not(:disabled) {
  background: var(--ide-hover-overlay);
  color: var(--ide-fg);
}

.chat-composer__ctx-ring-btn--active {
  color: var(--ide-accent);
  background: color-mix(in srgb, var(--ide-accent) 10%, transparent);
}

.chat-composer__ctx-ring-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.chat-composer__ctx-ring-btn.chat-composer__context--warn {
  color: var(--ide-warning);
}

.chat-composer__ctx-ring-btn.chat-composer__context--danger {
  color: var(--ide-error);
}

.chat-composer__icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--ide-muted);
  cursor: pointer;
}

.chat-composer__icon-btn:hover:not(:disabled) {
  background: var(--ide-hover-overlay);
  color: var(--ide-fg);
}

.chat-composer__icon-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.chat-composer__send {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border-radius: 999px;
  border: none;
  background: var(--ide-button-primary-bg);
  color: var(--ide-button-primary-fg);
  cursor: pointer;
}

.chat-composer__send:hover:not(:disabled) {
  background: color-mix(in srgb, var(--ide-button-primary-bg) 85%, var(--ide-fg));
}

.chat-composer__send:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.chat-composer__send--stop {
  background: transparent;
  border: 1px solid var(--ide-error);
  color: var(--ide-error);
}

.chat-composer__send--stop:hover {
  background: color-mix(in srgb, var(--ide-error) 10%, transparent);
}

.chat-composer__hint {
  margin: 6px 2px 0;
  font-size: 0.68rem;
  color: var(--ide-welcome-fg);
}

.chat-composer__hint--error {
  color: var(--ide-error);
}
</style>
