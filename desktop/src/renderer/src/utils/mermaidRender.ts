import mermaid from 'mermaid'

import type { ColorThemeDefinition } from '@renderer/modules/appearance/types'
import { getColorTheme } from '@renderer/modules/appearance/colorThemes'
import { buildMermaidThemeConfig } from '@renderer/utils/mermaidTheme'
import { normalizeMermaidSvg } from '@renderer/utils/mermaidSvg'

export type MermaidRenderMode = 'inline' | 'full'

let activeThemeKey: string | null = null

function resolveColorTheme(theme?: ColorThemeDefinition): ColorThemeDefinition {
  return (
    theme ??
    getColorTheme(
      (document.documentElement.dataset.colorTheme as ColorThemeDefinition['id']) || 'default-dark',
    )
  )
}

function initMermaid(theme: ColorThemeDefinition, mode: MermaidRenderMode): void {
  const key = `${theme.id}:${mode}`
  if (activeThemeKey === key) return
  mermaid.initialize(
    buildMermaidThemeConfig(theme, { useMaxWidth: mode === 'inline' }),
  )
  activeThemeKey = key
}

export function cleanupStrayMermaidErrors(): void {
  document.querySelectorAll('svg').forEach(svg => {
    const text = svg.textContent ?? ''
    if (text.includes('Syntax error in text')) {
      svg.remove()
    }
  })
}

/** Clear cached theme so the next render re-initializes Mermaid. */
export function invalidateMermaidTheme(): void {
  activeThemeKey = null
}

export function restoreInlineMermaid(theme?: ColorThemeDefinition): void {
  initMermaid(resolveColorTheme(theme), 'inline')
}

export function resetMermaidNodes(container: HTMLElement | null): void {
  if (!container) return
  container.querySelectorAll<HTMLElement>('.md-mermaid').forEach(node => {
    node.removeAttribute('data-processed')
    node.classList.remove('md-mermaid--rendered', 'md-mermaid--error')
    const source = node.dataset.mermaidSource
    if (source) {
      node.innerHTML = ''
      node.textContent = source
    }
  })
}

async function canRenderMermaid(source: string, theme: ColorThemeDefinition): Promise<boolean> {
  initMermaid(theme, 'inline')
  try {
    await mermaid.parse(source)
    return true
  } catch {
    return false
  }
}

export async function renderMermaidSvg(
  source: string,
  mode: MermaidRenderMode,
  theme?: ColorThemeDefinition,
): Promise<string> {
  const colorTheme = resolveColorTheme(theme)
  initMermaid(colorTheme, mode)
  const id = `mermaid-${Math.random().toString(36).slice(2, 10)}`
  const { svg } = await mermaid.render(id, source)
  return mode === 'full' ? normalizeMermaidSvg(svg) : svg
}

function openDiagramLightbox(source: string): void {
  void import('@renderer/utils/diagramLightbox').then(({ diagramLightbox }) => {
    void diagramLightbox.showDiagramFromSource(source, 'Agent 图表')
  })
}

function attachDiagramToolbar(node: HTMLElement, svg: string, source: string): void {
  if (node.querySelector('.md-mermaid-toolbar')) return

  const wrap = document.createElement('div')
  wrap.className = 'md-mermaid-wrap'

  const toolbar = document.createElement('div')
  toolbar.className = 'md-mermaid-toolbar'

  const label = document.createElement('span')
  label.className = 'md-mermaid-toolbar__label'
  label.textContent = '图表'

  const expandBtn = document.createElement('button')
  expandBtn.type = 'button'
  expandBtn.className = 'md-mermaid-expand'
  expandBtn.title = '放大查看原尺寸'
  expandBtn.textContent = '放大'
  expandBtn.addEventListener('click', event => {
    event.stopPropagation()
    openDiagramLightbox(source)
  })

  const canvas = document.createElement('div')
  canvas.className = 'md-mermaid-canvas'
  canvas.innerHTML = svg
  canvas.addEventListener('dblclick', () => {
    openDiagramLightbox(source)
  })

  toolbar.append(label, expandBtn)
  wrap.append(toolbar, canvas)

  node.innerHTML = ''
  node.append(wrap)
}

/** Render `.md-mermaid` blocks inside a container (after v-html update). */
export async function renderMermaidIn(
  container: HTMLElement | null,
  theme?: ColorThemeDefinition,
): Promise<void> {
  if (!container) return

  const colorTheme = resolveColorTheme(theme)
  initMermaid(colorTheme, 'inline')

  const nodes = container.querySelectorAll<HTMLElement>('.md-mermaid:not([data-processed])')
  if (nodes.length === 0) return

  for (const node of nodes) {
    node.setAttribute('data-processed', '1')
    const source = (node.dataset.mermaidSource ?? node.textContent ?? '').trim()
    if (!source) {
      node.classList.add('md-mermaid--error')
      node.textContent = '图表内容为空，无法渲染。'
      continue
    }

    if (!node.dataset.mermaidSource) {
      node.dataset.mermaidSource = source
    }

    const valid = await canRenderMermaid(node.dataset.mermaidSource, colorTheme)
    if (!valid) {
      node.classList.add('md-mermaid--error')
      node.textContent = '图表语法无效，已跳过渲染。'
      continue
    }

    try {
      const svg = await renderMermaidSvg(node.dataset.mermaidSource, 'inline', colorTheme)
      attachDiagramToolbar(node, svg, node.dataset.mermaidSource)
      node.classList.add('md-mermaid--rendered')
    } catch {
      cleanupStrayMermaidErrors()
      node.classList.add('md-mermaid--error')
      node.textContent = '图表渲染失败，已跳过。'
    }
  }

  cleanupStrayMermaidErrors()
}
