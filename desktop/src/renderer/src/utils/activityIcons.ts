/** VS Code codicon-style paths (16×16 viewBox), scaled to 24px in UI. */
export type ActivityIconId = 'explorer' | 'search' | 'git' | 'agent' | 'settings'

export type ActivityIconElement =
  | { type: 'path'; d: string; fillRule?: 'evenodd' | 'nonzero' }
  | { type: 'stroke'; d: string; strokeWidth?: number }
  | { type: 'circle'; cx: number; cy: number; r: number }

export const ACTIVITY_ICONS: Record<
  ActivityIconId,
  { label: string; elements: ActivityIconElement[] }
> = {
  explorer: {
    label: '资源管理器',
    elements: [
      {
        type: 'path',
        d: 'M2.75 5.5v7.75A1.25 1.25 0 0 0 4 14.5h7.5a1.25 1.25 0 0 0 1.25-1.25V6.25H8.25L7.5 5H3.5a.75.75 0 0 0-.75.75z',
      },
      {
        type: 'path',
        d: 'M4.75 2.75v7.5a.75.75 0 0 0 .75.75H13V4.75H9.25L8.5 4H5.5a.75.75 0 0 0-.75.75z',
      },
    ],
  },
  search: {
    label: '搜索',
    elements: [
      {
        type: 'path',
        d: 'M11.742 10.344a6.5 6.5 0 1 0-1.397 1.398h-.001l.001.001a1 1 0 0 0 .115.1l3.85 3.85a1 1 0 0 0 1.415-1.414l-3.85-3.85a1 1 0 0 0-.115-.1zM12 6.5a5.5 5.5 0 1 1-11 0 5.5 5.5 0 0 1 11 0z',
      },
    ],
  },
  git: {
    label: '源代码管理',
    elements: [
      { type: 'circle', cx: 4.25, cy: 3.75, r: 1.35 },
      { type: 'circle', cx: 4.25, cy: 12.25, r: 1.35 },
      { type: 'circle', cx: 11.75, cy: 7.5, r: 1.35 },
      { type: 'stroke', d: 'M4.25 5.1v5.3', strokeWidth: 1.35 },
      { type: 'stroke', d: 'M4.25 7.5h5.75', strokeWidth: 1.35 },
      { type: 'stroke', d: 'M11.75 7.5v1.9', strokeWidth: 1.35 },
    ],
  },
  agent: {
    label: 'Agent',
    elements: [
      {
        type: 'path',
        d: 'M8 1.1l.95 2.88 2.95.97-2.95.97L8 8.9l-.95-2.98-2.95-.97 2.95-.97L8 1.1zM3.2 9.4l.65 1.98 2.03.67-2.03.67-.65 1.98-.65-1.98-2.03-.67 2.03-.67.65-1.98zm9.6 0l.65 1.98 2.03.67-2.03.67-.65 1.98-.65-1.98-2.03-.67 2.03-.67.65-1.98z',
      },
    ],
  },
  settings: {
    label: '设置',
    elements: [
      {
        type: 'path',
        d: 'M9.405 1.05c-.413-1.4-2.397-1.4-2.81 0l-.1.34a1.464 1.464 0 0 1-2.105.872l-.31-.17c-1.283-.698-2.686.705-1.987 1.987l.169.311c.446.82.023 1.841-.872 2.105l-.34.1c-1.4.413-1.4 2.397 0 2.81l.34.1a1.464 1.464 0 0 1 .872 2.105l-.17.31c-.698 1.283.705 2.686 1.987 1.987l.311-.169a1.464 1.464 0 0 1 2.105.872l.1.34c.413 1.4 2.397 1.4 2.81 0l.1-.34a1.464 1.464 0 0 1 2.105-.872l.31.17c1.283.698 2.686-.705 1.987-1.987l-.169-.311a1.464 1.464 0 0 1 .872-2.105l.34-.1c1.4-.413 1.4-2.397 0-2.81l-.34-.1a1.464 1.464 0 0 1-.872-2.105l.17-.31c.698-1.283-.705-2.686-1.987-1.987l-.311.169a1.464 1.464 0 0 1-2.105-.872l-.1-.34zM8 10.93a2.929 2.929 0 1 1 0-5.86 2.929 2.929 0 0 1 0 5.858z',
        fillRule: 'evenodd',
      },
    ],
  },
}
