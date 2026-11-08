import { monaco } from '@renderer/utils/monacoSetup'
import {
  compareProblems,
  type ProblemEntry,
  type ProblemSeverity,
} from '@renderer/utils/problemsModel'

const listeners = new Set<() => void>()
let markerListenerInstalled = false

export function ensureMonacoMarkerListener(): void {
  if (markerListenerInstalled) return
  markerListenerInstalled = true
  monaco.editor.onDidChangeMarkers(() => {
    for (const listener of listeners) listener()
  })
}

export function subscribeMonacoMarkerChanges(listener: () => void): () => void {
  ensureMonacoMarkerListener()
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function registerMonacoModel(path: string, model: monaco.editor.ITextModel): void {
  void path
  void model
  ensureMonacoMarkerListener()
  notifyMarkerListeners()
}

export function unregisterMonacoModel(path: string): void {
  void path
  notifyMarkerListeners()
}

function notifyMarkerListeners(): void {
  for (const listener of listeners) listener()
}

export function collectMonacoProblems(): ProblemEntry[] {
  ensureMonacoMarkerListener()
  const markers = monaco.editor.getModelMarkers({})
  const entries: ProblemEntry[] = []

  for (const marker of markers) {
    if (marker.severity === monaco.MarkerSeverity.Hint) continue
    const path = pathFromResource(marker.resource)
    if (!path) continue
    entries.push({
      path,
      line: marker.startLineNumber,
      column: marker.startColumn,
      endLine: marker.endLineNumber,
      endColumn: marker.endColumn,
      message: marker.message,
      severity: mapSeverity(marker.severity),
      source: marker.source,
      code: marker.code != null ? String(marker.code) : undefined,
    })
  }

  return entries.sort(compareProblems)
}

function mapSeverity(severity: monaco.MarkerSeverity): ProblemSeverity {
  if (severity === monaco.MarkerSeverity.Error) return 'error'
  if (severity === monaco.MarkerSeverity.Warning) return 'warning'
  return 'info'
}

function pathFromResource(resource: monaco.Uri): string | null {
  if (resource.scheme !== 'file') return null
  if (resource.fsPath) return resource.fsPath
  let decoded = decodeURIComponent(resource.path)
  if (/^\/[a-zA-Z]:/.test(decoded)) {
    decoded = decoded.slice(1)
  }
  return decoded.replace(/\//g, '\\')
}
