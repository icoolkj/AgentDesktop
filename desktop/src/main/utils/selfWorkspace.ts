import { app } from 'electron'
import { normalize, resolve } from 'node:path'

export interface DevContextDto {
  appRoot: string
  rendererSrcRoot: string
  isSelfWorkspace: boolean
}

function normalizePath(p: string): string {
  return normalize(resolve(p)).replace(/\\/g, '/').toLowerCase()
}

function isUnder(child: string, parent: string): boolean {
  const c = normalizePath(child)
  const p = normalizePath(parent)
  return c === p || c.startsWith(`${p}/`)
}

/** Roots that belong to this Electron app (dev + packaged). */
export function getAppDevRoots(): string[] {
  const appPath = app.getAppPath()
  return [resolve(appPath), resolve(appPath, '..')]
}

/** True when the opened workspace overlaps the running app sources (dogfooding). */
export function isSelfWorkspace(workspaceRoot: string | null | undefined): boolean {
  if (!workspaceRoot) return false
  for (const root of getAppDevRoots()) {
    if (isUnder(workspaceRoot, root) || isUnder(root, workspaceRoot)) {
      return true
    }
  }
  return false
}

/** True when a write target can trigger electron-vite HMR / main rebuild. */
export function touchesHotReloadPath(filePath: string): boolean {
  const appPath = app.getAppPath()
  const hotRoots = [
    resolve(appPath, 'src/renderer'),
    resolve(appPath, 'src/main'),
    resolve(appPath, 'src/preload'),
  ]
  return hotRoots.some(root => isUnder(filePath, root))
}

export function getDevContext(workspaceRoot?: string | null): DevContextDto {
  const appRoot = resolve(app.getAppPath())
  return {
    appRoot,
    rendererSrcRoot: resolve(appRoot, 'src/renderer'),
    isSelfWorkspace: isSelfWorkspace(workspaceRoot),
  }
}
