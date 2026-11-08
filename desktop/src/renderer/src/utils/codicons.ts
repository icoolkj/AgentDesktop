/** VS Code codicon-style icons (16×16 viewBox). */

export type CodiconFillPath = { type: 'path'; d: string; fillRule?: 'evenodd' | 'nonzero' }
export type CodiconStrokePath = {
  type: 'stroke'
  d: string
  strokeWidth?: number
  fill?: string
}
export type CodiconCircle = { type: 'circle'; cx: number; cy: number; r: number; fill?: string }

export type CodiconElement = CodiconFillPath | CodiconStrokePath | CodiconCircle

export type CodiconDefinition = {
  label: string
  elements: CodiconElement[]
}

export const EXPLORER_TOOLBAR_ICONS = {
  newFile: {
    label: '新建文件',
    elements: [
      { type: 'stroke', d: 'M4 2.5h5.5L13 6v7.5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V3.5a1 1 0 0 1 1-1z', strokeWidth: 1.35 },
      { type: 'stroke', d: 'M9 2.5V6h3.5', strokeWidth: 1.35 },
      { type: 'stroke', d: 'M8 9.5h4', strokeWidth: 1.5 },
      { type: 'stroke', d: 'M10 7.5v4', strokeWidth: 1.5 },
    ],
  },
  newFolder: {
    label: '新建文件夹',
    elements: [
      { type: 'stroke', d: 'M2 4.5A1.5 1.5 0 0 1 3.5 3H7l1 1.5H12.5A1.5 1.5 0 0 1 14 6v6.5A1.5 1.5 0 0 1 12.5 14h-9A1.5 1.5 0 0 1 2 12.5V4.5z', strokeWidth: 1.35 },
      { type: 'stroke', d: 'M8 8.5h4', strokeWidth: 1.5 },
      { type: 'stroke', d: 'M10 6.5v4', strokeWidth: 1.5 },
    ],
  },
  refresh: {
    label: '刷新',
    elements: [
      {
        type: 'stroke',
        d: 'M11.5 2.5A5 5 0 1 0 13.8 9.5',
        strokeWidth: 1.35,
      },
      { type: 'stroke', d: 'M11.5 2.5V5.5h-3', strokeWidth: 1.35 },
    ],
  },
} as const satisfies Record<string, CodiconDefinition>

export type ExplorerToolbarIconId = keyof typeof EXPLORER_TOOLBAR_ICONS
