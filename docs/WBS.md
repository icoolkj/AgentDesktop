# Agent Desktop 实现清单（WBS）

> 用于功能核对与进度跟踪。状态标记：`[x]` 已完成 · `[~]` 进行中 · `[ ]` 未开始 · `[-]` 暂不实施  
> **对齐状态列**：`Done` · `Partial` · `Gap` · `N/A`

**最后更新**：2026-06-08（P4-B B1 Inline Edit Ctrl+K · M5 完成 · P4-B ~17%）  
**官网**：https://agentdesktop.cn · **作者**：daipeng  
**技术栈定稿**：Electron 33 + electron-vite + **Vue 3 + Pinia** + **Spring Boot 3 + MyBatis + MySQL + Redis**

**对标产品**：Cursor IDE（目标产品形态）  
**参考架构**：Claude Code（Agent 引擎 + Transcript 参考）  
**CC 源码参考树**：`D:\Idea2024\Workspace2025\claude_code_src-master`（2.1.88 TS 反编译树；引擎语义对照源）  
**LLM 网关**：CLIProxyAPI-Java（CPA，原生 `tool_calls`）

> **生产发布原则**：功能进度 ≠ 可上生产。凡 §7 **P0 逻辑风险** 未关闭，**禁止**对外生产发布（含企业内网正式环境）。

---

## 0. 项目总览

| 阶段 | 名称 | 目标 | 状态 | 进度 |
|------|------|------|------|------|
| P0 | 脚手架 + 连通 | electron-vite、Agent 进程、WS、Chat | `[x]` | 100% |
| P1 | 引擎 + 编辑器 MVP | Agent 循环、Monaco、Tools、JSONL、IDE 联动 | `[x]` | 100% |
| P2 | 产品化 | Diff、Checkpoint、权限、@上下文、Review | `[x]` | 100% |
| P3 | 生态 | MCP、终端、打包发布 | `[x]` | 100% |
| P4 | 增强 | 稳定交付、Cursor 核心 UI、Claude 生态 | `[~]` | **~62%** |
| P5 | 长期 | RAG、Tab 补全、扩展生态 | `[ ]` | 0% |

### 0.1 整体对齐进度（严格估算）

> **口径**：引擎 = `server/` 可执行语义；完整产品 = 引擎 + 扩展运行时 + 对标 CC /npm 产品的工具/Hook/子 Agent 全集；Cursor = `desktop/` IDE 壳 + 交互。

| 维度 | 进度 | 说明 |
|------|------|------|
| **WBS 规划内（P0–P4）** | **~96%** | P0–P3 完成 · **P4-A ~95%** · **P4-B ~17%（B1 ✅）** · P4-C ~95% · P4-D ~85% |
| **Claude Code 引擎核心** | **~93%** | P4-A0 写盘/rewind 时序已对齐 · Bash 容器 → P5-3 |
| **Claude Code 完整产品** | **~76%** | 7/28 Hook 事件 · 无 sandbox-runtime · 缺插件市场/Computer Use |
| **Cursor IDE 产品形态** | **~64%** | 壳 + Agent + Git 强 · **Ctrl+K ✅** · @codebase / Tab / Problems 缺 |
| **生产就绪（Production Gate）** | **🟢 已通过** | §7 P0 四项已关闭 · R2 容器级隔离仍属 P5 |

#### 0.1.1 Claude Code 分域进度（对照 npm 产品能力，非 GitHub 示例仓）

| 域 | 权重 | 进度 | 缺口摘要 |
|----|------|------|----------|
| A. Agent 循环与上下文 | 20% | **98%** | autocompact · pairing · 流式策略已对齐 · 无 StreamingToolExecutor |
| B. 内置文件/搜索工具 | 15% | **95%** | read/write/search_replace/delete + **read-before-write + IDE buffer 校验** ✅ |
| C. 终端与 Bash | 10% | **85%** | strict 强化（sudo/powershell/curl\|sh）· 非容器 → P5-3 |
| D. 权限与 Checkpoint | 10% | **95%** | trackEdit 成功后再 backup ✅ · rewind/Review 同步 ✅ |
| E. Transcript / 斜杠 | 10% | **90%** | JSONL · /compact / /context · 自定义 commands ✅ |
| F. MCP | 10% | **85%** | stdio + http/sse ✅ · OAuth/复杂 SSE **~50%** |
| G. 扩展工具 | 10% | **88%** | Task · WebSearch · WebFetch ✅ |
| H. Hooks 运行时 | 8% | **40%** | 7 事件 command-only · CC 共 28 事件 + prompt/http/agent 型 |
| I. Skills / 插件体系 | 7% | **80%** | 目录 + `plugin.json` · 无 SkillTool · 无图形市场 |
| J. 子 Agent 体系 | 10% | **85%** | Task + 3 类型 · NDJSON · 无 worktree 隔离 · UI 卡片弱 |

**引擎核心（A+B+D+E 加权）≈ 91%** · **完整产品（A–J 加权）≈ 76%**

#### 0.1.2 Cursor IDE 分域进度

| 域 | 权重 | 进度 | 缺口摘要 |
|----|------|------|----------|
| 布局与导航 | 15% | **88%** | 三栏 · Activity Bar · 多窗口 ✅ |
| 编辑器 | 20% | **82%** | Monaco ✅ · **Ctrl+K ✅** · Split/面包屑弱 |
| Agent / Composer | 25% | **78%** | 工具卡片 · @上下文 · 📎 · 模型下拉 ✅ · 变更列表弱 |
| Git / 搜索 | 15% | **83%** | 四视图 · ripgrep ✅ |
| 终端 | 10% | **85%** | 多窗口 PTY · 主题 ✅ |
| 诊断 / 状态栏 | 10% | **15%** | **Problems ❌** · 状态栏弱 |
| 智能补全 | 5% | **0%** | Tab ghost text → P5 |

**Cursor 综合 ≈ 64%**

### 0.2 里程碑进度条

```
M0 脚手架 + WS + 连通          ████████████████████ 100%
M1 Agent + Tools + Monaco      ████████████████████ 100%
M2 Diff + 权限 + @上下文        ████████████████████ 100%
M3 MCP + 终端 + 安装包          ████████████████████ 100%  ← P3 完成
M4 产品打磨 + 稳定交付         ███████████████████░  95%   ← P4-A 主体完成 · A2b 可选 nightly
M5 Cursor 核心（Inline Edit）  ████░░░░░░░░░░░░░░░░  20%   ← P4-B B1 ✅ · B2–B6 待办
M6 Claude 生态（扩展引擎）     █████████████████░░░  85%   ← P4-C 主体完成
```

### 0.3 Claude Code 参考说明

| 对象 | 是什么 | 对 Agent Desktop 的意义 |
|------|--------|-------------------------|
| **`claude-code` GitHub 仓库** | 官方插件、hooks 示例、settings 示例 | **不是** CLI 引擎源码；生态 / schema 参考 |
| **Claude Code 产品（npm/二进制）** | `queryLoop`、内置工具、TUI、hooks 运行时 | Java **重实现**引擎语义（`AgentLoopService` 等） |
| **`claude_code_src` 参考树** | `claude_code_src-master/`（本地路径见文首） | **引擎语义对照源**：`query.ts` · `FileWriteTool.ts` · `hooks.ts` · `fileHistory.ts` |
| **已移植 TS** | transcript / permissions / mediaLimits | `desktop/src/main/transcript/` · `shared/` |
| **Java 重实现** | 上列 CC 模块 | `server/`（`AgentLoopService` · `ToolExecutor` · `HookRunner` 等） |

**结论**：引擎层对齐 Claude Code；**产品形态对齐 Cursor IDE**。

### 0.4 P3 交付清单

| 优先级 | 任务 | 状态 |
|--------|------|------|
| P3-1 | MCP 配置 UI + REST API + inputSchema 透传 | `[x]` |
| P3-2 | electron-builder 安装包（Win/macOS/Linux） | `[x]` |
| P3-3 | 命令面板 `Ctrl+Shift+P` + 斜杠命令发现 | `[x]` |
| P3-4 | E2E 接入 CI（Windows job） | `[x]` |
| P3-5 | OS 密钥链（Electron safeStorage） | `[x]` |

### 0.5 P4-A 产品打磨（近期已完成，未单独开阶段）

- [x] 多窗口 / 工作区启动契约（`restore` / `blank` / `workspace` · IPC `getWindowBoot`）
- [x] 文件菜单 IDE 化（新建窗口 · 打开文件夹 · 最近打开 · Shift+新窗口）
- [x] 每窗口独立 PTY 终端（按 `webContents.id` 隔离）
- [x] 查看菜单重组（资源管理器 / 搜索 / Git · 终端切换 · 定位文件）
- [x] 在资源管理器中定位（自动切 Explorer + 展开路径）
- [x] 红色主题终端 ANSI 对比度 / 暖色背景调和
- [x] 帮助文档：窗口与项目 · 查看菜单

---

## 1.0 项目管理与基础设施

### 1.1 仓库与工程

- [x] Monorepo（pnpm workspace：`desktop` / `server` / `shared`）
- [x] CI：GitHub Actions（build + typecheck + test + **E2E Windows**）
- [x] ESLint + Prettier + EditorConfig · `CONTRIBUTING.md`
- [x] 文档：`architecture-backend.md` · `ipc-protocol.md` · `theming.md` · `stateful-survival.md`
- [x] Playwright E2E 冒烟 + Agent WS 路径（`agent-ws.spec.ts` · mock WS）
- [x] 打包：electron-builder（`pnpm package:win` / `package:mac`）
- [x] 密钥：Electron safeStorage（`secretsApi` IPC）
- [x] **§7 P0 逻辑风险全部关闭**（生产发布门禁 · 见 §7.1）
- [x] agent-core JaCoCo 门禁（`com.agentdesktop.server.agent` ≥55% · **~95 tests**）· 真实 Java WS E2E 可选（`E2E_REAL_AGENT=1`）

---

## 5.0 P3 — 生态与发布（100%）

- [x] xterm.js + node-pty 集成终端（Windows prebuild 方案）
- [x] `run_terminal_cmd` / `BashTool.java`
- [x] MCP Client（`McpToolBridge` · `~/.agent-desktop/mcp.json` · **真实 inputSchema**）
- [x] **MCP 配置 UI**（设置 → Agent → MCP 服务器 · 测试连接 · 启用/禁用）
- [x] **MCP REST API**（`GET/PUT /api/settings/mcp` · `POST .../test/{name}`）
- [x] **命令面板** · **斜杠命令发现 UI**（`/compact` `/context` `/rewind`）
- [x] **electron-builder** · JAR 随包分发（`extraResources/agent-server.jar`）
- [x] Playwright E2E 冒烟 + **CI Windows job**
- [x] E2E：mock Agent WS · 附件 transcript 回放 · smoke
- [ ] agent-core Jacoco **>60%**（当前门禁 55% · 持续补测）

---

## 7.0 生产逻辑风险清单（CC 对照 · 发布门禁）

> **用途**：对照 `claude_code_src-master` 主路径语义，列出**可导致生产数据损坏、安全越界或 UI/磁盘状态不一致**的逻辑缺口。  
> **严重度**：`P0` 阻塞生产 · `P1` 下一迭代必修 · `P2` 已知差异/体验债  
> **状态**：`[ ]` 未修 · `[~]` 部分缓解 · `[x]` 已关闭  
> **CC 对照列**：CC 源码路径 + 关键行为摘要

### 7.1 发布门禁（Production Gate）

**对外生产发布（含企业内网正式环境）前，§7.2 全部 P0 必须为 `[x]`。**

| 门禁项 | 条件 | 当前 |
|--------|------|------|
| G1 | P0 逻辑风险 0 项未关闭 | 🟢 **4/4 已关闭** |
| G2 | `mvn test` + JaCoCo agent-core ≥55% | 🟢 ~95 tests · 目标 60% |
| G3 | Agent WS E2E | 🟢 mock + 附件回放 · A2b 可选 |
| G4 | `/rewind` 后 Review 列表与磁盘一致 | 🟢 R4 已修 · `rewind_restored` NDJSON |
| G5 | renderer store 单测 | 🟢 sessionChanges + chatStore |

### 7.2 风险登记表（按严重度）

#### P0 — 阻塞生产（数据损坏 / 安全 / 磁盘-UI 静默不一致）

| ID | 风险描述 | CC 对照（源码 · 行为） | AD 现状 | 状态 | 修复任务 | 验收标准 |
|----|----------|------------------------|---------|------|----------|----------|
| **R1** | **覆盖写盘无 read-before-write** | `FileWriteTool.ts`：`readFileState` + mtime | `ReadFileStateService` + `ToolExecutor` 写前校验 · **+ IDE buffer 校验** | `[x]` | A0-1 ✅ | 单测 `ReadFileStateServiceTest` · `ToolExecutorWritePathTest` |
| **R2** | **Bash 沙箱可绕过** | `@anthropic-ai/sandbox-runtime` | `BashSandboxService`：**sudo/powershell/curl\|sh/dd/nc** 等 · strict 默认 | `[x]` | A0-2 ✅ · 容器 → P5-3 | `BashSandboxServiceTest` 9 用例 |
| **R3** | **`file_changed` 静默丢弃** | CC 内部事件链 | `NdjsonEventPublisher.sendReliable` + ERROR 日志 + tool **syncWarning** | `[x]` | A0-3 ✅ | emit 失败时 tool_result 含 ⚠️ |
| **R4** | **`/rewind` 与 Review 不同步** | `fileHistory.ts` + rewind | `rewind_restored` WS · `sessionChangesStore.clear*` · `RewindService` 清 readState | `[x]` | A0-4 ✅ | rewind 后 Review 空 · 编辑器 reload |

#### P1 — 下一迭代必修（checkpoint 污染 / 测试置信度 / 命名移植）

| ID | 风险描述 | CC 对照 | AD 现状 | 状态 | 修复任务 | 验收标准 |
|----|----------|---------|---------|------|----------|----------|
| **R5** | **trackEdit 在 tool 成功前调用** | write 工具：**成功写盘后**才 backup | `ToolExecutor`：**仅 `result.ok()` 后** trackEdit | `[x]` | A0-5 ✅ | 失败 write 不污染 checkpoint |
| **R6** | **auto 模式不可逆写盘**：与 CC 一致但用户易误操作 | CC 同：直接 `writeTextContent` | ask/plan 有门禁；auto 直写 + diskAhead UI ✅ | `[~]` | **P4-A0-6**：保持 CC 语义；强化 banner/toast（已实现）+ 帮助文档 | auto 下覆盖未保存 buffer 时用户可见 ⚠️ |
| **R7** | **工具名与 CC 不一致**：移植 prompt/skill 时模型调错工具 | CC：`Write`/`Edit`/`Bash`/`Agent` | AD：`write_file`/`search_replace`/`run_terminal_cmd`/`Task` | `[ ]` | **P4-C 收尾**：`ToolRegistry` alias 或 system prompt 双名映射 | alias 注册后 CC 示例 prompt 可跑通 |
| **R8** | **Hooks 仅 7/28 事件 · command-only** | `utils/hooks.ts`：28 事件 · prompt/http/agent 型 | `HookRunner` + `HookEvents`：7 事件 | `[~]` | **P4-D**：按优先级补 SessionEnd/Notification/PreCompact 等 | 与 CC settings.json 示例事件集对齐文档 |
| **R9** | **E2E 用 mock WS，非真实 Java Agent** | — | `mockAgentServer.ts` 通过 · **未**连 `agent-server.jar` | `[~]` | **P4-A A2b**：CI 可选 job 启动 JAR + Playwright | 同一 spec 对 mock 与 real 双跑或 real 为主 |

#### P2 — 已知差异 / 体验债（不阻塞首发，须文档化）

| ID | 风险描述 | CC 对照 | AD 现状 | 状态 | 说明 |
|----|----------|---------|---------|------|------|
| **R10** | **`delete_file` 为 AD 扩展** | CC **无**内置 delete 工具 | `DeleteFileTool.java` + permission + `changeKind: deleted` | `[x]` | 有意扩展；文档与权限规则已覆盖 |
| **R11** | **Staging RPC 已移除** | CC 主路径亦**直接写盘** | 已删 `StagedWriteService` / `changes.*` WS | `[x]` | 与 CC `FileWriteTool` 一致；Review=Reject 回滚 |
| **R12** | **StreamingToolExecutor 缺失** | `StreamingToolExecutor.ts` 流式工具输出 | Java 一次性返回 tool result | `[ ]` | P5；不影响写盘正确性 |
| **R13** | **SkillTool / 插件市场** | CC `SkillTool` + marketplace | 目录加载 only | `[ ]` | P4-C / P5 |
| **R14** | **Task 无 git worktree 隔离** | CC `utils/worktree.ts` | 同 workspace 并发写 | `[ ]` | 多 Task 并行时依赖 R1 |

### 7.3 CC 主路径 vs AD 主路径（写盘链路）

```
CC (FileWriteTool.ts)                    AD (ToolExecutor + WriteFileTool)
─────────────────────                    ─────────────────────────────────
read → readFileState 记录 mtime           read → 无 session 级 read 快照
write → 校验 mtime 未变 → writeText      write → 直接 Files.writeString
       → fileHistory backup（成功后）            → trackEdit（⚠️ 成功前）
       → UI/TUI 刷新                            → WS file_changed（⚠️ 失败静默）
Reject N/A（直写）                        Reject → checkpoint restore
/rewind → fileHistory restore             /rewind → RewindService（⚠️ Review UI 未清）
```

**结论**：AD 与 CC **同为直接写盘**（R11），**R1/R3/R4/R5 已与 CC 时序对齐**；R2 regex 基线**严于 CC**，容器级隔离留 P5-3。

### 7.4 P4-A0 生产逻辑加固（§7 P0/P1 修复包 · 优先于 P4-B）

| # | 任务 | 风险 | 状态 | 交付 |
|---|------|------|------|------|
| A0-1 | Read-before-write + readFileState | R1 | `[x]` | `ReadFileStateService` + IDE buffer 校验 |
| A0-2 | Bash 沙箱强化 + bypass 用例 | R2 | `[x]` | 9 单测 · 非容器 documented |
| A0-3 | file_changed 失败可观测 | R3 | `[x]` | sendReliable + syncWarning |
| A0-4 | /rewind ↔ Review/renderer 同步 | R4 | `[x]` | `rewind_restored` + store clear |
| A0-5 | trackEdit 移至成功写盘后 | R5 | `[x]` | ToolExecutor 调整 |
| A0-6 | auto 模式 diskAhead UX（文档） | R6 | `[x]` | toast · tab `!` · banner · systemHelp |
| A0-7 | 真实 Java WS 集成 E2E | R9 | `[~]` | `agent-ws-real.spec.ts` · skip 除非 `E2E_REAL_AGENT=1` |

**P4-A0 验收 = §7.1 G1–G4 全部通过。** ✅

#### P4-A0 生产逻辑加固 ✅（见 §7.4）

#### P4-A 产品打磨 + 稳定交付 ✅（2026-06-08）

| # | 任务 | 状态 | 交付标准 |
|---|------|------|----------|
| A0 | **§7 P0 全部关闭** | `[x]` | §7.1 G1–G4 通过 |
| A1 | 附件 transcript 持久化 + 消息缩略图 | `[x]` | JSONL `adAttachments` · `attachments-transcript.spec.ts` · `data-testid=user-attachment` |
| A2 | E2E Agent WS（mock） | `[x]` | `agent-ws.spec.ts` Accept/Reject |
| A2b | E2E 真实 Java WS | `[~]` | `realAgentServer.ts` + `agent-ws-real.spec.ts`（需 MySQL/Redis） |
| A3 | agent-core JaCoCo 门禁 | `[~]` | **55%** enforced · 目标 **60%** · Permission/Rewind/Ndjson/WS 单测 |
| A4 | renderer 关键 store 单测 | `[x]` | sessionChangesStore · **chatStore** · persistUserAttachments |
| A5 | WBS / systemHelp 与实现同步 | `[x]` | 本文档 + 帮助已更新 |

**P4-A 验收**：生产门禁通过 · 附件历史可回放 · E2E 冒烟 · Java/Renderer 单测 CI 绿。

---

## 8.0 对标对齐清单

### 8.1 摘要（Cursor × Claude Code × Agent Desktop）

| 能力 | Cursor | Claude Code | Agent Desktop | 对齐 | 阶段 |
|------|--------|-------------|---------------|------|------|
| Staged Apply + Review | ✅ | 部分 | ✅ 直接写盘 + Reject 回滚 | Done | P2 |
| `/rewind` | ✅ | ✅ | ✅ | Done | P2 |
| Permission 规则文件 | ✅ | ✅ | ✅ + 设置 UI | Done | P2 |
| Checkpoint 跨重启 | ✅ | ✅ | ✅ JSON 持久化 | Done | P2 |
| 集成终端 | ✅ | — | ✅ PTY + 主题 + 多窗口 | Done | P3/P4-A |
| `run_terminal_cmd` | ✅ | ✅ | ✅ BashTool | Done | P3 |
| MCP 配置 UI | ✅ | ✅ | ✅ 设置页 + 测试连接 | Done | P3 |
| MCP inputSchema | ✅ | ✅ | ✅ 透传 | Done | P3 |
| 命令面板 | ✅ | 部分 | ✅ Ctrl+Shift+P | Done | P3 |
| 斜杠命令发现 | ✅ | ✅ | ✅ Composer 自动补全 | Done | P3 |
| 聊天附件（📎 图片/文件） | ✅ | ✅ | ✅ multimodal | Done | P4-1 |
| 多窗口 / 最近项目 | ✅ | — | ✅ | Done | P4-A |
| 安装包 | ✅ | — | ✅ electron-builder | Done | P3 |
| Inline Edit（Ctrl+K） | ✅ | ✅ | ✅ | Done | P4-B B1 ✅ |
| @codebase / 语义索引 | ✅ | — | ❌ | **Gap** | P4-B |
| Tab 补全 | ✅ | — | ❌ | **Gap** | P5 |
| Hooks 运行时 | — | ✅ | ✅ 7 事件 command | Partial | P4-D · **§7 R8** |
| 插件 / Skills 市场 | — | ✅ | ✅ 目录加载 | Partial | P4-C |
| 子 Agent（Task 工具） | 部分 | ✅ | ✅ | Partial | P4-C |
| WebSearch / WebFetch | — | ✅ | ✅ MySQL features | Done | P4-C |
| MCP HTTP/SSE | ✅ | ✅ | ✅ http/sse transport | Partial | P4-C |
| 扩展引擎设置 UI | — | 部分 | ✅ 设置 + 帮助文档 | Done | P4-C |
| MySQL/Redis 配置落库 | — | — | ✅ LLM/MCP/features | Done | P4-C |

**超越 Claude Code**：MCP 设置页；命令面板；Review batch；权限规则可视化；完整 IDE 壳（Git 四视图 / 搜索 / 多窗口）。

**超越 Cursor（CLI 产品无）**：JSONL 本地 transcript；`/compact` `/context` `/rewind`；Claude 式 checkpoint。

### 8.2 Claude Code 引擎对齐（`server/`）

| 能力 | CC 产品 | Agent Desktop | 进度 | 缺口 |
|------|---------|---------------|------|------|
| Agent 循环 + 流式 | ✅ | ✅ `AgentLoopService` | 100% | — |
| read / write / grep / glob / list / delete | ✅ | ✅ builtin tools | **95%** | — |
| 终端工具 | ✅ | ✅ `run_terminal_cmd` | **85%** | P5-3 容器 |
| 并行只读工具 | ✅ | ✅ `ToolExecutor` | 100% | — |
| 权限 ask / plan / auto | ✅ | ✅ `PermissionService` | 100% | — |
| `/compact` `/context` `/rewind` | ✅ | ✅ | **100%** | — |
| Checkpoint + fileHistory | ✅ | ✅ JSON 持久化 | **95%** | — |
| JSONL transcript | ✅ | ✅ `transcript/` | 100% | — |
| MCP stdio + inputSchema | ✅ | ✅ `McpToolBridge` | 100% | — |
| 多模态附件 | ✅ | ✅ P4-1 | 100% | — |
| Staged Review | 部分 | ✅ 直接写盘 + Reject 回滚 | 100% | **§7 R11** 已对齐 CC 主路径 |
| WebSearch / WebFetch | ✅ | ✅ | 100% | 更多 Provider |
| Task / 子 Agent | ✅ | ✅ `Task` + depth | 85% | 嵌套 UI 卡片 |
| Hooks 运行时 | ✅ | ✅ 7 事件 command | **40%** | **§7 R8** · 28 事件 |
| 插件 / Skills / 斜杠 | ✅ | ✅ `ExtensionLoader` | 80% | 图形市场 |
| Bash sandbox | ✅ | ✅ strict/warn/off | **85%** | P5-3 容器 |
| MCP HTTP/SSE/OAuth | ✅ | http/sse ✅ | 80% | OAuth · 长连 SSE |
| 配置持久化 | 文件为主 | MySQL+Redis | 85% | 权限 DB 编辑 UI |

### 8.3 Cursor IDE 界面对齐（`desktop/`）

| 能力 | Cursor | Agent Desktop | 进度 | 计划 |
|------|--------|---------------|------|------|
| 三栏 + Activity Bar | ✅ | ✅ | 95% | — |
| Monaco + Tab | ✅ | ✅ | 85% | P4-B 分屏 |
| Diff / Review | ✅ | ✅ | **95%** | — |
| Git 四视图 | ✅ | ✅ | 85% | — |
| 工作区搜索/替换 | ✅ | ✅ ripgrep | 80% | — |
| Agent 面板 + 工具卡片 | ✅ | ✅ | 85% | P4-B Composer |
| @file / @folder / @selection | ✅ | ✅ | 90% | — |
| 命令面板 | ✅ | ✅ | 90% | — |
| 集成终端 | ✅ | ✅ | 85% | — |
| 多窗口 | ✅ | ✅ | 80% | — |
| 主题 / 文件图标 | ✅ | ✅ | 85% | — |
| 查看菜单（面板/终端） | ✅ | ✅ | 85% | P4-B Problems |
| **Inline Edit（Ctrl+K）** | ✅ | ✅ | **100%** | **P4-B-1 ✅** |
| **@codebase** | ✅ | ❌ | 0% | **P4-B-4** |
| Tab 补全 | ✅ | ❌ | 0% | P5 |
| Problems / 诊断 | ✅ | ❌ | 0% | **P4-B-3** |
| 状态栏（分支/Agent） | ✅ | 弱 | 30% | **P4-B-2** |
| 面包屑 / Split | ✅ | 部分 | 40% | P4-B |
| 扩展 / LSP | ✅ | ❌ | 0% | P5 |
| Debug | ✅ | ❌ | 0% | P5 |

---

## 9.0 分阶段路线

### Phase 3 生态 ✅ 100%（2026-06-01）

1. [x] MCP 产品化 UI + REST API  
2. [x] electron-builder + JAR bundling  
3. [x] 命令面板 + 斜杠命令发现  
4. [x] E2E CI（Windows）  
5. [x] safeStorage 密钥链 IPC  

### Phase 4 增强

#### P4-1 聊天附件 ✅（2026-06-01）

- [x] Composer 📎 按钮 · 多选文件对话框 · 附件 chips
- [x] 粘贴图片 · 空文本 + 附件可发送
- [x] `ChatAttachment` `image` kind · base64/mimeType
- [x] OpenAI multimodal `content` 数组（`image_url` data URL）
- [x] 图片 resize（≤2000px）· 5MB 限制

#### P4-A0 / P4-A ✅（详见 §7.4 · 2026-06-08）

> P4-A0 生产逻辑加固 + P4-A 稳定交付已完成。剩余：**A2b** 真实 Java E2E（`E2E_REAL_AGENT=1`）· **A3** JaCoCo 55%→60%。

#### P4-B Cursor 核心 UI `[~]` **~17%**（B1 完成 · 约 4–6 周）

| # | 任务 | Cursor 对标 | 说明 | 状态 |
|---|------|-------------|------|------|
| B1 | **Inline Edit（Ctrl+K）** | ⭐ 最大缺口 | `inline.edit` RPC + InlineEditWidget + 选区流式替换 | ✅ |
| B2 | **状态栏** | 分支 / 编码 / Agent | 接 `gitStore` + 连接状态 | `[ ]` |
| B3 | **Problems 面板** | 诊断列表 | Monaco markers 或 ESLint 输出 | `[ ]` |
| B4 | **@codebase 轻量索引** | @codebase MVP | 符号 + ripgrep 摘要（非向量先行） | `[ ]` |
| B5 | **Composer UX** | 多文件任务 | 会话级变更列表 + 批量 Accept | `[ ]` |
| B6 | 编辑器 chrome | 面包屑 · Split | breadcrumb 完善 · 可选分屏 | `[ ]` |

**P4-B 验收**：Ctrl+K 可改选中代码；@codebase 可引用项目片段；底栏信息完整。

#### P4-C Claude Code 生态 `[~]` **85%**（引擎 + 设置 UI 已落地）

| # | 任务 | CC 对标 | 状态 | 剩余 |
|---|------|---------|------|------|
| C1 | Hooks 最小运行时 | PreToolUse | ✅ | PostToolUse · Stop · SessionStart |
| C2 | Skills / 项目规则 | `.claude/skills` | ✅ | 设置页只读发现 · 无 Skill 编辑器 |
| C3 | 斜杠命令插件化 | `/commit` 等 | ✅ | Composer 动态拉 `/api/extensions` |
| C4 | 子 Agent（Task 工具） | Task | ✅ | 子 Agent 进度 UI · 类型参数 |
| C5 | WebSearch / WebFetch | 可选工具 | ✅ | 更多 Search Provider |
| C6 | MCP SSE/HTTP 传输 | 远程 MCP | ✅ | OAuth · 长连 SSE 重连 |
| C7 | MySQL + Redis 落库 | — | ✅ | 权限规则 DB 可视化编辑 |
| C8 | 系统帮助 / 优缺点文档 | — | ✅ | — |

**P4-C 验收（已通过）**：`skills/` · 斜杠 · Task · Web 工具 · MCP 多传输 · 设置页 · 帮助文档。

**P4-C 收尾（→ P4-D，约 1–2 周）**

| # | 任务 | 说明 |
|---|------|------|
| D1 | Composer 斜杠命令动态列表 | `GET /api/extensions` 驱动 autocomplete |
| D2 | Hooks PostToolUse + Stop | 对齐 CC settings.json hook 事件集 |
| D3 | MCP OAuth 配置字段 | 设置页 + 存 MySQL |
| D4 | 权限规则 REST + 设置页 | `permission_rule` 表可视化 |

### Phase 5 长期增强 `[ ]`（按需）

| # | 任务 | 价值 |
|---|------|------|
| P5-1 | 向量 RAG / 语义搜索 | 大仓库 @codebase 质量 |
| P5-2 | Tab 补全（ghost text） | Copilot++ 级体验 |
| P5-3 | Bash sandbox（容器级） | 安全执行 · **关闭 R2 剩余 gap** |
| P5-4 | 扩展 / LSP 插件体系 | 长期生态 |
| P5-5 | 自动更新通道 | 产品化 |

**暂不实施（P5-）**：完整插件市场、VS Code 扩展兼容、Debug 适配（投入大、偏离 Agent IDE 主线）。

---

## 9.1 推荐执行顺序（2026-06-08 修订）

```
阻塞发布    P4-A0 §7 P0 逻辑加固（R1–R4 + R5）        ← 必须最先
并行        P4-A 测试 + 真实 Java E2E + transcript
已完成      P4-C 扩展引擎 + MySQL 持久化              ← 85%
接下来      P4-B 状态栏 / Problems / @codebase           ← B1 Ctrl+K ✅
短期        P4-D Hook 事件扩展 · 工具 alias             ← R7/R8
中期        P5 Tab · RAG · sandbox-runtime · LSP
```

### 9.1.1 下一步计划（建议 8–10 周 · **A0 前置**）

| 周次 | 主线 | 交付物 | 对齐目标 |
|------|------|--------|----------|
| **W0** | **P4-A0** | R1 read-before-write · R3/R4/R5 · §7.1 门禁 | **生产可发布** |
| **W1–W2** | P4-A | 附件 transcript · Java WS E2E · server >60% | 可发布质量 |
| **W3–W4** | P4-B-1/2 | Ctrl+K · 状态栏 | Cursor **60%→68%** |
| **W5–W6** | P4-B-3/4 | Problems · @codebase MVP | Cursor **68%→75%** |
| **W7** | P4-D | 动态斜杠 · Hook 扩展 | CC **76%→82%** |
| **W8** | P4-B-5 | Composer 变更列表 · 批量 Accept | Cursor Agent UX |
| **W9–W10** | P4-D + 打磨 | MCP OAuth · 权限 DB UI | 双对标提升 |

**里程碑判定**

- **生产发布**：§7.1 **G1–G4 全绿**（P4-A0 完成）→ 允许正式环境
- **Cursor 可用对标**：P4-B 完成 → Cursor **~75%**
- **Claude Code 引擎对标**：P4-A0 后 **~93%**（R1/R5 关闭后）
- **Claude Code 产品对标**：P4-D + P5-3 sandbox-runtime → **~82%+**

---

## 9.2 Cursor 界面验收清单（P4-B）

| Cursor 元素 | 当前 | P4-B 目标 |
|-------------|------|-----------|
| 顶栏菜单（文件/查看/…） | ✅ | 保持；加「问题」入口 |
| Activity Bar | Explorer / Search / Git / Agent | 加 Problems |
| 底部 Panel | 仅终端 | 终端 + Problems Tab |
| 右侧 Agent | ✅ | Composer 变更列表增强 |
| Ctrl+K 内联改码 | ✅ | ✅ |
| Ctrl+L 聚焦聊天 | 部分 | 快捷键 + 聚焦 composer |
| 状态栏 | 弱 | Git 分支 + 行号 + Agent |

---

## 10.0 变更记录

| 日期 | 变更 |
|------|------|
| 2026-06-08 | **P4-B B1 / M5**：Inline Edit（Ctrl+K）— `inline.edit` RPC · NDJSON 流 · InlineEditWidget · 不污染 Chat transcript |
| 2026-06-08 | **P4-A 主体完成**：A1 附件回放 E2E · A3 JaCoCo 55% · A4 chatStore 单测 · A2b realAgentServer 可选 |
| 2026-06-08 | **P4-A0 完成**：R1 read-before-write · R2 Bash 强化 · R3 file_changed 可观测 · R4 rewind/Review 同步 · R5 trackEdit 时机 · §7.1 门禁通过 |
| 2026-06-08 | **进度口径收紧**：引擎 91% · 完整产品 76% · 生产就绪 🔴；B/C/D/H 域进度与 §7 一致 |
| 2026-06-08 | **P4-A**：A2 mock E2E ✅ · A0 门禁 · A2b 真实 Java WS；§9.1 W0=A0 前置 |
| 2026-06-01 | **P4-C ~85%**：扩展引擎 · Task/Web/MCP http · MySQL/Redis · 设置页 · systemHelp · WBS 严格对齐表 |
| 2026-06-01 | **路线图对齐**：§0 分域进度 · §9 下一步 8–10 周计划 · P4-D 收尾项 |
| 2026-06-01 | **P4-A 产品打磨**：多窗口 · 每窗口终端 · 查看/文件菜单 · 定位 Explorer · 终端主题 |
| 2026-06-01 | **P4-1**：聊天附件 📎 · 图片 multimodal · paste · IPC `openChatFiles` |
| 2026-06-01 | **P3 100%**：MCP UI/API · 命令面板 · 斜杠发现 · electron-builder · E2E CI · safeStorage |
| 2026-06-07 | **P2 100%**：`@selection` · dirty buffer 合并 UI |
| 2026-06-01 | **P2 ~92%**：Checkpoint · 权限 UI · E2E · 主题加固 |
| 2026-06-06 | P2 ~75%：Accept/Reject · Permission · @file |
| 2026-06-01 | 目录扁平化 · Vue 3 · Spring Boot 定稿 |

---

> **说明**：P0–P2 详细条目仍有效；完整 P0/P1 清单见 Git 历史或 `docs/architecture-backend.md`。  
> **生产发布**：必须先通过 **§7.1 发布门禁**（P0 逻辑风险清零）。  
> PR 涉及功能变更时请同步更新本文件（见 `CONTRIBUTING.md`）。
