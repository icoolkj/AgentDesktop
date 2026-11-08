# WS / IPC 协议

> 类型定义源码：`shared/src/index.ts`  
> Java DTO：`server/.../dto/`  
> 最后更新：2026-06-01

---

## 1. 通信概览

```
Renderer (Vue)
    ↕ preload agentApi (IPC)
Main (Electron)
    ↕ spawn / AGENT_WS_URL
Server (Spring Boot)
    ↕ WebSocket ws://127.0.0.1:{port}/ws
Renderer (Vue)  ← 直连 WS，不经 Main 转发消息
```

| 链路 | 协议 | 用途 |
|------|------|------|
| Renderer ↔ Main | Electron IPC | 获取后端连接信息、重启 Agent |
| Renderer ↔ Server | WebSocket | 聊天、Agent 事件（JSON-RPC + NDJSON） |
| Main ↔ Server | stdout | `AGENT_READY` 启动握手（仅 spawn 模式） |

---

## 2. Electron IPC（preload → main）

暴露对象：`window.agentApi`

| 方法 | 方向 | 说明 |
|------|------|------|
| `getConnection()` | invoke | 返回 `{ port, wsUrl }` |
| `restart()` | invoke | 重启 Java 进程，返回新连接信息 |
| `onDisconnected(cb)` | event | Agent 进程退出时回调，返回 unsubscribe |

### IPC Channel

| Channel | 类型 |
|---------|------|
| `agent:getConnection` | `ipcMain.handle` |
| `agent:restart` | `ipcMain.handle` |
| `agent:disconnected` | `webContents.send`（Main → Renderer） |

### 类型

```typescript
interface AgentConnectionInfo {
  port: number
  wsUrl: string   // 例: ws://127.0.0.1:5566/ws
}
```

---

## 3. 进程启动握手（Main ← Java stdout）

Java 启动完成后输出一行（供 Electron 解析）：

```
AGENT_READY {"type":"agent-ready","port":8080,"wsUrl":"ws://127.0.0.1:8080/ws"}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | `"agent-ready"` | 固定 |
| `port` | `number` | 监听端口 |
| `wsUrl` | `string?` | 完整 WebSocket URL（含 path） |

**外部后端模式**：设置环境变量 `AGENT_WS_URL`，Electron 不 spawn Java，不解析 stdout。

---

## 4. WebSocket 协议

- **URL**：`ws://127.0.0.1:{port}/ws`（仅 localhost）
- **控制面**：JSON-RPC 2.0（单帧 JSON）
- **数据面**：NDJSON 事件流（每行一个 JSON 对象 + `\n`）

### 4.1 JSON-RPC 请求（UI → Server）

```json
{
  "jsonrpc": "2.0",
  "method": "chat.send",
  "params": {
    "message": "explain main.ts",
    "sessionId": "uuid",
    "workspaceRoot": "D:/project",
    "openFilePath": "D:/project/src/main.ts"
  },
  "id": 1
}
```

#### 方法一览（P0）

| method | params | 说明 |
|--------|--------|------|
| `chat.send` | `ChatSendParams` | 发送聊天消息，触发 NDJSON 流 |
| `ping` | — | 健康探测 |

```typescript
interface ChatSendParams {
  message: string
  sessionId?: string
  workspaceRoot?: string   // IDE 打开的工作区根目录，启用 file tools
  openFilePath?: string    // 当前编辑器打开的文件
}
```

### 4.2 JSON-RPC 响应（Server → UI）

成功：

```json
{ "jsonrpc": "2.0", "id": 1, "result": { "ok": true } }
```

失败：

```json
{ "jsonrpc": "2.0", "id": 1, "error": { "code": -32603, "message": "..." } }
```

| code | 含义 |
|------|------|
| `-32600` | Invalid Request |
| `-32601` | Method not found |
| `-32603` | Internal error |

> JSON-RPC 响应与 NDJSON 事件在同一 WebSocket 上混传；UI 按行解析 NDJSON，整帧 JSON 且含 `jsonrpc` 字段的为 RPC 响应。

### 4.3 NDJSON 事件（Server → UI）

每行一个事件，类型定义见 `AgentEvent`：

| type | 字段 | 说明 |
|------|------|------|
| `ready` | `port` | 连接就绪（预留） |
| `text_delta` | `delta` | 流式文本片段 |
| `tool_start` | `toolCallId`, `name` | Tool 开始执行 |
| `tool_result` | `toolCallId`, `name`, `ok`, `output` | Tool 执行结果 |
| `done` | `sessionId?` | 本轮回复结束 |
| `error` | `message` | 业务错误 |

示例流：

```
{"type":"text_delta","delta":"Hello! "}
{"type":"text_delta","delta":"You said: hi"}
{"type":"done","sessionId":"hello"}
```

### 4.4 工具函数（shared）

```typescript
parseNdjsonLine(line: string): AgentEvent | null
formatNdjson(event: AgentEvent): string  // 追加 \n
```

---

## 5. 时序：chat.send

```mermaid
sequenceDiagram
    participant UI as Renderer
    participant WS as AgentWebSocketHandler
    participant Svc as AgentChatService
    participant Loop as AgentLoopService / LlmProvider

    UI->>WS: JSON-RPC chat.send
    WS->>Svc: handleChatSend(params)
    Svc->>Loop: stream / tool loop
    loop stream
        Loop->>UI: NDJSON text_delta / tool_*
    end
    Svc->>UI: NDJSON done
    WS->>UI: JSON-RPC result { ok: true }
```

---

## 6. 后续扩展（P1+，类型待补充）

| method / event | 阶段 | 说明 |
|----------------|------|------|
| `permission_request` / `permission.reply` | P2 | 工具权限确认 |
| `tool_start` / `tool_result` | P1 | Tool 执行事件（已实现） |
| `file_changed` | P2 | 文件变更通知 UI 刷新 |

---

## 7. 变更约定

1. 先改 `shared/src/index.ts`，再改 Java `dto/` 与 Handler
2. 破坏性变更递增 minor 版本或文档 CHANGELOG
3. PR 必须同时更新本文档与 `docs/WBS.md` 对应项
