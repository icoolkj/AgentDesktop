# Windows 终端（node-pty / PTY）

集成终端使用 **node-pty**（ConPTY），支持 `vim`、彩色 TUI、窗口 resize。

## 正常安装（无需编译）

`node-pty@1.2.0-beta.13` 已自带 **预编译包**：

```text
node_modules/node-pty/prebuilds/win32-x64/conpty.node
```

项目配置：

- `pnpm.neverBuiltDependencies: [node-pty]` — 跳过 install 时的 node-gyp
- `.npmrc` 中 `build-from-source=false` — 不删除 prebuilds
- `postinstall` 使用 `electron-rebuild -o better-sqlite3 -f`（**`-o` 只编译 sqlite3**，`-w` 会误编译 node-pty）

```powershell
cd D:\Idea2024\Workspace2025\agent-desktop
pnpm install
pnpm dev
```

验证预编译包：

```powershell
dir node_modules\.pnpm\node-pty@1.2.0-beta.13\node_modules\node-pty\prebuilds\win32-x64
```

底部终端运行 `vim` 测试。

## 常见错误：MSB8040 / Spectre 库

若看到 `electron-rebuild` 正在编译 `node-pty` 并报 **MSB8040**，说明**不该编译**：

| 错误操作 | 正确做法 |
|---------|---------|
| 运行 `electron-rebuild -f -w node-pty` | **不要运行** — 直接用 prebuilds |
| 全局 `npm_config_build_from_source=true` | 项目 `.npmrc` 已设 `build-from-source=false` |
| 失败后残留 `node-pty/build/` | `postinstall` 会自动删除，或手动删该目录 |

**不要**再执行任何 `rebuild:pty` 类命令；该脚本已从项目中移除。

## 仅当必须从源码编译时（可选）

1. Visual Studio Installer → Build Tools 2022 → Individual components
2. 安装 **MSVC v143 - VS 2022 C++ x64/x86 Spectre-mitigated libs (Latest)**
3. 手动执行（一般不需要）：

```powershell
cd desktop
npx electron-rebuild -f -w node-pty
```

## 降级模式

若 PTY 仍无法加载，终端会显示 `[pipe mode]` 并降级为管道模式（vim/TUI 受限）。见 `terminalService.ts` 中的 fallback。
