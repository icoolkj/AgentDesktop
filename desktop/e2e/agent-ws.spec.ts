import fs from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'

import { expect, test } from '@playwright/test'

import { startMockAgentServer } from './helpers/mockAgentServer'
import { gotoWorkspace, launchDesktopApp } from './helpers/launchApp'

test.describe('Agent WebSocket path', () => {
  test('chat.send → tool → file_changed → review reject restores disk', async () => {
    const mock = await startMockAgentServer()
    const workspace = await fs.mkdtemp(path.join(os.tmpdir(), 'agent-desktop-e2e-'))
    const targetFile = path.join(workspace, 'hello.txt')
    await fs.writeFile(targetFile, 'before', 'utf8')

    const { app, page } = await launchDesktopApp({ env: { AGENT_WS_URL: mock.wsUrl } })
    try {
      await gotoWorkspace(page)
      await page.evaluate(async ws => {
        await window.__e2e!.openWorkspace(ws)
        // `auto` auto-acknowledges writes on turn completion, which removes the
        // pending review immediately. Keep `ask` so the review bar can be asserted.
        window.__e2e!.setPermissionMode('ask')
      }, workspace)

      // sendMessage() returns early unless the socket is OPEN, so wait explicitly.
      await page.evaluate(() => window.__e2e!.waitForAgentConnected())

      await page.getByTestId('chat-composer-input').fill('update hello.txt')
      await page.getByTestId('chat-composer-send').click()

      await expect(page.getByTestId('review-accept')).toBeVisible({ timeout: 20_000 })
      await expect.poll(async () => fs.readFile(targetFile, 'utf8')).toBe('from-agent-ws')

      await page.getByTestId('review-reject').click()
      await expect.poll(async () => fs.readFile(targetFile, 'utf8')).toBe('before')
    } finally {
      await app.close()
      await mock.close()
    }
  })

  test('chat.send → review accept keeps agent disk write', async () => {
    const mock = await startMockAgentServer()
    const workspace = await fs.mkdtemp(path.join(os.tmpdir(), 'agent-desktop-e2e-'))
    const targetFile = path.join(workspace, 'hello.txt')
    await fs.writeFile(targetFile, 'before', 'utf8')

    const { app, page } = await launchDesktopApp({ env: { AGENT_WS_URL: mock.wsUrl } })
    try {
      await gotoWorkspace(page)
      await page.evaluate(async ws => {
        await window.__e2e!.openWorkspace(ws)
        // `auto` auto-acknowledges writes on turn completion, which removes the
        // pending review immediately. Keep `ask` so the review bar can be asserted.
        window.__e2e!.setPermissionMode('ask')
      }, workspace)

      // sendMessage() returns early unless the socket is OPEN, so wait explicitly.
      await page.evaluate(() => window.__e2e!.waitForAgentConnected())

      await page.getByTestId('chat-composer-input').fill('update hello.txt')
      await page.getByTestId('chat-composer-send').click()

      await expect(page.getByTestId('review-accept')).toBeVisible({ timeout: 20_000 })
      await page.getByTestId('review-accept').click()
      await expect.poll(async () => fs.readFile(targetFile, 'utf8')).toBe('from-agent-ws')
    } finally {
      await app.close()
      await mock.close()
    }
  })
})
