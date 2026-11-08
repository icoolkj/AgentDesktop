<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import type { ChatUserAttachment } from '@renderer/stores/chatStore'
import { openAttachmentPreview } from '@renderer/utils/attachmentLightbox'

const props = defineProps<{
  content: string
  attachments?: ChatUserAttachment[]
  createdAt?: string
}>()

const resolved = ref<ChatUserAttachment[]>([])

async function resolveAttachment(att: ChatUserAttachment): Promise<ChatUserAttachment> {
  if (att.missing) return att
  if (att.previewUrl) return att
  if (!att.filePath) return att

  try {
    const pathKind = await window.workspaceApi.getPathKind(att.filePath)
    if (pathKind === 'missing') return { ...att, missing: true }
  } catch {
    return { ...att, missing: true }
  }

  if (att.kind === 'image') {
    try {
      const { dataUrl } = await window.workspaceApi.readFileDataUrl(att.filePath)
      return { ...att, previewUrl: dataUrl }
    } catch {
      return { ...att, missing: true }
    }
  }

  return att
}

async function hydrateAttachments(items: ChatUserAttachment[] | undefined) {
  if (!items?.length) {
    resolved.value = []
    return
  }
  resolved.value = await Promise.all(items.map(resolveAttachment))
}

watch(
  () => props.attachments,
  items => {
    void hydrateAttachments(items)
  },
  { immediate: true, deep: true },
)

onMounted(() => {
  void hydrateAttachments(props.attachments)
})

async function openAttachment(att: ChatUserAttachment) {
  if (att.missing) return

  if (att.kind === 'image') {
    let url = att.previewUrl
    if (!url && att.filePath) {
      try {
        const { dataUrl } = await window.workspaceApi.readFileDataUrl(att.filePath)
        url = dataUrl
      } catch {
        const idx = resolved.value.findIndex(item => item.id === att.id)
        if (idx >= 0) resolved.value[idx] = { ...att, missing: true }
        return
      }
    }
    if (url) openAttachmentPreview(att.name, url)
    return
  }

  if (att.filePath) {
    try {
      await window.workspaceApi.openInSystem(att.filePath)
    } catch {
      const idx = resolved.value.findIndex(item => item.id === att.id)
      if (idx >= 0) resolved.value[idx] = { ...att, missing: true }
    }
  }
}
</script>

<template>
  <div class="user-msg">
    <div v-if="resolved.length" class="user-msg__attachments">
      <button
        v-for="att in resolved"
        :key="att.id"
        type="button"
        class="user-msg__attach"
        data-testid="user-attachment"
        :class="{
          'user-msg__attach--image': att.kind === 'image' && !att.missing,
          'user-msg__attach--pdf': att.kind === 'pdf',
          'user-msg__attach--file': att.kind === 'text',
          'user-msg__attach--missing': att.missing,
        }"
        :disabled="att.missing"
        :title="
          att.missing
            ? '文件已不存在或无法访问'
            : att.kind === 'image'
              ? '点击查看大图'
              : '点击打开文件'
        "
        @click="openAttachment(att)"
      >
        <img
          v-if="att.kind === 'image' && att.previewUrl && !att.missing"
          :src="att.previewUrl"
          :alt="att.name"
          class="user-msg__thumb"
        />
        <span v-else class="user-msg__attach-icon" aria-hidden="true">
          {{ att.missing ? '!' : att.kind === 'pdf' ? 'PDF' : att.kind === 'image' ? 'IMG' : 'FILE' }}
        </span>
        <span class="user-msg__attach-name">{{ att.name }}</span>
        <span v-if="att.missing" class="user-msg__attach-missing">已删除</span>
      </button>
    </div>

    <p v-if="content && !content.startsWith('[')" class="user-msg__text">{{ content }}</p>
    <p v-else-if="content" class="user-msg__text user-msg__text--meta">{{ content }}</p>
  </div>
</template>

<style scoped>
.user-msg {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  max-width: 100%;
}

.user-msg__attachments {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
  max-width: 100%;
}

.user-msg__attach {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 220px;
  padding: 4px 8px 4px 4px;
  border-radius: 8px;
  border: 1px solid color-mix(in srgb, var(--ide-accent) 25%, var(--ide-border));
  background: var(--ide-surface-bg);
  color: var(--ide-fg);
  cursor: pointer;
  font-size: 0.68rem;
  text-align: left;
  transition: border-color 0.15s, background 0.15s;
}

.user-msg__attach:hover:not(:disabled) {
  border-color: var(--ide-accent);
  background: color-mix(in srgb, var(--ide-accent) 8%, var(--ide-surface-bg));
}

.user-msg__attach--missing {
  opacity: 0.65;
  cursor: not-allowed;
  border-color: color-mix(in srgb, var(--ide-error) 35%, var(--ide-border));
}

.user-msg__attach--image {
  padding: 4px;
  flex-direction: column;
  align-items: stretch;
  width: 88px;
}

.user-msg__thumb {
  width: 80px;
  height: 64px;
  object-fit: cover;
  border-radius: 5px;
  display: block;
}

.user-msg__attach-icon {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 5px;
  background: var(--ide-code-bg);
  font-size: 0.55rem;
  font-weight: 700;
  color: var(--ide-muted);
  flex-shrink: 0;
}

.user-msg__attach--pdf .user-msg__attach-icon {
  color: #e74c3c;
}

.user-msg__attach--missing .user-msg__attach-icon {
  color: var(--ide-error);
}

.user-msg__attach-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.user-msg__attach--image .user-msg__attach-name {
  width: 100%;
  padding: 0 2px 2px;
  text-align: center;
}

.user-msg__attach-missing {
  font-size: 0.58rem;
  color: var(--ide-error);
  flex-shrink: 0;
}

.user-msg__text {
  margin: 0;
  padding: 8px 12px;
  border-radius: 12px 12px 4px 12px;
  background: color-mix(in srgb, var(--ide-accent) 14%, var(--ide-surface-bg));
  border: 1px solid color-mix(in srgb, var(--ide-accent) 22%, transparent);
  font-size: 0.84rem;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

.user-msg__text--meta {
  font-size: 0.72rem;
  color: var(--ide-muted);
  font-style: italic;
}
</style>
