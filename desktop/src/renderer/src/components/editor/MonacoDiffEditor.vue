<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'

import { monaco } from '@renderer/utils/monacoSetup'
import { DIFF_VIEW_EDITOR_OPTIONS } from '@renderer/utils/monacoEditorTheme'
import { useAppearanceStore } from '@renderer/stores/appearanceStore'

const props = defineProps<{
  path: string
  language: string
  original: string
  modified: string
}>()

const emit = defineEmits<{
  'update:modified': [value: string]
}>()

const appearance = useAppearanceStore()
const { monacoTheme } = storeToRefs(appearance)

const containerRef = ref<HTMLDivElement | null>(null)
let diffEditor: monaco.editor.IStandaloneDiffEditor | null = null
let originalModel: monaco.editor.ITextModel | null = null
let modifiedModel: monaco.editor.ITextModel | null = null
let modifiedContentDisposable: monaco.IDisposable | null = null

function bindModifiedListener(): void {
  modifiedContentDisposable?.dispose()
  modifiedContentDisposable = modifiedModel?.onDidChangeContent(() => {
    if (modifiedModel) emit('update:modified', modifiedModel.getValue())
  }) ?? null
}

function syncModels(): void {
  if (!diffEditor) return

  const originalUri = monaco.Uri.parse(`git-diff-original://${encodeURIComponent(props.path)}`)
  const modifiedUri = monaco.Uri.parse(`git-diff-modified://${encodeURIComponent(props.path)}`)

  if (!originalModel || originalModel.uri.toString() !== originalUri.toString()) {
    originalModel?.dispose()
    originalModel = monaco.editor.createModel(props.original, props.language, originalUri)
  } else if (originalModel.getValue() !== props.original) {
    originalModel.setValue(props.original)
  }

  if (!modifiedModel || modifiedModel.uri.toString() !== modifiedUri.toString()) {
    modifiedModel?.dispose()
    modifiedModel = monaco.editor.createModel(props.modified, props.language, modifiedUri)
  } else if (modifiedModel.getValue() !== props.modified) {
    modifiedModel.setValue(props.modified)
  }

  diffEditor.setModel({ original: originalModel, modified: modifiedModel })
  diffEditor.getModifiedEditor().updateOptions({ readOnly: false, ...DIFF_VIEW_EDITOR_OPTIONS })
  diffEditor.getOriginalEditor().updateOptions({ readOnly: true, ...DIFF_VIEW_EDITOR_OPTIONS })
  bindModifiedListener()
}

onMounted(() => {
  if (!containerRef.value) return

  diffEditor = monaco.editor.createDiffEditor(containerRef.value, {
    theme: monacoTheme.value,
    automaticLayout: true,
    renderSideBySide: true,
    readOnly: false,
    originalEditable: false,
    fontSize: 14,
    scrollBeyondLastLine: false,
    minimap: { enabled: true },
    renderOverviewRuler: true,
  })

  syncModels()
})

watch(
  () => [props.original, props.modified, props.language, props.path] as const,
  () => syncModels(),
)

watch(monacoTheme, theme => {
  monaco.editor.setTheme(theme)
})

onBeforeUnmount(() => {
  modifiedContentDisposable?.dispose()
  diffEditor?.dispose()
  originalModel?.dispose()
  modifiedModel?.dispose()
  diffEditor = null
  originalModel = null
  modifiedModel = null
})
</script>

<template>
  <div ref="containerRef" class="monaco-diff-host" />
</template>

<style scoped>
.monaco-diff-host {
  width: 100%;
  height: 100%;
  min-height: 0;
}
</style>

<style>
/* Only suppress bracket-match yellow boxes — do NOT touch .cdr (Monaco diff highlights). */
.monaco-diff-host .monaco-editor .bracket-match,
.monaco-diff-host .monaco-editor .match-bracket {
  border: none !important;
  box-shadow: none !important;
  background: transparent !important;
  outline: none !important;
}
</style>
