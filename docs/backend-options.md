# Agent 后端技术选型（已定稿）

> **结论**：后端统一采用 **Spring Boot 3 + MyBatis + MySQL + Redis**，详见 [architecture-backend.md](./architecture-backend.md)。

---

## 定稿栈

| 组件 | 选型 |
|------|------|
| Agent 服务 | Spring Boot 3.3 |
| ORM | MyBatis 3 |
| 数据库 | MySQL 8.0 |
| 缓存 | Redis 7 |
| 实时通信 | Spring WebSocket |
| 构建 | Maven |
| Java | 21 LTS |

---

## 为何不用 Node 做后端

- 团队定 Java/Spring 为主栈，便于与企业系统、MySQL 运维体系集成
- 会话、权限、审计、MCP 配置等 **天然适合关系库**
- Redis 做会话热点、权限决策、限流等缓存
Electron **仅** spawn Java jar，不再使用 Node agent-server。

---

## 与 CLI (TypeScript) 源码的关系

参考 Claude Code 的 `query.ts` / `tools/*` 设计，在 Java 侧等价重写：

| CLI (TS) | Java 对标 |
|----------|-----------|
| `query.ts` | `AgentLoopService` |
| `Tool.ts` | `Tool` 接口 + `ToolRegistry` |
| `toolExecution.ts` | `ToolExecutor` |
| `assembleToolPool` | `ToolRegistry.assemble()` |
| `useCanUseTool` | `PermissionService` + WS 问 UI |

业务逻辑参考 CLI 还原项目，实现语言为 Java。

---

## 混合场景（可选）

若将来需要复用 TS 的 MCP SDK 等，可保留 **Sidecar Node 微服务**，由 Spring Boot 通过 HTTP/gRPC 调用；**主 Agent 仍以 Java 为准**。

---

## 变更记录

| 日期 | 说明 |
|------|------|
| 2026-05-31 | 定稿 Spring Boot + MyBatis + MySQL + Redis |
