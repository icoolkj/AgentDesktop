<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import { monaco } from '@renderer/utils/monacoSetup'
import { computeInlineDiff } from '@renderer/utils/inlineDiff'
import { focusEditor, registerMonacoEditor, unregisterMonacoEditor } from '@renderer/utils/editorSearchBridge'
import { registerMonacoModel, unregisterMonacoModel } from '@renderer/utils/monacoMarkersBridge'
import { DIFF_VIEW_EDITOR_OPTIONS } from '@renderer/utils/monacoEditorTheme'
import { cssVar } from '@renderer/utils/ideCssVars'
import { onColorThemeChange } from '@renderer/modules/appearance/store'
import { useEditorStore } from '@renderer/stores/editorStore'
import { useAppearanceStore } from '@renderer/stores/appearanceStore'
import { storeToRefs } from 'pinia'

const props = defineProps<{
  path: string
  language: string
  modelValue: string
  readOnly?: boolean
  largeFile?: boolean
  /** Original file content before Agent edit — enables inline diff decorations. */
  diffBase?: string | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  focus: []
}>()

const appearance = useAppearanceStore()
const { monacoTheme } = storeToRefs(appearance)

const containerRef = ref<HTMLDivElement | null>(null)
let editor: monaco.editor.IStandaloneCodeEditor | null = null
let model: monaco.editor.ITextModel | null = null
let decorationIds: string[] = []
let viewZoneIds: string[] = []
let diffOverlayTimer: ReturnType<typeof setTimeout> | null = null

function syncDiffEditorOptions(): void {
  if (!editor) return
  const inDiffReview = props.diffBase != null
  editor.updateOptions(inDiffReview ? DIFF_VIEW_EDITOR_OPTIONS : { bracketPairColorization: { enabled: true } })
}

function scheduleDiffOverlay(): void {
  if (diffOverlayTimer) clearTimeout(diffOverlayTimer)
  diffOverlayTimer = setTimeout(() => {
    diffOverlayTimer = null
    applyDiffOverlay()
  }, 120)
}

function syncFromProps(value: string): void {
  if (!model || model.getValue() === value) return
  model.setValue(value)
}

function clearDiffOverlay(): void {
  if (!editor) return

  decorationIds = editor.deltaDecorations(decorationIds, [])
  editor.changeViewZones(accessor => {
    for (const id of viewZoneIds) accessor.removeZone(id)
    viewZoneIds = []
  })
}

function applyDiffOverlay(): void {
  if (!editor || !model) return

  syncDiffEditorOptions()

  if (props.diffBase == null) {
    clearDiffOverlay()
    return
  }

  const current = model.getValue()
  const { pureAddedLines, modifiedLines, charAddedRanges, removedZones } = computeInlineDiff(
    props.diffBase,
    current,
  )

  const decorations: monaco.editor.IModelDeltaDecoration[] = []

  for (const lineNumber of pureAddedLines) {
    decorations.push({
      range: new monaco.Range(lineNumber, 1, lineNumber, 1),
      options: {
        isWholeLine: true,
        className: 'agent-diff-line-added',
        marginClassName: 'agent-diff-margin-added',
        linesDecorationsClassName: 'agent-diff-gutter-added',
        overviewRuler: {
          color: cssVar('--ide-success', '#2ea043'),
          position: monaco.editor.OverviewRulerLane.Left,
        },
      },
    })
  }

  for (const lineNumber of modifiedLines) {
    decorations.push({
      range: new monaco.Range(lineNumber, 1, lineNumber, 1),
      options: {
        isWholeLine: false,
        linesDecorationsClassName: 'agent-diff-gutter-modified',
        overviewRuler: {
          color: cssVar('--ide-git-modified', '#e2c08d'),
          position: monaco.editor.OverviewRulerLane.Left,
        },
      },
    })
  }

  for (const range of charAddedRanges) {
    decorations.push({
      range: new monaco.Range(range.lineNumber, range.startColumn, range.lineNumber, range.endColumn),
      options: {
        className: 'agent-diff-char-added',
        inlineClassName: 'agent-diff-char-added',
        overviewRuler: {
          color: cssVar('--ide-success', '#2ea043'),
          position: monaco.editor.OverviewRulerLane.Left,
        },
      },
    })
  }

  decorationIds = editor.deltaDecorations(decorationIds, decorations)

  void nextTick(() => {
    requestAnimationFrame(() => editor?.layout())
  })

  editor.changeViewZones(accessor => {
    for (const id of viewZoneIds) accessor.removeZone(id)
    viewZoneIds = []

    for (const zone of removedZones) {
      const domNode = document.createElement('div')
      domNode.className = 'agent-diff-removed-zone'

      for (const line of zone.lines) {
        const lineEl = document.createElement('div')
        lineEl.className = 'agent-diff-removed-line'
        lineEl.textContent = line.length > 0 ? line : ' '
        domNode.appendChild(lineEl)
      }

      viewZoneIds.push(
        accessor.addZone({
          afterLineNumber: zone.afterLineNumber,
          heightInLines: zone.lines.length,
          domNode,
        }),
      )
    }
  })
}

onMounted(() => {
  offColorTheme = onColorThemeChange(() => scheduleDiffOverlay())
  if (!containerRef.value) return

  model = monaco.editor.createModel(props.modelValue, props.language, monaco.Uri.file(props.path))
  editor = monaco.editor.create(containerRef.value, {
    model,
    theme: monacoTheme.value,
    automaticLayout: true,
    largeFileOptimizations: props.largeFile ?? false,
    minimap: { enabled: !(props.largeFile ?? false) },
    fontSize: 14,
    tabSize: 2,
    readOnly: props.readOnly ?? false,
    scrollBeyondLastLine: false,
    wordWrap: 'off',
    scrollbar: {
      verticalScrollbarSize: 5,
      horizontalScrollbarSize: 5,
      verticalSliderSize: 5,
      horizontalSliderSize: 5,
      useShadows: false,
    },
  })

  editor.onDidChangeModelContent(() => {
    if (!model) return
    emit('update:modelValue', model.getValue())
    scheduleDiffOverlay()
  })

  editor.onDidFocusEditorText(() => {
    emit('focus')
  })

  registerMonacoEditor(props.path, editor)
  registerMonacoModel(props.path, model)
  applyDiffOverlay()

  void nextTick(() => {
    requestAnimationFrame(() => {
      if (useEditorStore().activePath === props.path) {
        focusEditor(props.path)
      }
    })
  })
})

watch(
  () => props.modelValue,
  value => {
    syncFromProps(value)
    applyDiffOverlay()
  },
)

watch(
  () => props.language,
  language => {
    if (!model) return
    monaco.editor.setModelLanguage(model, language)
  },
)

watch(
  () => props.readOnly,
  readOnly => {
    editor?.updateOptions({ readOnly: readOnly ?? false })
  },
)

watch(
  () => props.diffBase,
  () => applyDiffOverlay(),
)

watch(monacoTheme, theme => {
  monaco.editor.setTheme(theme)
  scheduleDiffOverlay()
})

let offColorTheme: (() => void) | null = null

onBeforeUnmount(() => {
  offColorTheme?.()
  if (diffOverlayTimer) clearTimeout(diffOverlayTimer)
  clearDiffOverlay()
  unregisterMonacoEditor(props.path)
  unregisterMonacoModel(props.path)
  editor?.dispose()
  model?.dispose()
  editor = null
  model = null
})
</script>

<template>
  <div
    ref="containerRef"
    class="monaco-editor-host"
    :class="{ 'monaco-editor-host--diff-review': diffBase != null }"
  />
</template>

<style scoped>
.monaco-editor-host {
  width: 100%;
  height: 100%;
  min-height: 0;
}
</style>

<style>
.monaco-editor .view-line.agent-diff-line-added,
.monaco-editor .view-lines .agent-diff-line-added {
  background-color: color-mix(in srgb, var(--ide-success) 32%, transparent) !important;
  border-left: 3px solid color-mix(in srgb, var(--ide-success) 90%, transparent);
}

.monaco-editor .margin-view-overlays .agent-diff-margin-added,
.monaco-editor .margin-view-overlays .agent-diff-gutter-added {
  background-color: var(--ide-success) !important;
  width: 4px !important;
  margin-left: 3px;
}

.monaco-editor .margin-view-overlays .agent-diff-gutter-modified {
  background-color: var(--ide-git-modified) !important;
  width: 3px !important;
  margin-left: 3px;
}

.monaco-editor .agent-diff-char-added,
.monaco-editor .view-line .agent-diff-char-added {
  background-color: color-mix(in srgb, var(--ide-success) 38%, transparent) !important;
  border-radius: 2px;
}

.agent-diff-removed-zone {
  box-sizing: border-box;
  padding: 0 0 0 62px;
  background: color-mix(in srgb, var(--ide-error) 18%, transparent);
  border-left: 3px solid color-mix(in srgb, var(--ide-error) 75%, transparent);
}

/* Agent diff review: hide bracket-match yellow boxes only (not .cdr diff highlights). */
.monaco-editor-host--diff-review .monaco-editor .bracket-match,
.monaco-editor-host--diff-review .monaco-editor .match-bracket {
  border: none !important;
  box-shadow: none !important;
  background: transparent !important;
  outline: none !important;
}

.agent-diff-removed-line {
  min-height: 19px;
  line-height: 19px;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 14px;
  color: var(--ide-error);
  text-decoration: line-through;
  white-space: pre;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
