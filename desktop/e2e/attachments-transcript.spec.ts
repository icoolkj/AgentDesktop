import fs from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'

import { expect, test } from '@playwright/test'

import { startMockAgentServer } from './helpers/mockAgentServer'
import { gotoWorkspace, launchDesktopApp } from './helpers/launchApp'

test.describe('Attachment transcript replay (P4-A A1)', () => {
  test('persisted image attachment visible after syncCurrentSession reload', async () => {
    const mock = await startMockAgentServer()
    const workspace = await fs.mkdtemp(path.join(os.tmpdir(), 'agent-desktop-att-'))
    const imagePath = path.join(workspace, 'note.png')
    const pngBase64 =
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z5BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='
    await fs.writeFile(imagePath, Buffer.from(pngBase64, 'base64'))

    const { app, page } = await launchDesktopApp({ env: { AGENT_WS_URL: mock.wsUrl } })
    try {
      await gotoWorkspace(page)
      await page.evaluate(async ws => {
        await window.__e2e!.openWorkspace(ws)
      }, workspace)

      await page.evaluate(
        async ({ img, text }) => {
          await window.__e2e!.replayPersistedAttachment({ text, imagePath: img, imageName: 'note.png' })
        },
        { img: imagePath, text: 'see attached screenshot' },
      )

      await expect(page.getByText('see attached screenshot')).toBeVisible()
      await expect(page.getByTestId('user-attachment')).toHaveCount(1)
      await expect(page.getByTestId('user-attachment')).toContainText('note.png')
    } finally {
      await app.close()
      await mock.close()
    }
  })
})
