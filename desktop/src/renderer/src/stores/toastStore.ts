import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useToastStore = defineStore('toast', () => {
  const message = ref<string | null>(null)
  let timer: ReturnType<typeof setTimeout> | null = null

  function show(text: string, durationMs = 4000): void {
    message.value = text
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      message.value = null
      timer = null
    }, durationMs)
  }

  function dismiss(): void {
    message.value = null
    if (timer) clearTimeout(timer)
    timer = null
  }

  return { message, show, dismiss }
})
