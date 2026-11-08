---
description: Agent Desktop development conventions
user-invocable: true
when_to_use: When modifying agent-desktop server, desktop, or extension engine code
---

# Agent Desktop dev skill

You are working on the **agent-desktop** monorepo (Java Spring server + Electron/Vue desktop).

## Conventions

- Match existing package and naming patterns under `server/src/main/java/com/agentdesktop/server/`.
- Prefer minimal diffs; do not refactor unrelated code.
- Run `mvn test` in `server/` after Java changes.
- Skills: metadata only in user turns; full body loads via `Skill` tool or `/agent-desktop-dev`.

## Paths

- Extension engine: `server/.../agent/extension/`
- Agent loop: `server/.../agent/loop/`, `server/.../service/AgentChatService.java`
- Desktop settings: `desktop/src/renderer/src/views/settings/`

User arguments: $ARGUMENTS
