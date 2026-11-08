import fs from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'

import { expect, test } from '@playwright/test'

import { gotoWorkspace, launchDesktopApp } from './helpers/launchApp'

test.describe('Agent Desktop smoke', () => {
  test('open workspace → review apply → theme switch → terminal stays alive', async () => {
    const workspace = await fs.mkdtemp(path.join(os.tmpdir(), 'agent-desktop-e2e-'))
    const targetFile = path.join(workspace, 'hello.txt')
    await fs.writeFile(targetFile, 'before', 'utf8')

    const { app, page } = await launchDesktopApp()
    try {
      await gotoWorkspace(page)

      await page.evaluate(async ws => {
        await window.__e2e!.openWorkspace(ws)
      }, workspace)

      // The agent writes to disk before emitting file_changed; accept only confirms the review.
      await fs.writeFile(targetFile, 'after-e2e', 'utf8')

      await page.evaluate(async ({ file, content }) => {
        await window.__e2e!.recordPendingChange({
          path: file,
          content,
          previousContent: 'before',
          changeKind: 'modified',
        })
      }, { file: targetFile, content: 'after-e2e' })

      const reviewBtn = page.getByTestId('review-accept')
      await expect(reviewBtn).toBeVisible()
      await reviewBtn.click()

      // Accept clears the pending review and keeps the agent's disk write.
      await expect(page.getByTestId('review-accept')).toHaveCount(0)
      await expect.poll(async () => fs.readFile(targetFile, 'utf8')).toBe('after-e2e')

      const terminalBgBefore = await page.evaluate(() => window.__e2e!.getTerminalBg())
      expect(await page.evaluate(() => window.__e2e!.isTerminalVisible())).toBe(true)

      await page.evaluate(() => window.__e2e!.setColorTheme('solarized-light'))
      await page.waitForTimeout(400)

      const terminalBgAfter = await page.evaluate(() => window.__e2e!.getTerminalBg())
      expect(terminalBgAfter).not.toBe(terminalBgBefore)

      const terminalText = await page.evaluate(() => window.__e2e!.getTerminalText())
      expect(terminalText).not.toContain('进程已退出')
    } finally {
      await app.close()
    }
  })
})
