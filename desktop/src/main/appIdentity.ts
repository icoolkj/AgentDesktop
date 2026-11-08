import { app } from 'electron'

export const APP_NAME = 'Agent Desktop'
export const APP_USER_MODEL_ID = 'cn.agentdesktop.app'

export function formatWorkspaceWindowTitle(workspacePath?: string | null): string {
  if (!workspacePath?.trim()) return APP_NAME
  const folder = workspacePath.replace(/\\/g, '/').split('/').filter(Boolean).pop()
  return folder ? `${folder} — ${APP_NAME}` : APP_NAME
}

/** Set process / taskbar identity before app.ready (especially on Windows). */
export function configureAppIdentity(): void {
  app.setName(APP_NAME)
  process.title = APP_NAME

  if (process.platform === 'win32') {
    app.setAppUserModelId(APP_USER_MODEL_ID)
  }
}
