import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ExplorerDialogMode = 'prompt' | 'confirm'

export const useExplorerPromptStore = defineStore('explorerPrompt', () => {
  const open = ref(false)
  const mode = ref<ExplorerDialogMode>('prompt')
  const title = ref('')
  const message = ref('')
  const value = ref('')
  const placeholder = ref('')
  const confirmLabel = ref('确定')

  let pendingPrompt: ((result: string | null) => void) | null = null
  let pendingConfirm: ((result: boolean) => void) | null = null

  function ask(options: {
    title: string
    defaultValue?: string
    placeholder?: string
  }): Promise<string | null> {
    mode.value = 'prompt'
    title.value = options.title
    message.value = ''
    value.value = options.defaultValue ?? ''
    placeholder.value = options.placeholder ?? ''
    open.value = true

    return new Promise(resolve => {
      pendingPrompt = resolve
    })
  }

  function confirm(options: { title: string; message: string; confirmLabel?: string }): Promise<boolean> {
    mode.value = 'confirm'
    title.value = options.title
    message.value = options.message
    confirmLabel.value = options.confirmLabel ?? '确定'
    value.value = ''
    placeholder.value = ''
    open.value = true

    return new Promise(resolve => {
      pendingConfirm = resolve
    })
  }

  function submit(): void {
    if (mode.value === 'confirm') {
      open.value = false
      pendingConfirm?.(true)
      pendingConfirm = null
      return
    }

    const result = value.value.trim() || null
    open.value = false
    pendingPrompt?.(result)
    pendingPrompt = null
  }

  function dismiss(): void {
    open.value = false
    if (mode.value === 'confirm') {
      pendingConfirm?.(false)
      pendingConfirm = null
      return
    }

    pendingPrompt?.(null)
    pendingPrompt = null
  }

  return {
    open,
    mode,
    title,
    message,
    value,
    placeholder,
    confirmLabel,
    ask,
    confirm,
    submit,
    dismiss,
  }
})
