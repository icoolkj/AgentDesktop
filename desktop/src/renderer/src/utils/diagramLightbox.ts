import { ref } from 'vue'
import { renderMermaidSvg, restoreInlineMermaid } from '@renderer/utils/mermaidRender'

const open = ref(false)
const svgHtml = ref('')
const loading = ref(false)
const error = ref('')
const title = ref('')

export function useDiagramLightbox() {
  async function showDiagramFromSource(source: string, label = '架构图'): Promise<void> {
    title.value = label
    open.value = true
    loading.value = true
    error.value = ''
    svgHtml.value = ''

    try {
      svgHtml.value = await renderMermaidSvg(source, 'full')
    } catch {
      error.value = '图表渲染失败，请稍后重试。'
    } finally {
      loading.value = false
      restoreInlineMermaid()
    }
  }

  function closeDiagram(): void {
    open.value = false
    svgHtml.value = ''
    error.value = ''
    title.value = ''
    loading.value = false
  }

  return { open, svgHtml, loading, error, title, showDiagramFromSource, closeDiagram }
}

export const diagramLightbox = useDiagramLightbox()
