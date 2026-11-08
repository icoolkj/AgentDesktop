import { describe, expect, it, vi } from 'vitest'

import { editorPathKey, editorPathsEqual, normalizeEditorPath } from './editorPath'

describe('editorPath', () => {
  it('normalizes slashes on win32', () => {
    vi.stubGlobal('window', { desktopEnv: { platform: 'win32' } })
    expect(normalizeEditorPath('D:/proj/src/a.ts')).toBe('D:\\proj\\src\\a.ts')
    vi.unstubAllGlobals()
  })

  it('compares paths case-insensitively on Windows', () => {
    expect(editorPathsEqual('D:/a.ts', 'D:\\A.ts')).toBe(true)
    expect(editorPathKey('D:/a.ts')).toBe(editorPathKey('d:\\a.ts'))
  })
})
