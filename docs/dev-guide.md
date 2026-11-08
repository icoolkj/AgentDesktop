# 本地开发说明

## 团队约定

| 场景 | 做法 |
|------|------|
| **日常开发** | IDEA 运行/调试 `AgentServerApplication` → 设置 `AGENT_WS_URL` → `pnpm dev:ui` |
| **不要** | IDEA 已启动后端时，再跑 `pnpm dev:full`（会再起一个 Java，端口冲突） |
| **集成验证** | 无 IDEA 时，用 `pnpm dev:full` 一条命令（build jar + Electron spawn Java） |

`pnpm dev` 与 `pnpm dev:ui` **等价**，均只启动 Electron，不执行 `build:java`。

---

## 日常开发（方式 A，默认）

1. 启动基础设施（可选，持久化需要）：

```powershell
pnpm infra:up
```

2. IDEA 运行/调试 `server/` 下的 `AgentServerApplication`  
   控制台出现：`AGENT_READY {"type":"agent-ready","port":5566,"wsUrl":"ws://127.0.0.1:5566/ws"}`

3. **另开终端**，设置 WS 地址（端口以 IDEA 控制台为准）：

```powershell
cd D:\Idea2024\Workspace2025\agent-desktop
$env:AGENT_WS_URL="ws://127.0.0.1:5566/ws"
pnpm dev:ui
```

Electron **不会** spawn Java，直接连 IDEA 里已启动的后端。

> 可将环境变量写入 `scripts/env.local.ps1`，启动前执行 `. .\scripts\env.local.ps1`（`build:java` 会自动加载该文件，`dev:ui` 需手动 source）。

---

## 集成验证（方式 B，非日常）

适用于：CI、新环境冒烟、不想开 IDEA 时。

1. 终端 Maven **3.9+**，`JAVA_HOME` 指向 **JDK 21**
2. `pnpm infra:up`（可选）
3. 运行：

```powershell
pnpm dev:full
```

流程：`build:java` → 启动 Electron → Electron `java -jar` 拉起后端。

> ⚠️ **勿与方式 A 同时使用**，否则会两个 Java 进程抢端口。

---

## 为什么 IDEA 里后端已启动，跑 `pnpm dev:full` 还报错？

这是 **两件独立的事**：

| 步骤 | 你在做什么 | 结果 |
|------|------------|------|
| IDEA 运行 `AgentServerApplication` | IntelliJ 用 **内置/配置的 Maven + JDK 21** 编译并启动 | ✅ 后端正常，`AGENT_READY` 已打印 |
| `pnpm dev:full` | 脚本 **先** 在终端跑 `mvn package`，**再** 启动 Electron | ❌ 终端里的 Maven 若是 **3.5.0**，可能 **构建失败** |

**结论**：不是「后端没启动」，而是 **`dev:full` 在启动 Electron 之前，命令行 Maven 打包可能失败**。  
日常开发请用 **方式 A**，不要依赖 `dev:full`。

---

## 界面显示 CONNECTED 但报「Unable to connect to Redis」？

说明 **WebSocket 已连上**，但 **Redis（或 MySQL）未启动**，持久化会降级。

**处理：**

1. **快速验证聊天**：IDEA 重启 `AgentServerApplication`，再发一条消息，应能看到流式回复。
2. **完整持久化**：

```powershell
pnpm infra:up
```

等待 MySQL/Redis 就绪后，IDEA 重启 `AgentServerApplication`。

---

## 提交前检查

```powershell
pnpm lint
pnpm typecheck
pnpm test
```

详见 [CONTRIBUTING.md](../CONTRIBUTING.md)。

---

## 环境变量

| 变量 | 作用 |
|------|------|
| `AGENT_WS_URL` | **日常必设**（IDEA 调试时）：`ws://127.0.0.1:端口/ws`，Electron 连外部后端，不 spawn Java |
| `AGENT_JAVA_HOME` | `dev:full` 时 Electron spawn jar 用的 JDK（**需 17+**） |
| `JAVA_HOME` | 指向 JDK 21 |
| `MYSQL_*` / `REDIS_*` | 见 `server/src/main/resources/application.yml` |

---

## Maven / JDK 用错版本？

终端里执行 `mvn -version`，若仍是 **3.5.0** 或 **Java 8**，说明 **PATH 里旧 Maven 排在前面**。

**处理办法（任选其一）：**

1. **推荐**：复制 `scripts/env.local.example.ps1` → `scripts/env.local.ps1`，填好 Maven 3.9.9、JDK 21 路径，以及常用 `AGENT_WS_URL`（`pnpm build:java` 会自动加载）

2. **改系统 PATH**：把 `D:\Maven\apache-maven-3.9.9\bin` 移到 3.5.0 **之前**，并设 `JAVA_HOME=D:\Program Files\Java\jdk-21.0.6`

3. **当前终端临时生效**：

```powershell
$env:MAVEN_HOME="D:\Maven\apache-maven-3.9.9"
$env:JAVA_HOME="D:\Program Files\Java\jdk-21.0.6"
$env:Path="$env:MAVEN_HOME\bin;$env:JAVA_HOME\bin;$env:Path"
mvn -version   # 应显示 3.9.9 + Java 21
pnpm dev:full  # 仅集成验证时使用
```

---

## 自举工作区（用 Agent Desktop 开发 Agent Desktop）

若打开的工作区路径 **包含本仓库**（例如 `agent-desktop` 根目录），则：

- Agent **Review / 保存** 写入 `desktop/src/**` 会触发 **electron-vite 热更新**
- 历史上曾导致：终端 PTY 退出、界面假死（已通过布局与 batch 写盘缓解）

**建议：**

1. 日常改 UI：继续 dogfooding 可以，但注意 Review 会改源码并 HMR
2. 批量 accept 前：确认终端里没有在跑第二个 `pnpm dev`
3. 详见 **[有状态组件生存指南](./stateful-survival.md)**

打开自举工作区时，应用会在 dev 模式 **Toast 提示** 一次。
