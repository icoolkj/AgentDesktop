# 贡献指南

项目主页：https://agentdesktop.cn  
维护者：daipeng（icoolkj@163.com · 594047667@qq.com）

## 开源协议

本项目采用 [Apache License 2.0](./LICENSE)，**完全开源、无商业授权例外、无开源版/商业版功能差异**。

- 提交 PR 即表示你同意该贡献按 Apache-2.0 授权，**无需签署 CLA，也不要求转让版权**；
  你贡献的代码版权仍归你所有。
- 请保留源码中既有的版权声明与 [NOTICE](./NOTICE)；修改过的文件请自行标注变更。
- 请勿在 PR 中提交密钥、`.env`、`env.local.ps1`、`secrets.enc` 或任何本机数据文件。

## 开发环境

- Node **20+**、pnpm **9+**
- JDK **21+**、Maven **3.9+**
- Docker（MySQL + Redis，可选）

详见 [dev-guide.md](./docs/dev-guide.md)。

## 团队约定

- **日常开发**：IDEA 跑 `AgentServerApplication` → 设 `AGENT_WS_URL` → `pnpm dev:ui`
- **不要**在 IDEA 已启动后端时使用 `pnpm dev:full`（端口冲突）

## 提交前检查

```powershell
pnpm lint
pnpm typecheck
pnpm test
pnpm build:java    # 改了 server/ 时
pnpm build         # 改了 desktop/ 时（可选）
```

## 目录约定

| 目录 | 用途 |
|------|------|
| `desktop/` | Electron + Vue 3 + Pinia |
| `server/` | Spring Boot 后端 |
| `shared/` | WS/IPC 协议类型（前后端契约） |

新增功能按 **模块边界** 放置：IPC → preload/api → store → view/component。

## 协议变更

修改 WebSocket / IPC 协议时：

1. 更新 `shared/src/index.ts`
2. 更新 `server/.../dto/`
3. 更新 [docs/ipc-protocol.md](./docs/ipc-protocol.md)

## PR 规范

- 使用 [PR 模板](.github/PULL_REQUEST_TEMPLATE.md)
- 一个 PR 聚焦一个主题
- 关联 WBS 条目（如有）

## 代码风格

- TypeScript / Vue：ESLint + Prettier（`pnpm lint` / `pnpm format`）
- Java：遵循现有包结构，构造器注入，SLF4J 日志
- 注释：仅解释非 obvious 的业务/协议逻辑
