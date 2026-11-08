import { expect, test } from '@playwright/test'

import { gotoWorkspace, launchDesktopApp } from './helpers/launchApp'
import { startRealAgentServer } from './helpers/realAgentServer'

test.describe('Real Java Agent WebSocket (P4-A A2b)', () => {
  test.skip(!process.env.E2E_REAL_AGENT, 'Set E2E_REAL_AGENT=1 with MySQL/Redis + built JAR')

  test('connects to Spring Boot agent-server and responds to ping', async () => {
    const agent = await startRealAgentServer()
    const { app, page } = await launchDesktopApp({ env: { AGENT_WS_URL: agent.wsUrl } })
    try {
      await gotoWorkspace(page)
      await expect(page.getByText('Agent 未连接')).toHaveCount(0, { timeout: 30_000 })
    } finally {
      await app.close()
      await agent.close()
    }
  })
})
