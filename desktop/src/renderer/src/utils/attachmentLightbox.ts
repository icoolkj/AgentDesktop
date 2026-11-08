import { ref } from 'vue'

export const attachmentLightbox = {
  open: ref(false),
  title: ref(''),
  imageUrl: ref(''),
}

export function openAttachmentPreview(title: string, imageUrl: string): void {
  attachmentLightbox.title.value = title
  attachmentLightbox.imageUrl.value = imageUrl
  attachmentLightbox.open.value = true
}

export function closeAttachmentPreview(): void {
  attachmentLightbox.open.value = false
  attachmentLightbox.imageUrl.value = ''
}
