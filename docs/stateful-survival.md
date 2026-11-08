# 有状态组件生存指南

> 背景：Review 接受 Agent 修改 → 写入 `desktop/src` → Vite HMR → 组件 remount → 终端 PTY 被 kill、界面假死。  
> 本文档归纳 **同类问题的排查清单** 与 **项目内已落地的防护**。

---

## 问题模式（Anti-patterns）

| 模式 | 典型症状 | 示例 |
|------|----------|------|
| **有状态组件放在 `router-view` 子树** | 路由切换 / HMR 时 PTY、Monaco 被销毁 | 终端原先在 `WorkspaceContent` |
| **`v-if` 控制长生命周期资源** | 隐藏/切页 = kill 进程 | 终端 `v-if="terminalOpen"`、设置页 `v-if="!onSettings"` |
| **`onUnmounted` 立即 kill IPC/进程** | HMR remount 误杀 | `terminalApi.kill()` |
| **`ResizeObserver` + `fit()` 无节流** | 拖拽/布局变化时 UI 卡死 | xterm fit + PTY resize 循环 |
| **循环内多次 `writeFile` + 自举工作区** | 连续 HMR 风暴 | Review accept 多个 `desktop/src` 文件 |
| **`watch` 拖拽时每像素 persist** | 磁盘/IPC 风暴 | 面板宽度 watch 无 debounce |

---

## 项目内防护（已实现）

### 1. 布局层托管长生命周期 UI

- **终端**、**Chat** 在 `WorkspaceLayout`，不在 `router-view` 内。
- **编辑器** 路由组件 `WorkspaceContent` 使用 `KeepAlive`，切设置/帮助再回来不丢 Monaco 实例。

### 2. 用 `v-show` 代替 `v-if`（隐藏 ≠ 销毁）

- 终端折叠：`v-show="terminalOpen"`
- 终端在设置页：`v-show="!onSettings"`（不再 `v-if` 整块卸载）
- 侧栏 explorer / search / git：`v-show` 切换，保留滚动与焦点状态

### 3. 终端专用

- `useSurvivingUnmount()`：HMR remount 后 300ms 内不 kill PTY
- 主进程 `intentionalKill`：主动 replace 时不发 `terminal:exit`
- `ResizeObserver` → `requestAnimationFrame` + cols/rows 去重

### 4. 批量写盘（减少 HMR 次数）

- IPC：`workspace:writeFilesBatch` / `workspace:deleteFilesBatch`
- Review：`acceptAll` / `rejectAll` 批量写盘回滚（CC 主路径：Agent 已直接写磁盘）
- Vite：`server.watch.awaitWriteFinish`（300ms 合并连续写入）

### 5. 自举工作区（dogfooding）检测

- 主进程 `isSelfWorkspace()`：工作区与 `app.getAppPath()` 重叠
- 打开工作区时 dev 模式 Toast 提示
- IPC：`app:getDevContext`

### 6. 其它

- 面板布局 persist：`useResizablePanels` 400ms debounce + 拖拽结束 `flushPanelLayout`
- `App.vue`：`beforeunload` / unmount 调用 `chatStore.cleanup()`

---

## 新功能开发 Checklist

在添加 **终端 / WebSocket / 原生 IPC / Monaco / 定时器** 前，逐项确认：

1. **组件挂载位置**：是否应放在 `WorkspaceLayout` 而非 `router-view`？
2. **显示/隐藏**：是否用 `v-show`？若必须用 `v-if`，是否在 unmount 做延迟 teardown？
3. **ResizeObserver**：是否 rAF 节流 + 数值去重？
4. **写盘路径**：是否会写 `desktop/src`？能否 batch？是否在自举工作区需提示？
5. **RPC/IPC 循环**：多文件操作能否一次 batch（参考 `sessionChangesStore.acceptAll`）？
6. **watch persist**：拖拽类 UI 是否 debounce 或仅在 `resizeEnd` 持久化？
7. **cleanup**：是否在 `beforeunload` 释放 listener / WebSocket？

可复用 composable：

- `useSurvivingUnmount()` — 延迟 destructive cleanup
- 批量写盘 — `window.workspaceApi.writeFilesBatch`

---

## 仍须人工注意的场景

| 场景 | 说明 |
|------|------|
| 在集成终端里跑 `pnpm dev` 再 Review 改源码 | HMR 仍会发生，但终端/编辑器已防护；极端情况可重启 dev |
| 自举工作区保存 `Ctrl+S` | 单文件写盘，风险低于 Review 批量 accept |
| 工作区全局替换 | `searchStore` → `replaceInFiles`，大量写盘时同样触 HMR |
| Monaco 多标签 + 快速拖面板 | `automaticLayout: true` 可能多实例同时 layout，后续可集中 debounce |

---

## 相关文件

| 文件 | 职责 |
|------|------|
| `WorkspaceLayout.vue` | 终端/Chat/KeepAlive 布局 |
| `TerminalPanel.vue` | xterm + PTY + surviving unmount |
| `terminalService.ts` | intentionalKill |
| `sessionChangesStore.ts` | Review batch apply/reject |
| `useResizablePanels.ts` | debounced panel persist |
| `selfWorkspace.ts` | 自举工作区检测 |
| `electron.vite.config.ts` | `awaitWriteFinish` |
