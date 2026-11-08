# Agent Desktop — Spring Boot 后端架构

**定稿栈**：Spring Boot 3 + MyBatis + MySQL + Redis  
**服务目录**：`server/`

---

## 1. 总体架构

```
┌─────────────────────────────────────────────────────────────┐
│  Electron Desktop (React + Monaco)                          │
│  desktop/                                                   │
└───────────────────────────┬─────────────────────────────────┘
                            │ WebSocket (JSON-RPC + NDJSON)
                            │ ws://127.0.0.1:{port}/ws
┌───────────────────────────▼─────────────────────────────────┐
│  Spring Boot Agent Server                                   │
│  server/                                                    │
│  ┌─────────────┐ ┌──────────────┐ ┌─────────────────────┐  │
│  │ WebSocket   │ │ Agent Loop   │ │ Tools (P1+)         │  │
│  │ Handler     │→│ (待实现)      │→│ read/edit/bash...   │  │
│  └─────────────┘ └──────────────┘ └─────────────────────┘  │
│         │                │                                  │
│         ▼                ▼                                  │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────────┐   │
│  │ MyBatis     │  │ Redis Cache  │  │ LLM Provider     │   │
│  │ → MySQL     │  │ 会话/权限缓存 │  │ (可插拔，P1+)    │   │
│  └─────────────┘  └──────────────┘  └──────────────────┘   │
└─────────────────────────────────────────────────────────────┘
         │                    │
         ▼                    ▼
    MySQL 8.0            Redis 7
```

Electron **只连 WebSocket**，不直连 MySQL/Redis；所有持久化与缓存在 Spring Boot 内完成。

---

## 2. 技术栈明细

| 层级 | 技术 | 版本建议 |
|------|------|----------|
| 语言 | Java | 21 LTS |
| 框架 | Spring Boot | 3.3.x |
| ORM | MyBatis | 3.0.x (spring-boot-starter) |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis | 7.x |
| WS | Spring WebSocket | starter-websocket |
| JSON | Jackson | Boot 自带 |
| 构建 | Maven | 3.9+ |

---

## 3. 包结构

```
com.agentdesktop.server
├── AgentServerApplication.java      # 启动类
├── bootstrap/
│   └── AgentReadyNotifier.java      # AGENT_READY → Electron
├── config/
│   ├── WebSocketConfig.java
│   └── RedisConfig.java
├── ws/
│   └── AgentWebSocketHandler.java   # JSON-RPC 入口
├── service/
│   ├── HelloChatService.java        # P0 mock（已有）
│   ├── ChatPersistenceService.java  # MySQL + Redis
│   └── (P1) AgentLoopService.java
├── domain/                          # 实体
├── mapper/                          # MyBatis Mapper 接口
├── web/
│   └── HealthController.java
└── (P1) agent/                      # Agent 循环
    tool/                            # Tools 实现
    permission/                      # 权限 Gate
    mcp/                             # MCP Client
```

---

## 4. 数据存储分工

### 4.1 MySQL（持久化，MyBatis）

| 表 | 用途 | 阶段 |
|----|------|------|
| `chat_session` | 聊天会话 | P0 ✅ |
| `chat_message` | 消息历史 | P0 ✅ |
| `mcp_server_config` | MCP 配置 | P3 |
| `permission_rule` | 权限规则 | P2 |
| `audit_log` | 工具调用审计 | P2 |
| `checkpoint` | 代码快照元数据（P2，文件存磁盘） | P2 |

Schema：`src/main/resources/db/schema.sql`

### 4.2 Redis（缓存，按需）

| Key 模式 | 用途 | TTL |
|----------|------|-----|
| `agent:session:{id}` | 活跃会话存在标记 | 30min |
| `agent:permission:{hash}` | 权限决策缓存 | 5min |
| `agent:tool:result:{id}` | 大 tool 结果临时缓存 | 10min |
| `agent:rate:{client}` | 限流 | 1min |

**原则**：MySQL 为 source of truth；Redis 可丢、可重建。

---

## 5. WebSocket 协议（与前端 shared-types 对齐）

### UI → Agent（JSON-RPC）

```json
{ "jsonrpc": "2.0", "method": "chat.send", "params": { "message": "hi", "sessionId": "hello" }, "id": 1 }
{ "jsonrpc": "2.0", "method": "permission.reply", "params": { "id": "...", "allow": true }, "id": 2 }
{ "jsonrpc": "2.0", "method": "ping", "id": 3 }
```

### Agent → UI（NDJSON，每行一个 JSON）

```json
{"type":"text_delta","delta":"Hello! "}
{"type":"tool_start","id":"...","name":"Edit","input":{}}
{"type":"done","sessionId":"hello"}
{"type":"error","message":"..."}
```

### Electron 启动握手

Spring Boot 启动完成后 stdout：

```
AGENT_READY {"type":"agent-ready","port":8080,"wsUrl":"ws://127.0.0.1:8080/ws"}
```

---

## 6. 配置与环境变量

`application.yml` 支持环境变量覆盖：

| 变量 | 默认 | 说明 |
|------|------|------|
| `AGENT_SERVER_PORT` | `0`（随机） | HTTP/WS 端口 |
| `MYSQL_HOST` | `127.0.0.1` | MySQL 主机 |
| `MYSQL_PORT` | `3306` | |
| `MYSQL_DATABASE` | `agent_desktop` | |
| `MYSQL_USER` | `root` | |
| `MYSQL_PASSWORD` | *（必填）* | MySQL 密码 |
| `REDIS_HOST` | `127.0.0.1` | |
| `REDIS_PORT` | `6379` | |

---

## 7. 本地开发

### 7.1 启动 MySQL + Redis

```bash
docker compose up -d
```

### 7.2 构建并运行 Spring Boot

```bash
cd server
mvn package -DskipTests
java -jar target/agent-server-0.1.0.jar
```

### 7.3 启动 Electron

```bash
# 根目录（会先 build Java，再启动 Electron）
pnpm dev
```

Electron 主进程 **仅** spawn Java jar（`server/target/agent-server-0.1.0.jar`）。

---

## 8. 历史说明

Node 版旧 agent-server 已删除。Electron **仅** spawn Java JAR。

---

## 9. Agent Loop（已实现，对齐 OpenAI function-calling / ReAct）

与 Cursor、Claude Code、OpenAI Agents SDK 相同的两阶段模型：

```
用户消息
  ↓
┌─ 规划轮 (stream=false, tools=auto) ─────────────────┐
│  LLM 返回 tool_calls?                               │
│    是 → 执行工具 → 追加 assistant+tool 消息 → 重复   │
│    否 → 若有 text：直接输出；否则进入回答轮          │
└─────────────────────────────────────────────────────┘
  ↓（达到 max iterations 时跳过工具，强制回答轮）
┌─ 回答轮 (stream=true, tool_choice=none) ────────────┐
│  SSE 流式 text_delta → 用户                          │
└─────────────────────────────────────────────────────┘
```

实现位置：`agent/loop/AgentLoopService.java`

| 原则 | 说明 |
|------|------|
| 规划用非流式 | 可靠解析 `tool_calls`，不在 UI 上闪半成品 |
| 回答用流式 | 仅最后一轮对用户 `text_delta` 流式输出 |
| 不重复调 LLM | 规划轮已返回纯文本时，直接交付，不再二次请求 |
| 工具输出限长 | `ToolResult.forModelContext()` / `forUiPreview()` |
| 大文件 | `read_file` 支持 `offset` + `limit`（默认 500 行） |

### P1+ 待演进

1. **PermissionService** — 写文件/命令前 Ask 模式确认  
2. **ToolExecutor** — 只读工具并行、写工具串行  
3. **grep / search_replace** — 补齐编辑类工具  
4. **McpClientService** — MCP Java SDK  
5. **CheckpointService** — Diff 前快照  

LLM 调用封装为 `LlmProvider` 接口，不绑死具体厂商。

---

## 10. 变更记录

| 日期 | 说明 |
|------|------|
| 2026-06-02 | 默认使用原生 tool_calls；弃用 DSML 解析路径 |
| 2026-05-31 | 定稿 Spring Boot + MyBatis + MySQL + Redis；P0 Java 脚手架 |
