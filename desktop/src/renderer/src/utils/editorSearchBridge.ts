import { monaco } from '@renderer/utils/monacoSetup'
import { editorPathKey, normalizeEditorPath } from '@renderer/utils/editorPath'

export interface EditorSearchOptions {
  query?: string
  replace?: string
  caseSensitive?: boolean
  wholeWord?: boolean
}

type FindController = {
  start?: (opts: {
    forceRevealReplace?: boolean
    seedSearchStringFromSelection?: boolean
    seedSearchStringFromGlobalClipboard?: boolean
  }) => void
  getState?: () => {
    change: (
      patch: {
        searchString?: string
        replaceString?: string
        matchCase?: boolean
        wholeWord?: boolean
        isRevealReplace?: boolean
        isRegex?: boolean
      },
      reset?: boolean,
    ) => void
  }
}

const handles = new Map<string, monaco.editor.IStandaloneCodeEditor>()

export function registerMonacoEditor(
  path: string,
  editor: monaco.editor.IStandaloneCodeEditor,
): void {
  handles.set(editorPathKey(path), editor)
}

export function unregisterMonacoEditor(path: string): void {
  handles.delete(editorPathKey(path))
}

function getEditor(path: string | null | undefined): monaco.editor.IStandaloneCodeEditor | null {
  if (!path) return null
  return handles.get(editorPathKey(path)) ?? null
}

function getFindController(editor: monaco.editor.IStandaloneCodeEditor): FindController | null {
  return editor.getContribution('editor.contrib.findController') as FindController | null
}

function applyFindState(
  editor: monaco.editor.IStandaloneCodeEditor,
  options: EditorSearchOptions,
  revealReplace: boolean,
): void {
  const controller = getFindController(editor)
  controller?.start?.({
    forceRevealReplace: revealReplace,
    seedSearchStringFromSelection: false,
    seedSearchStringFromGlobalClipboard: false,
  })

  if (!options.query && options.replace === undefined) return

  controller?.getState?.()?.change(
    {
      searchString: options.query ?? '',
      replaceString: options.replace ?? '',
      matchCase: options.caseSensitive ?? false,
      wholeWord: options.wholeWord ?? false,
      isRevealReplace: revealReplace,
      isRegex: false,
    },
    false,
  )
}

export function focusEditor(path: string | null | undefined): boolean {
  const editor = getEditor(path)
  if (!editor) return false

  if (!editor.getPosition()) {
    editor.setPosition({ lineNumber: 1, column: 1 })
  }

  const line = editor.getPosition()?.lineNumber ?? 1
  editor.revealLineInCenter(line)
  editor.layout()
  editor.focus()
  return true
}

export function openFindInFile(
  path: string | null | undefined,
  options: EditorSearchOptions = {},
): boolean {
  const editor = getEditor(path)
  if (!editor) return false

  applyFindState(editor, options, false)
  editor.focus()
  return true
}

export function openReplaceInFile(
  path: string | null | undefined,
  options: EditorSearchOptions = {},
): boolean {
  const editor = getEditor(path)
  if (!editor) return false

  applyFindState(editor, options, true)
  editor.focus()
  return true
}

export function revealLineInFile(
  path: string | null | undefined,
  line: number,
  column = 1,
): boolean {
  const editor = getEditor(path)
  if (!editor) return false

  editor.revealLineInCenter(line)
  editor.setPosition({ lineNumber: line, column })
  editor.layout()
  editor.focus()
  return true
}

/** Wait until Monaco is mounted for path, then reveal line (Problems / search navigation). */
export async function waitForEditorAndRevealLine(
  path: string,
  line: number,
  column = 1,
  timeoutMs = 5000,
): Promise<boolean> {
  const normalized = normalizeEditorPath(path)
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    if (revealLineInFile(normalized, line, column)) {
      return true
    }
    await new Promise<void>(resolve => {
      window.setTimeout(resolve, 40)
    })
  }
  return false
}

export interface EditorSelectionSnapshot {
  path: string
  text: string
  startLine: number
  startColumn: number
  endLine: number
  endColumn: number
}

/** Non-empty Monaco selection for @selection attachments. */
export function getEditorSelection(path: string | null | undefined): EditorSelectionSnapshot | null {
  if (!path) return null
  const editor = getEditor(path)
  if (!editor) return null

  const selection = editor.getSelection()
  const model = editor.getModel()
  if (!selection || !model || selection.isEmpty()) return null

  const text = model.getValueInRange(selection)
  if (!text.trim()) return null

  return {
    path,
    text,
    startLine: selection.startLineNumber,
    startColumn: selection.startColumn,
    endLine: selection.endLineNumber,
    endColumn: selection.endColumn,
  }
}

/** Selection or current line when empty — for Ctrl+K inline edit. */
export function getSelectionOrCurrentLine(path: string | null | undefined): EditorSelectionSnapshot | null {
  const selected = getEditorSelection(path)
  if (selected) return selected

  if (!path) return null
  const editor = getEditor(path)
  if (!editor) return null
  const model = editor.getModel()
  const position = editor.getPosition()
  if (!model || !position) return null

  const lineContent = model.getLineContent(position.lineNumber)
  if (!lineContent.trim()) return null

  return {
    path,
    text: lineContent,
    startLine: position.lineNumber,
    startColumn: 1,
    endLine: position.lineNumber,
    endColumn: model.getLineMaxColumn(position.lineNumber),
  }
}

export function applySelectionReplace(
  path: string,
  selection: EditorSelectionSnapshot,
  replacement: string,
): boolean {
  const editor = getEditor(path)
  if (!editor) return false

  const range = new monaco.Range(
    selection.startLine,
    selection.startColumn,
    selection.endLine,
    selection.endColumn,
  )
  editor.executeEdits('inline-edit', [
    {
      range,
      text: replacement,
      forceMoveMarkers: true,
    },
  ])
  editor.focus()
  return true
}

export interface SelectionAnchorRect {
  top: number
  left: number
  width: number
}

/** Viewport coordinates for floating inline-edit widget below selection. */
export function getSelectionAnchorRect(path: string | null | undefined): SelectionAnchorRect | null {
  const editor = getEditor(path)
  if (!editor) return null

  const selection = editor.getSelection()
  const domNode = editor.getDomNode()
  if (!selection || !domNode) return null

  const editorRect = domNode.getBoundingClientRect()
  const visible = editor.getScrolledVisiblePosition({
    lineNumber: selection.endLineNumber,
    column: selection.endColumn,
  })
  if (!visible) return null

  return {
    top: editorRect.top + visible.top + visible.height + 4,
    left: editorRect.left + visible.left,
    width: Math.min(520, Math.max(280, editorRect.width - 32)),
  }
}
