# Agent Desktop

**一个完全开源的桌面 Agent IDE** —— 把 AI Agent 引擎、代码编辑器、终端和 Git 工作台装进同一个本地应用。

Electron + Vue 3 桌面端 · Spring Boot 3 Agent 后端 · WebSocket 流式协议

**官网**：https://agentdesktop.cn  
**作者**：daipeng (icoolkj)  
**联系**：icoolkj@163.com · QQ 594047667  
**协议**：[Apache License 2.0](./LICENSE) · [NOTICE](./NOTICE)

---

## 这是什么

Agent Desktop 是一个**本地优先、可自己掌控**的 AI 编程工作台：产品形态对标 Cursor 一类 AI IDE，
但 Agent 引擎、工具调用、会话记录、扩展体系都跑在你自己的机器上，模型与数据由你自己配置。

| 能力 | 说明 |
|------|------|
| 类 IDE 工作台 | Monaco 多标签编辑器与 Diff、Ctrl+K 内联改码、文件树、全局搜索、命令面板、主题与图标主题 |
| 内置终端 | xterm.js + node-pty，与工作区联动 |
| Git 工作台 | 变更、提交、日志、同步四视图，Problems 面板 |
| Agent 对话 | 流式输出、工具调用可视化、实时进度、上下文用量、会话历史与 Transcript |
| 安全控制 | ask / auto / plan 权限模式、工具权限规则、检查点与文件回退 |
| 开放工具体系 | 十余个内置工具（Bash、文件读写、Glob/Grep、WebFetch/WebSearch、Skill、Task 等）|
| 扩展机制 | MCP（stdio / HTTP）、Skills、Plugins、Hooks、Slash Commands、Subagent |
| 模型接入 | OpenAI 兼容接口 + Anthropic Messages 映射，模型与参数在设置中可调 |

> API Key 由用户自行填写，经 Electron `safeStorage` 加密保存在本机，项目不内置任何密钥。

## 架构

```
Renderer (Vue 3 + Pinia + Monaco)
    ↕ preload contextBridge (agentApi / workspaceApi / terminalApi ...)
Main (Electron) — java -jar server/target/agent-server-0.1.0.jar
    ↕ WebSocket ws://127.0.0.1:{port}/ws
        控制面：JSON-RPC 2.0      数据流：NDJSON
Spring Boot 3 (Agent Loop → MyBatis → MySQL, Redis)
```

渲染进程直连 WebSocket 收发对话流，主进程只负责 Java 进程的启停与 `AGENT_READY` 握手；
设置 `AGENT_WS_URL` 时可连接外部后端而不在本地 spawn Java（便于 IDEA 联调）。

## 技术栈

| 层 | 技术 |
|----|------|
| 桌面端 | Electron 33 · Vue 3 · Pinia · Monaco · xterm.js · node-pty · better-sqlite3 |
| 构建 | pnpm 9 monorepo · electron-vite · Vite 5 · electron-builder（Win/Mac/Linux） |
| 后端 | Java 21 · Spring Boot 3.3.5 · MyBatis 3 · MySQL 8 · Redis 7 · Maven |
| 契约 | `shared/` TypeScript 类型（前后端协议对齐）· tsup |
| 质量 | ESLint + Prettier · vue-tsc · Vitest · Playwright E2E · JaCoCo 覆盖率门禁 · GitHub Actions CI |

## 快速开始

**环境要求**：Node **20+** · pnpm **9+** · JDK **21+** · Maven **3.9+** · Docker（可选，用于 MySQL + Redis）

详见 **[本地开发说明](./docs/dev-guide.md)**；环境变量示例见 [`.env.example`](./.env.example)。

### 团队约定（日常开发）

**IDEA 跑后端 + `pnpm dev:ui`，不要混用 `pnpm dev:full`。**

```powershell
pnpm infra:up
# IDEA 运行 AgentServerApplication，记下端口，例如 5566
$env:AGENT_WS_URL="ws://127.0.0.1:5566/ws"
pnpm dev:ui          # 或 pnpm dev（等价，仅启动 Electron）
```

| 命令 | 用途 |
|------|------|
| `pnpm dev` / `pnpm dev:ui` | **日常**：只启动 Electron，连 IDEA 里已启动的后端 |
| `pnpm dev:full` | **集成验证**：先 `build:java`，再由 Electron spawn Java（勿与 IDEA 同时跑） |
| `pnpm test` | 前端单测 + Java 单测 |
| `pnpm test:e2e` | Playwright 端到端 |
| `pnpm package:win` / `:mac` | 构建桌面安装包 |

### 单独运行 Java 后端

```bash
pnpm build:java
java -jar server/target/agent-server-0.1.0.jar
```

> 当前以**源码构建**为主要使用方式，安装包发布请关注官网 <https://agentdesktop.cn>。
> `docker-compose.yml` 的默认数据库口令仅供本机开发，共享环境请务必覆盖，
> 详见 [安全政策](./SECURITY.md)。

## 目录结构

```
agent-desktop/
├── desktop/          # Electron 桌面端（Vue 3 + Pinia）
│   └── src/{main,preload,renderer}/
├── server/           # Spring Boot Agent 后端（Java / Maven）
├── shared/           # 前后端共享协议类型（TypeScript）
├── docs/             # 架构与开发文档
├── scripts/          # 构建脚本（build-java 等）
├── docker-compose.yml
└── package.json      # monorepo 根脚本
```

新增功能按模块边界放置：IPC → preload/api → store → view/component；
修改 WS / IPC 协议时同步更新 `shared/src/index.ts`、`server/.../dto/` 与 `docs/ipc-protocol.md`。

## 开源协议

**Agent Desktop 完全开源，采用 [Apache License 2.0](./LICENSE)，遵循协议即可自由使用。**

- ✅ 允许个人使用、商业使用、修改、分发与二次开发，**无需付费、无需申请授权**
- ✅ 没有开源版 / 商业版的功能差异，没有付费墙，也不需要签署 CLA
- ✅ 提交 PR 即表示贡献按 Apache-2.0 授权，贡献代码版权仍归各自作者
- ⚠️ 分发时请保留版权声明与 [NOTICE](./NOTICE)，修改过的文件需标注变更
- ⚠️ 协议不授予商标权：「Agent Desktop」名称与 agentdesktop.cn 仍归原作者
- ⚠️ 软件按「AS IS」提供，不含任何担保

> **关于「完全开源」**：Apache-2.0 是宽松型协议，允许他人在履行署名与 NOTICE 义务的
> 前提下将衍生版本闭源。若你希望所有衍生作品也必须开源，需要改用 GPL / AGPL 等
> Copyleft 协议 —— 那会同时限制商业采用。本项目刻意选择 Apache-2.0，欢迎商业使用。

欢迎 Issue、PR 与任何形式的反馈，参与方式见 [贡献指南](./CONTRIBUTING.md)。

## 项目状态

版本 `0.1.0`，核心链路（编辑器 / 终端 / Agent Loop / 工具 / MCP / 扩展 / 权限 / 会话持久化）已可用，
P0–P3 完成、P4 部分完成、P5（发布通道、自动更新等）待做。完整清单与发布门禁见 [WBS.md](./docs/WBS.md)。

## 文档

- [本地开发说明](./docs/dev-guide.md)
- [WBS 实现清单](./docs/WBS.md)
- [Spring Boot 架构](./docs/architecture-backend.md)
- [WS / IPC 协议](./docs/ipc-protocol.md)
- [主题机制](./docs/theming.md)
- [后端技术选型](./docs/backend-options.md)
- [贡献指南](./CONTRIBUTING.md) · [安全政策](./SECURITY.md)

## 作者

**daipeng (icoolkj)** · https://agentdesktop.cn  
邮箱：icoolkj@163.com · QQ：594047667

Copyright © 2026 daipeng (icoolkj). Licensed under the Apache License, Version 2.0.
