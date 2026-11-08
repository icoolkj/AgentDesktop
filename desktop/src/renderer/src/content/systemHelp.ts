import {
  PRODUCT_AUTHOR,
  PRODUCT_EMAIL_PRIMARY,
  PRODUCT_EMAIL_SECONDARY,
  PRODUCT_WEBSITE,
} from '@renderer/constants/productInfo'

export interface HelpSection {
  id: string
  title: string
  paragraphs?: string[]
  items?: string[]
  subsections?: { title: string; body: string[] }[]
}

export const systemHelpSections: HelpSection[] = [
  {
    id: 'overview',
    title: '概述',
    paragraphs: [
      'Agent Desktop 是带 Agent 助手的桌面 IDE。左侧为资源管理器与 Git，中间为编辑器，右侧为 Agent 对话。',
      '菜单「帮助 → 系统帮助文档」可随时打开本页；左侧目录可跳转到 Git、窗口、扩展引擎、优缺点等章节。',
      '若同时使用 IDEA 等外部工具提交 Git，Agent Desktop 会检测 .git/index.lock，避免与外部提交争抢锁导致界面卡住；终端在 dev 热更新时会尽量保留 PTY 会话。',
    ],
  },
  {
    id: 'positioning',
    title: '产品定位',
    paragraphs: [
      'Agent Desktop 的目标是：用 Cursor 风格的 IDE 界面，承载对齐 Claude Code 的 Agent 引擎能力。',
      '适合：需要在本地 IDE 里打开项目、改代码、看 Git、并让 Agent 读写工作区的开发者。',
      '不适合替代：纯终端 Claude Code TUI、云端-only 的 Cursor Cloud Agent、或需要完整 VS Code 插件生态的场景。',
    ],
    subsections: [
      {
        title: '与 Claude Code 的关系',
        body: [
          '引擎侧：Agent 循环、工具并行、权限 ask/plan/auto、JSONL 会话、checkpoint、/compact / /context 等与 Claude Code 思路一致。',
          '扩展侧：斜杠命令、Skills、Hooks、Task 子 Agent、Web 工具、MCP 多传输等可通过配置与工作区文件扩展，路径兼容 .agent-desktop 与 .claude。',
          '差异：本产品是 Electron IDE，不是 Node CLI；插件图形市场、Computer Use、容器级 Bash 隔离仍在路线图。',
        ],
      },
      {
        title: '与 Cursor IDE 的关系',
        body: [
          '界面侧：三栏布局、Activity Bar、命令面板、Git 四视图、终端、Agent 面板等向 Cursor 靠拢。',
          '差异：暂无 Tab 补全、语义 @codebase 索引；Agent 后端为自研 Java 服务 + MySQL/Redis，而非 Cursor 云 Agent。',
          '已实现 Cursor 式 Inline Edit（Ctrl+K）：选中代码或当前行 → 输入指令 → 流式预览 → Accept 替换选区，不写入主 Chat transcript。',
          '优势：会话 JSONL 本地可控、/rewind checkpoint、MCP 与扩展引擎可在设置页配置并落库。',
        ],
      },
    ],
  },
  {
    id: 'strengths',
    title: '项目优势',
    paragraphs: [
      '以下为当前版本相对同类产品的长处，便于判断是否适合你的工作流。',
    ],
    items: [
      '完整桌面 IDE 壳：Monaco 编辑、多 Tab、Diff/Review、工作区搜索、多窗口与最近项目。',
      'Git 四视图：变更 / 提交 / 日志 / 同步，与 VS Code 习惯接近。',
      'Agent 引擎成熟度高：读写文件、grep/glob、终端、并行只读工具、权限三模式、流式对话。',
      'Claude Code 式会话：JSONL 本地 transcript、checkpoint 回滚、/compact / /context。',
      '扩展引擎可配置：Task 子 Agent、WebSearch、WebFetch、MCP（stdio/http/sse），设置页 + MySQL 落库。',
      '工作区插件化：commands/*.md 斜杠、skills/**/SKILL.md、hooks.json，兼容 .claude 目录。',
      'MCP 可视化管理：设置里添加/测试服务器，Agent 自动注册 mcp__服务器__工具。',
      '多窗口隔离：每窗独立工作区、终端 PTY、Agent 会话引导，避免「新窗仍是旧项目」。',
      '主题与文件图标：颜色主题、文件图标主题独立切换，全局 --ide-* token 一致。',
    ],
  },
  {
    id: 'limitations',
    title: '局限与待完善',
    paragraphs: [
      '以下为已知短板或路线图项，避免误以为是 Bug；详细进度见仓库 docs/WBS.md。',
    ],
    items: [
      '无 Tab 补全 / Ghost Text（Copilot 式行内补全尚未实现）。',
      '已实现 Inline Edit（Ctrl+K）：编辑器内选中或当前行改写，独立于 Agent 对话 transcript。',
      '无向量语义 @codebase：@file / @folder 可用，大仓库「全项目语义检索」质量有限。',
      'Hooks 运行时：Pre/PostToolUse、UserPromptSubmit、Stop、SessionStart/End、SubagentStop 已接入；复杂 hook 类型（prompt 型）仍少于 CC 全量。',
      '插件：支持 plugins/*/plugin.json manifest 与工作区发现 API；尚无图形化插件市场/一键安装。',
      'Bash 沙箱：strict/warn/off（设置 → 扩展引擎）；拦截 sudo、powershell、curl|sh 等；含 workspace 路径守卫。非容器级隔离，企业环境请叠加 OS 策略（P5-3 容器方案在路线图）。',
      '写盘安全：write_file / search_replace / delete_file 须先 read_file；外部改盘或 IDE 未保存编辑会被拒绝（超越 CC 的 IDE buffer 校验）。',
      '/rewind 回滚后 Review 列表与编辑器自动同步（rewind_restored 事件）。',
      '写文件主路径：Agent 工具直接写磁盘，Review 通过 Reject 回滚；不再使用服务端 staging。',
      'WebSearch 默认 DuckDuckGo 即时答案，复杂检索不如专用 Search API。',
      'MCP OAuth / 远程 SSE 复杂场景仍在演进，简单 http URL 已支持。',
      '依赖本地 MySQL + Redis：后端配置落库需要数据库可用；不可用时部分历史/设置会降级。',
      'LLM 需正确网关：推荐 CLIProxyAPI-Java 以获取原生 tool_calls，否则 Agent 无法稳定调用工具。',
    ],
  },
  {
    id: 'extension-engine',
    title: '扩展引擎',
    paragraphs: [
      '扩展引擎 = Task / Web / Bash 等可配置工具。Skills 独立入口：设置 → Agent → Skills。',
      '全局开关（Task / WebSearch / WebFetch）保存在 MySQL agent_config 表，读取经 Redis 缓存；修改后建议重启 Electron 使主进程加载最新配置。',
      '工作区扩展（斜杠命令、Skills、项目 Hooks）仍放在项目目录，便于 Git 跟踪；全局 Hooks 可迁移到数据库。',
    ],
    subsections: [
      {
        title: '设置页可配置项',
        body: [
          'Task 子 Agent — 是否启用、最大嵌套深度、子 Agent 最大轮次；Task 工具支持 subagent_type（generalPurpose / explore / shell）。',
          'Bash 沙箱 — strict / warn / off，作用于 run_terminal_cmd。',
          'WebSearch — 是否启用；默认 DuckDuckGo，无需 API Key。',
          'WebFetch — 是否启用；HTTP GET 抓取，可设最大字节数。',
          '页面底部「工作区扩展发现」：斜杠命令、插件 manifest、Skills/Hooks 数量（需已打开文件夹）。',
        ],
      },
      {
        title: '工作区文件（与 Claude Code 兼容）',
        body: [
          '斜杠命令：.agent-desktop/commands/xxx.md 或 .claude/commands/xxx.md（YAML frontmatter + 正文模板）。',
          'Skills：设置 → Skills 页安装/卸载；目录 ~/.agent-desktop/skills-agent-desktop/ 等；Agent 通过 Skill 工具或 /name 加载正文。',
          'Hooks：.agent-desktop/hooks.json 或 .claude/settings.json 内 hooks 段；支持 PreToolUse/PostToolUse/UserPromptSubmit/Stop/Session*/SubagentStop。',
          '插件：~/.agent-desktop/plugins/插件名/plugin.json（或 .claude-plugin/plugin.json），可声明 commands/skills/hooks 路径。',
          '权限规则：.agent-desktop/permissions.json；全局规则可存 MySQL permission_rule 表。',
        ],
      },
      {
        title: 'MCP 服务器',
        body: [
          '设置 → MCP 服务器：支持 stdio（本地子进程）与 http/sse（远程 URL）。',
          '配置保存在 MySQL mcp_server_config；旧版 ~/.agent-desktop/mcp.json 首次启动会自动迁移。',
          'Agent 中工具名格式：mcp__服务器名__工具名；可用「测试连接」验证后再在对话中让 Agent 调用。',
        ],
      },
      {
        title: '内置斜杠命令',
        body: [
          '/compact — 手动压缩上下文（与 Claude Code 类似）。',
          '/context — 查看当前上下文占用 breakdown。',
          '自定义命令 — 如 /review，由 commands/*.md 定义，发送前展开为完整 prompt。',
        ],
      },
    ],
  },
  {
    id: 'extension-test',
    title: '如何测试扩展引擎',
    paragraphs: [
      '测试分三层：配置是否落库 → API/设置页是否一致 → Agent 对话是否真正调用工具（Chat 里会出现工具卡片）。',
      '前提：已打开工作区文件夹；LLM 已配置且支持 tool_calls；扩展引擎里对应开关已打开并「保存到数据库」。',
    ],
    subsections: [
      {
        title: '1. 配置层',
        body: [
          '设置 → 扩展引擎：修改一项 → 保存 → 刷新页面，值应保持。',
          '设置 → MCP 服务器：添加服务器 → 测试连接 → 应显示工具数量。',
          '可选：浏览器或 curl 访问 GET /api/settings/features、GET /api/extensions?workspaceRoot=你的项目路径。',
        ],
      },
      {
        title: '2. Agent 对话层（看工具名）',
        body: [
          'WebSearch：「请使用 WebSearch 搜索 xxx，用中文总结。」→ 应出现 WebSearch 工具卡片。',
          'WebFetch：「请用 WebFetch 抓取 https://example.com 并摘要。」→ 应出现 WebFetch 工具卡片。',
          'Task：「请用 Task（subagent_type=explore）探索 server/agent/extension 目录结构。」→ Task 卡片 + 时间线「子 Agent」步骤。',
          'MCP：「调用可用的 MCP 工具各演示一次。」→ 应出现 mcp__… 工具名。',
        ],
      },
      {
        title: '3. 斜杠 / Skills / Hooks',
        body: [
          '斜杠：在项目中添加 commands/review.md 后，Chat 输入 /review 测试点 → 应按模板展开任务。',
          'Skills：设置 → Skills → 内置商店一键安装或从本地目录安装 → Agent 调用 Skill 或 /name。',
          'Hooks：在 hooks.json 中对 write_file 配置 block → 让 Agent 写文件 → 应被 hook 拒绝。',
        ],
      },
      {
        title: '常见问题',
        body: [
          'Chat 里只有文字、没有工具卡片：检查 LLM 是否走 CPA 等支持 tool_calls 的网关；是否已打开工作区。',
          '工具显示 disabled：扩展引擎里对应开关是否关闭；Task 是否超过 maxSubagentDepth。',
          '设置保存无效：MySQL/Redis 是否运行；是否执行过 schema-agent-config.sql 迁移。',
        ],
      },
    ],
  },
  {
    id: 'settings-persistence',
    title: '设置与持久化',
    paragraphs: [
      'Agent Desktop 后端（Spring Boot）使用 MySQL 为主存储、Redis 为热缓存；Electron 侧会话 transcript 等为本地 JSONL。',
      '设置 → Agent → 后端连接：可查看/修改 Java 服务 HTTP 地址，保存前可用「测试连接」访问 /health。',
    ],
    subsections: [
      {
        title: '存 MySQL + Redis 的配置',
        body: [
          'LLM 配置 — llm_settings 表（设置 → LLM 配置）。',
          '扩展功能开关 — agent_config.features（设置 → 扩展引擎）。',
          'MCP 服务器 — mcp_server_config（设置 → MCP 服务器）。',
          '全局 Hooks — agent_config.hooks_global（可由 hooks.json 首次迁移）。',
          '全局权限规则 — permission_rule 表 scope=global（工作区规则仍以文件为主）。',
          '聊天会话/消息 — chat_session / chat_message（Legacy REST；主会话以客户端 JSONL 为准）。',
        ],
      },
      {
        title: '仍用本地文件的配置',
        body: [
          '工作区斜杠命令、Skills、项目 permissions — 项目内 .agent-desktop 或 .claude 目录，适合提交到 Git。',
          'Agent 会话 transcript — 用户目录下 JSONL，与 Claude Code 类似，便于备份与审计。',
          '编辑器主题、窗口布局等 — Electron renderer 本地存储。',
        ],
      },
      {
        title: '设置入口一览',
        body: [
          '后端连接 — Java 服务 URL 与健康检查。',
          'Agent 行为 — Ask / Auto / Plan 权限模式。',
          '权限规则 — 项目 deny/ask/allow 说明（加载 .agent-desktop/permissions.json）。',
          '扩展引擎 — Task / Web / 工作区扩展发现。',
          'MCP 服务器 — stdio 与 http/sse 远程 MCP。',
          'Models — 从 CPA 拉取模型列表并设默认。',
          'LLM 配置 — API Key、Base URL、流式策略等。',
        ],
      },
    ],
  },
  {
    id: 'git',
    title: 'Git 源代码管理',
    paragraphs: [
      '侧栏 Git 面板分为四个视图：变更、提交、日志、同步。典型流程：在「变更」暂存并查看对比 → 在「提交」写说明并提交 → 在「同步」推送到远程；「日志」可浏览提交历史。',
    ],
    subsections: [
      {
        title: '变更',
        body: [
          '已暂存：下次提交将包含的文件。',
          '更改：尚未暂存的本地修改。',
          '点击文件名：打开左右并排对比（左侧 HEAD/索引，右侧工作区）。',
          '悬停行尾操作：+ 暂存　− 取消暂存　↶ 还原',
          '已暂存区的 ↶：放弃全部更改（含暂存），恢复为上次提交版本。',
          '更改区的 ↶：仅丢弃工作区未暂存修改，不影响已暂存内容。',
        ],
      },
      {
        title: '提交',
        body: [
          '填写提交说明后点击「提交」，或使用 Ctrl+Enter。',
          '只有「已暂存」的文件会进入本次提交；提交成功后自动跳到「同步」页。',
          '若列表为空，可点「去变更页暂存」或「全部暂存」；已暂存文件可点「全部取消」。',
        ],
      },
      {
        title: '日志',
        body: [
          '按时间倒序显示提交历史：短 hash、说明、作者、相对时间。',
          '若有分支/标签引用会显示在条目下方；支持「加载更多」分页浏览。',
        ],
      },
      {
        title: 'Fetch / Pull / Push 的区别',
        body: [
          'Fetch（获取）：从远程下载最新提交到本地，不修改当前工作区，也不自动合并。适合先查看远程是否有更新。',
          'Pull（拉取）：下载远程更新并合并到当前分支，会改变本地代码。推送前通常先 Pull，减少冲突。',
          'Push（推送）：把本地已 commit 的提交上传到远程仓库。',
          '简记：Fetch = 只下载；Pull = 下载 + 合并；Push = 上传。',
        ],
      },
      {
        title: '状态字母',
        body: [
          'M 已修改　U 未跟踪　A 新增　D 删除　R 重命名',
        ],
      },
    ],
  },
  {
    id: 'workspace',
    title: '窗口与项目',
    paragraphs: [
      '通过菜单「文件」管理窗口与工作区，行为与常见 IDE 一致。',
    ],
    items: [
      '新建窗口 — 打开空白窗口，不自动加载项目。',
      '打开文件夹… — 在当前窗口切换项目；若该项目已在其他窗口打开，则聚焦该窗口。',
      '在新窗口打开文件夹… — 始终在新窗口中打开所选文件夹。',
      '最近打开 — 单击切换到该项目；若已在其他窗口打开则聚焦该窗口。',
      '最近打开 + Shift 单击 — 在新窗口中打开该项目。',
    ],
  },
  {
    id: 'about',
    title: '关于',
    paragraphs: [
      `Agent Desktop 是带 Agent 助手的桌面 IDE，官网：${PRODUCT_WEBSITE}`,
      `作者：${PRODUCT_AUTHOR} · ${PRODUCT_EMAIL_PRIMARY} · ${PRODUCT_EMAIL_SECONDARY}`,
    ],
  },
  {
    id: 'view',
    title: '查看菜单',
    paragraphs: [
      '「查看」菜单用于切换侧栏面板、终端与 Agent 布局，行为与 VS Code / Cursor 类似。',
    ],
    subsections: [
      {
        title: '侧栏面板',
        body: [
          '资源管理器 — 浏览项目文件树。',
          '搜索 — 在工作区或当前文件中查找（Ctrl+Shift+F）。',
          '源代码管理 — Git 变更、提交、日志与同步（Ctrl+Shift+G）。',
        ],
      },
      {
        title: '布局',
        body: [
          '显示/隐藏终端 — 切换底部终端面板（Ctrl+`）。',
          '显示/隐藏 Agent 面板 — 切换右侧对话区。',
          '显示/隐藏侧栏 — 折叠或展开整个左侧区域。',
        ],
      },
      {
        title: '在资源管理器中定位',
        body: [
          '将当前编辑器打开的文件在左侧资源管理器中高亮并展开路径（不会打开系统文件管理器）。',
          '若当前在搜索或 Git 面板，会自动切回资源管理器并滚动到对应文件。',
          '需先打开一个文件；无打开文件时该菜单项不可用。',
        ],
      },
    ],
  },
  {
    id: 'shortcuts',
    title: '常用快捷键',
    items: [
      'Ctrl+K — Inline Edit（选中或当前行改写）',
      'Ctrl+S — 保存当前文件',
      'Ctrl+Shift+P — 命令面板',
      'Ctrl+Shift+E — 资源管理器',
      'Ctrl+Shift+F — 全局搜索',
      'Ctrl+Shift+G — 源代码管理',
      'Ctrl+` — 显示/隐藏终端',
      'Git 提交页 Ctrl+Enter — 提交',
    ],
  },
  {
    id: 'appearance',
    title: '外观与主题',
    paragraphs: [
      '菜单栏提供两个独立入口：「颜色主题」与「文件图标」。',
      '设置页左侧分为「外观」（颜色主题、文件图标）与「Agent」（后端、行为、权限、扩展引擎、MCP、Models、LLM）；选择后立即生效或保存到后端数据库。',
      '所有界面配色通过统一 design token（--ide-*）驱动，切换主题时侧栏、编辑区、Agent 面板、终端 ANSI 色同步协调。',
    ],
  },
]
