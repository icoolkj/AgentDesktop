import * as monaco from 'monaco-editor'

import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker'
import jsonWorker from 'monaco-editor/esm/vs/language/json/json.worker?worker'
import cssWorker from 'monaco-editor/esm/vs/language/css/css.worker?worker'
import htmlWorker from 'monaco-editor/esm/vs/language/html/html.worker?worker'
import tsWorker from 'monaco-editor/esm/vs/language/typescript/ts.worker?worker'

self.MonacoEnvironment = {
  getWorker(_: unknown, label: string) {
    switch (label) {
      case 'json':
        return new jsonWorker()
      case 'css':
      case 'scss':
      case 'less':
        return new cssWorker()
      case 'html':
      case 'handlebars':
      case 'razor':
        return new htmlWorker()
      case 'typescript':
      case 'javascript':
        return new tsWorker()
      default:
        return new editorWorker()
    }
  },
}

type MonacoTsLanguage = {
  typescriptDefaults: {
    setCompilerOptions: (options: object) => void
    setDiagnosticsOptions: (options: object) => void
  }
  javascriptDefaults: {
    setCompilerOptions: (options: object) => void
    setDiagnosticsOptions: (options: object) => void
  }
  ScriptTarget: { ESNext: number }
  ModuleResolutionKind: { NodeJs: number }
  ModuleKind: { ESNext: number }
  JsxEmit: { React: number }
}

const tsLanguage = (monaco.languages as unknown as { typescript?: MonacoTsLanguage }).typescript
if (tsLanguage) {
  const compilerOptions = {
    target: tsLanguage.ScriptTarget.ESNext,
    allowNonTsExtensions: true,
    moduleResolution: tsLanguage.ModuleResolutionKind.NodeJs,
    module: tsLanguage.ModuleKind.ESNext,
    noEmit: true,
    esModuleInterop: true,
    allowJs: true,
    jsx: tsLanguage.JsxEmit.React,
    strict: true,
  }
  tsLanguage.typescriptDefaults.setCompilerOptions(compilerOptions)
  tsLanguage.javascriptDefaults.setCompilerOptions(compilerOptions)
  tsLanguage.typescriptDefaults.setDiagnosticsOptions({
    noSemanticValidation: false,
    noSyntaxValidation: false,
  })
  tsLanguage.javascriptDefaults.setDiagnosticsOptions({
    noSemanticValidation: false,
    noSyntaxValidation: false,
  })
}

export { monaco }
