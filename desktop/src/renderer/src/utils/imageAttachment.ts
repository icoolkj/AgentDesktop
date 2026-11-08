import {
  API_IMAGE_MAX_BASE64_SIZE,
  CHAT_IMAGE_MAX_DIMENSION,
  IMAGE_TARGET_RAW_SIZE,
  isChatImageMimeType,
  type ChatAttachment,
} from '@agent-desktop/shared-types'

function basename(path: string): string {
  const parts = path.replace(/\\/g, '/').split('/')
  return parts[parts.length - 1] || path
}

function blobToBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const result = reader.result
      if (typeof result !== 'string') {
        reject(new Error('Failed to read image'))
        return
      }
      const comma = result.indexOf(',')
      resolve(comma >= 0 ? result.slice(comma + 1) : result)
    }
    reader.onerror = () => reject(reader.error ?? new Error('Failed to read image'))
    reader.readAsDataURL(blob)
  })
}

function dataUrlToBlob(dataUrl: string): Blob {
  const comma = dataUrl.indexOf(',')
  const header = comma >= 0 ? dataUrl.slice(0, comma) : ''
  const base64 = comma >= 0 ? dataUrl.slice(comma + 1) : dataUrl
  const mimeMatch = header.match(/^data:([^;]+)/)
  const mimeType = mimeMatch?.[1] ?? 'image/png'
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return new Blob([bytes], { type: mimeType })
}

async function resizeImage(
  file: Blob,
  mimeType: string,
  maxDimension: number,
): Promise<{ blob: Blob; mimeType: string }> {
  if (typeof createImageBitmap !== 'function') {
    return { blob: file, mimeType }
  }

  const bitmap = await createImageBitmap(file)
  const scale = Math.min(1, maxDimension / Math.max(bitmap.width, bitmap.height))
  const width = Math.max(1, Math.round(bitmap.width * scale))
  const height = Math.max(1, Math.round(bitmap.height * scale))

  if (scale >= 1 && file.size <= IMAGE_TARGET_RAW_SIZE) {
    bitmap.close()
    return { blob: file, mimeType }
  }

  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')
  if (!ctx) {
    bitmap.close()
    return { blob: file, mimeType }
  }
  ctx.drawImage(bitmap, 0, 0, width, height)
  bitmap.close()

  const outputMime = mimeType === 'image/png' ? 'image/png' : 'image/jpeg'
  const blob = await new Promise<Blob>((resolve, reject) => {
    canvas.toBlob(
      b => (b ? resolve(b) : reject(new Error('Failed to encode image'))),
      outputMime,
      outputMime === 'image/jpeg' ? 0.85 : undefined,
    )
  })

  if (blob.size > IMAGE_TARGET_RAW_SIZE && outputMime !== 'image/jpeg') {
    const jpeg = await new Promise<Blob>((resolve, reject) => {
      canvas.toBlob(b => (b ? resolve(b) : reject(new Error('Failed to encode image'))), 'image/jpeg', 0.75)
    })
    return { blob: jpeg, mimeType: 'image/jpeg' }
  }

  return { blob, mimeType: outputMime }
}

export async function prepareImageFromFile(file: File | Blob, filename: string): Promise<ChatAttachment> {
  const mimeType = file.type && isChatImageMimeType(file.type) ? file.type : 'image/png'
  const { blob, mimeType: outMime } = await resizeImage(file, mimeType, CHAT_IMAGE_MAX_DIMENSION)
  const base64 = await blobToBase64(blob)

  if (base64.length > API_IMAGE_MAX_BASE64_SIZE) {
    throw new Error(
      `Image base64 exceeds ${API_IMAGE_MAX_BASE64_SIZE / (1024 * 1024)}MB limit (Claude Code API_IMAGE_MAX_BASE64_SIZE)`,
    )
  }

  return {
    kind: 'image',
    path: filename,
    label: filename,
    mimeType: outMime,
    base64,
  }
}

export async function prepareImageFromPath(absPath: string): Promise<ChatAttachment> {
  const { dataUrl, mimeType } = await window.workspaceApi.readFileDataUrl(absPath)
  const blob = dataUrlToBlob(dataUrl)
  const file = new File([blob], basename(absPath), { type: mimeType })
  return prepareImageFromFile(file, basename(absPath))
}

export function attachmentSummary(uploads: ChatAttachment[]): string {
  const labels = uploads.map(u => u.label || basename(u.path)).slice(0, 3)
  const suffix = uploads.length > 3 ? ` +${uploads.length - 3}` : ''
  return `[${uploads.length} attachment(s): ${labels.join(', ')}${suffix}]`
}

export function assertImageBase64WithinLimit(base64: string): void {
  if (base64.length > API_IMAGE_MAX_BASE64_SIZE) {
    throw new Error(
      `Image base64 exceeds ${API_IMAGE_MAX_BASE64_SIZE / (1024 * 1024)}MB limit (Claude Code API_IMAGE_MAX_BASE64_SIZE)`,
    )
  }
}
