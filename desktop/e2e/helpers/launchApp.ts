import path from 'node:path'
import { fileURLToPath } from 'node:url'

import { _electron as electron, type ElectronApplication, type Page } from '@playwright/test'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const desktopRoot = path.join(__dirname, '..', '..')

export async function launchDesktopApp(options?: {
  env?: Record<string, string>
}): Promise<{
  app: ElectronApplication
  page: Page
}> {
  const mainEntry = path.join(desktopRoot, 'out/main/index.js')
  const app = await electron.launch({
    args: [mainEntry, ...(process.platform === 'linux' ? ['--no-sandbox'] : [])],
    cwd: desktopRoot,
    env: {
      ...process.env,
      VITE_E2E: '1',
      ELECTRON_DISABLE_GPU: '1',
      ...options?.env,
    },
  })

  const page = await app.firstWindow()
  await page.waitForLoadState('domcontentloaded')
  await page.waitForFunction(() => window.__e2e != null)
  return { app, page }
}

export async function gotoWorkspace(page: Page): Promise<void> {
  const hash = '#/workspace'
  await page.evaluate(h => {
    window.location.hash = h
  }, hash)
  await page.waitForTimeout(300)
}
