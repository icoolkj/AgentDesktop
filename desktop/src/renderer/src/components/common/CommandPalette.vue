<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { searchCommands, type CommandItem } from '@renderer/utils/commandRegistry'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  close: []
  run: [command: CommandItem]
}>()

const query = ref('')
const selectedIndex = ref(0)
const inputRef = ref<HTMLInputElement | null>(null)

const results = computed(() => searchCommands(query.value))

watch(
  () => props.open,
  async open => {
    if (!open) {
      query.value = ''
      selectedIndex.value = 0
      return
    }
    await nextTick()
    inputRef.value?.focus()
    inputRef.value?.select()
  },
)

watch(results, () => {
  selectedIndex.value = 0
})

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault()
    emit('close')
    return
  }
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    if (!results.value.length) return
    selectedIndex.value = (selectedIndex.value + 1) % results.value.length
    return
  }
  if (event.key === 'ArrowUp') {
    event.preventDefault()
    if (!results.value.length) return
    selectedIndex.value = (selectedIndex.value - 1 + results.value.length) % results.value.length
    return
  }
  if (event.key === 'Enter') {
    event.preventDefault()
    const item = results.value[selectedIndex.value]
    if (item) pick(item)
  }
}

function pick(command: CommandItem) {
  emit('run', command)
  emit('close')
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="cmd-palette-backdrop" @mousedown="emit('close')">
      <div class="cmd-palette" role="dialog" aria-label="命令面板" @mousedown.stop>
        <input
          ref="inputRef"
          v-model="query"
          class="cmd-palette__input"
          type="text"
          placeholder="输入命令名称…"
          spellcheck="false"
          @keydown="onKeydown"
        />

        <ul v-if="results.length" class="cmd-palette__list">
          <li
            v-for="(item, index) in results"
            :key="item.id"
            class="cmd-palette__item"
            :class="{ 'cmd-palette__item--active': index === selectedIndex }"
            @mousedown.prevent="pick(item)"
          >
            <span class="cmd-palette__label">{{ item.label }}</span>
            <span v-if="item.detail" class="cmd-palette__detail">{{ item.detail }}</span>
            <kbd v-if="item.shortcut" class="cmd-palette__kbd">{{ item.shortcut }}</kbd>
          </li>
        </ul>
        <p v-else class="cmd-palette__empty">无匹配命令</p>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.cmd-palette-backdrop {
  position: fixed;
  inset: 0;
  z-index: 10000;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 12vh;
  background: color-mix(in srgb, var(--ide-editor-bg) 20%, transparent);
  backdrop-filter: blur(2px);
}

.cmd-palette {
  width: min(560px, calc(100vw - 32px));
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  background: var(--ide-surface-bg, var(--ide-editor-bg));
  box-shadow: 0 16px 48px color-mix(in srgb, var(--ide-editor-bg) 40%, transparent);
  overflow: hidden;
}

.cmd-palette__input {
  width: 100%;
  padding: 14px 16px;
  border: none;
  border-bottom: 1px solid var(--ide-border);
  background: transparent;
  color: var(--ide-fg);
  font-size: 14px;
  outline: none;
}

.cmd-palette__list {
  list-style: none;
  margin: 0;
  padding: 6px;
  max-height: 360px;
  overflow: auto;
}

.cmd-palette__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 4px;
  cursor: pointer;
}

.cmd-palette__item--active,
.cmd-palette__item:hover {
  background: var(--ide-list-active-bg);
  color: var(--ide-list-active-fg);
}

.cmd-palette__label {
  flex: 1;
  font-size: 13px;
}

.cmd-palette__detail {
  font-size: 11px;
  color: var(--ide-muted);
}

.cmd-palette__kbd {
  font-size: 10px;
  padding: 2px 6px;
  border: 1px solid var(--ide-border);
  border-radius: 3px;
  color: var(--ide-muted);
}

.cmd-palette__empty {
  margin: 0;
  padding: 16px;
  font-size: 12px;
  color: var(--ide-muted);
}
</style>
