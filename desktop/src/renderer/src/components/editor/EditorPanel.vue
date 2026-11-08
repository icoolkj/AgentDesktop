<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { storeToRefs } from 'pinia'

import BinaryFileView from '@renderer/components/editor/BinaryFileView.vue'
import EditorTabs from '@renderer/components/editor/EditorTabs.vue'
import ImagePreview from '@renderer/components/editor/ImagePreview.vue'
import LargeFilePrompt from '@renderer/components/editor/LargeFilePrompt.vue'
import InlineEditWidget from '@renderer/components/editor/InlineEditWidget.vue'
import MonacoEditor from '@renderer/components/editor/MonacoEditor.vue'
import MonacoDiffEditor from '@renderer/components/editor/MonacoDiffEditor.vue'
import EditorWelcome from '@renderer/components/editor/EditorWelcome.vue'
import { useEditorStore } from '@renderer/stores/editorStore'
import { useGitStore } from '@renderer/stores/gitStore'
import { useSessionChangesStore } from '@renderer/stores/sessionChangesStore'
import { useWorkspaceStore } from '@renderer/stores/workspaceStore'
import { focusEditor } from '@renderer/utils/editorSearchBridge'
import { textLoadPolicyMessage } from '@renderer/utils/fileSizeLimits'
import { formatFileSize } from '@renderer/utils/fileKind'
import { relativePathFromRoot } from '@renderer/utils/lineDiff'

const workspace = useWorkspaceStore()
const editor = useEditorStore()
const sessionChanges = useSessionChangesStore()
const gitStore = useGitStore()
const { rootPath } = storeToRefs(workspace)

const activeTab = computed(() => editor.activeTab)

const loadedTextTabs = computed(() =>
  editor.tabs.filter(tab => tab.viewKind === 'text' && !tab.loading),
)

const loadedGitDiffTabs = computed(() =>
  editor.tabs.filter(tab => tab.viewKind === 'git-diff' && !tab.loading),
)

const breadcrumb = computed(() => {
  const tab = activeTab.value
  if (!tab || !rootPath.value) return null
  return relativePathFromRoot(rootPath.value, tab.path)
})

const largeFileBanner = computed(() => {
  const tab = activeTab.value
  if (!tab?.largeFile || tab.fileSize == null) return null
  return textLoadPolicyMessage('large', formatFileSize(tab.fileSize))
})

const diskAheadBanner = computed(() => {
  const tab = activeTab.value
  if (!tab?.diskAhead) return null
  return {
    path: tab.path,
    message: 'Agent 已在磁盘更新此文件，与您未保存的编辑冲突。请选择采用 Agent 版本或保留您的编辑。',
  }
})

function onAcceptDiskVersion() {
  const tab = activeTab.value
  if (!tab?.diskAhead) return
  editor.acceptDiskVersion(tab.path)
}

function onKeepBuffer() {
  const tab = activeTab.value
  if (!tab?.diskAhead) return
  editor.keepBufferOverDisk(tab.path)
}

function diffBaseForPath(path: string): string | null {
  const tab = editor.tabs.find(t => t.path === path)
  if (tab?.diskAhead) {
    return tab.savedContent
  }

  if (gitStore.isPathIgnoredSync(path)) return null

  const agentChange = sessionChanges.getChange(path)
  if (agentChange) return agentChange.previousContent ?? ''

  const cached = gitStore.cachedBaseline(path)
  if (cached !== undefined) return cached

  void gitStore.ensureBaseline(path)
  return null
}

const diffBanner = computed(() => {
  const tab = activeTab.value
  if (!tab || tab.viewKind !== 'text' || tab.loading) return null

  if (tab.diskAhead) {
    return '未保存编辑 vs Agent 磁盘版本 — 绿色为你的内容 · 红色为 Agent 版本'
  }

  const agentChange = sessionChanges.getChange(tab.path)
  if (agentChange) {
    const stats =
      agentChange.removed > 0 || agentChange.added > 0
        ? `+${agentChange.added}${agentChange.removed > 0 ? ` −${agentChange.removed}` : ''}`
        : ''
    return stats
      ? `Agent 修改对比 ${stats} — 绿色新增 · 红色删除`
      : 'Agent 修改对比 — 绿色新增 · 红色删除'
  }

  return null
})

const gitDiffBanner = computed(() => {
  const tab = activeTab.value
  if (!tab || tab.viewKind !== 'git-diff' || tab.loading) return null
  return 'Git 并排对比 — 左侧为 HEAD / 索引，右侧为工作区（可编辑）'
})

function onContentChange(path: string, value: string): void {
  editor.setContent(path, value)
}

async function focusActiveEditor(): Promise<void> {
  const path = editor.activePath
  if (!path) return

  const tab = editor.tabs.find(t => t.path === path)
  if (!tab || tab.viewKind !== 'text' || tab.loading) return

  await nextTick()
  requestAnimationFrame(() => {
    focusEditor(path)
  })
}

watch(
  () => {
    const tab = activeTab.value
    if (!tab) return ''
    return `${tab.path}|${tab.loading}|${tab.viewKind}`
  },
  () => {
    void focusActiveEditor()
  },
)

let baselineEnsureTimer: ReturnType<typeof setTimeout> | null = null

function scheduleBaselineEnsure(paths: string[]): void {
  if (baselineEnsureTimer) clearTimeout(baselineEnsureTimer)
  baselineEnsureTimer = setTimeout(() => {
    baselineEnsureTimer = null
    for (const path of paths) void gitStore.ensureBaseline(path)
  }, 120)
}

watch(
  () => loadedTextTabs.value.map(tab => tab.path),
  paths => {
    scheduleBaselineEnsure(paths)
  },
  { immediate: true },
)

async function onKeyDown(event: KeyboardEvent): Promise<void> {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
    event.preventDefault()
    await editor.saveActive()
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeyDown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeyDown)
})
</script>

<template>
  <main class="editor-panel">
    <EditorWelcome v-if="!rootPath || !editor.tabs.length" :has-workspace="Boolean(rootPath)" />

    <div v-else class="editor-panel__workspace">
      <EditorTabs />
      <button
        v-if="breadcrumb"
        type="button"
        class="editor-breadcrumb"
        title="在资源管理器中显示"
        @click="editor.revealActiveInTree()"
      >
        {{ breadcrumb }}
      </button>
      <div v-if="editor.error" class="editor-panel__error">{{ editor.error }}</div>
      <div v-if="gitDiffBanner" class="editor-panel__git-diff">{{ gitDiffBanner }}</div>
      <div v-else-if="diffBanner" class="editor-panel__agent-diff">{{ diffBanner }}</div>
      <div v-if="diskAheadBanner" class="editor-panel__merge">
        <span class="editor-panel__merge-text">{{ diskAheadBanner.message }}</span>
        <div class="editor-panel__merge-actions">
          <button type="button" class="editor-panel__merge-btn" @click="onAcceptDiskVersion">
            采用 Agent 版本
          </button>
          <button type="button" class="editor-panel__merge-btn editor-panel__merge-btn--primary" @click="onKeepBuffer">
            保留我的编辑
          </button>
        </div>
      </div>
      <div v-if="largeFileBanner" class="editor-panel__large-banner">{{ largeFileBanner }}</div>
      <div class="editor-panel__editor">
        <div v-if="activeTab?.loading" class="editor-panel__loading">Loading…</div>

        <MonacoEditor
          v-for="tab in loadedTextTabs"
          v-show="activeTab?.path === tab.path && activeTab?.viewKind === 'text' && !activeTab?.loading"
          :key="`text-${tab.path}`"
          class="editor-panel__monaco"
          :path="tab.path"
          :language="tab.language"
          :model-value="tab.content"
          :large-file="tab.largeFile"
          :diff-base="diffBaseForPath(tab.path)"
          @update:model-value="value => onContentChange(tab.path, value)"
          @focus="editor.revealActiveInTree()"
        />

        <MonacoDiffEditor
          v-for="tab in loadedGitDiffTabs"
          v-show="activeTab?.path === tab.path && activeTab?.viewKind === 'git-diff' && !activeTab?.loading"
          :key="`git-diff-${tab.path}`"
          class="editor-panel__monaco"
          :path="tab.path"
          :language="tab.language"
          :original="tab.gitDiffOriginal ?? ''"
          :modified="tab.content"
          @update:modified="value => onContentChange(tab.path, value)"
        />

        <ImagePreview
          v-if="activeTab && !activeTab.loading && activeTab.viewKind === 'image' && activeTab.mediaDataUrl"
          :src="activeTab.mediaDataUrl"
          :alt="activeTab.name"
          :file-size="activeTab.fileSize"
        />
        <LargeFilePrompt
          v-else-if="activeTab && !activeTab.loading && activeTab.viewKind === 'large-prompt' && activeTab.textLoadPolicy"
          :name="activeTab.name"
          :path="activeTab.path"
          :file-size="activeTab.fileSize"
          :text-load-policy="activeTab.textLoadPolicy"
          @open-anyway="editor.confirmOpenLargeFile(activeTab.path)"
          @open-in-system="editor.openActiveInSystem()"
        />
        <BinaryFileView
          v-else-if="activeTab && !activeTab.loading && activeTab.viewKind === 'binary'"
          :name="activeTab.name"
          :path="activeTab.path"
          :mime-type="activeTab.mimeType"
          :file-size="activeTab.fileSize"
          @open-in-system="editor.openActiveInSystem()"
        />
      </div>
    </div>
    <InlineEditWidget />
  </main>
</template>

<style scoped>
.editor-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: var(--ide-editor-bg);
}

.editor-panel__workspace {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.editor-breadcrumb {
  display: block;
  width: 100%;
  padding: 4px 12px;
  border: none;
  border-bottom: 1px solid var(--ide-border);
  background: var(--ide-tab-bar-bg);
  color: var(--ide-muted);
  font-size: 12px;
  font-family: ui-monospace, monospace;
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.editor-breadcrumb:hover {
  color: var(--ide-accent);
  background: var(--ide-hover-overlay);
}

.editor-panel__error {
  padding: 6px 12px;
  background: color-mix(in srgb, var(--ide-error) 15%, transparent);
  color: var(--ide-error);
  font-size: 12px;
  border-bottom: 1px solid color-mix(in srgb, var(--ide-error) 30%, transparent);
}

.editor-panel__large-banner {
  padding: 6px 12px;
  background: color-mix(in srgb, var(--ide-warning) 12%, transparent);
  color: var(--ide-warning);
  font-size: 12px;
  border-bottom: 1px solid color-mix(in srgb, var(--ide-warning) 25%, transparent);
}

.editor-panel__merge {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 12px;
  background: color-mix(in srgb, var(--ide-warning) 14%, transparent);
  color: var(--ide-fg);
  font-size: 12px;
  border-bottom: 1px solid color-mix(in srgb, var(--ide-warning) 30%, transparent);
}

.editor-panel__merge-text {
  flex: 1;
  min-width: 0;
}

.editor-panel__merge-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.editor-panel__merge-btn {
  padding: 3px 10px;
  border-radius: 4px;
  border: 1px solid var(--ide-border);
  background: var(--ide-surface-bg);
  color: var(--ide-fg);
  font-size: 11px;
  cursor: pointer;
}

.editor-panel__merge-btn:hover {
  background: var(--ide-hover-overlay);
}

.editor-panel__merge-btn--primary {
  border-color: color-mix(in srgb, var(--ide-accent) 40%, var(--ide-border));
  background: color-mix(in srgb, var(--ide-accent) 12%, var(--ide-surface-bg));
  color: var(--ide-accent);
}

.editor-panel__disk-ahead {
  padding: 6px 12px;
  background: color-mix(in srgb, var(--ide-accent) 15%, transparent);
  color: var(--ide-accent);
  font-size: 12px;
  border-bottom: 1px solid color-mix(in srgb, var(--ide-accent) 30%, transparent);
}

.editor-panel__agent-diff {
  padding: 6px 12px;
  background: color-mix(in srgb, var(--ide-success) 10%, transparent);
  color: var(--ide-success);
  font-size: 12px;
  border-bottom: 1px solid color-mix(in srgb, var(--ide-success) 25%, transparent);
}

.editor-panel__git-diff {
  padding: 6px 12px;
  background: color-mix(in srgb, var(--ide-accent) 12%, transparent);
  color: var(--ide-accent);
  font-size: 12px;
  border-bottom: 1px solid color-mix(in srgb, var(--ide-accent) 25%, transparent);
}

.editor-panel__editor {
  position: relative;
  flex: 1;
  min-height: 0;
}

.editor-panel__monaco {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.editor-panel__loading {
  position: absolute;
  inset: 0;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--ide-editor-bg);
  color: var(--ide-muted);
}
</style>
