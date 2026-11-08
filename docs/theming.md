# 主题与 Design Tokens

Agent Desktop 使用 **单一颜色主题系统**（`modules/appearance/`）。所有 UI 组件必须跟随主题，禁止写死 VS Code 深色 hex。

---

## 架构

```
colorThemes.ts          定义各主题 tokens
themeHarmonize.ts       从主色推导侧栏/终端/按钮等次级 token
applyColorTheme()       写入 document.documentElement CSS 变量
appearanceStore         setColorTheme → notifyColorTheme
  ├─ Monaco             monaco.editor.setTheme
  └─ 订阅者              onColorThemeChange（终端 xterm 等）
```

**切换主题路径**：设置 → 颜色主题 → `useAppearanceStore().setColorTheme(id)`

---

## 开发规则

### ✅ 必须

1. **样式** 使用 `var(--ide-*)`，见 `modules/appearance/types.ts` 中 `ColorThemeTokens`
2. **Canvas/第三方控件**（xterm、未来图表等）通过 `onColorThemeChange` 订阅，用 `buildXtermTheme(tokens)` 等映射函数读 tokens
3. **语义色** 用 token：`--ide-error`、`--ide-success`、`--ide-accent`、`--ide-git-*`
4. **半透明背景** 用 `color-mix(in srgb, var(--ide-error) 15%, transparent)`，不要 `rgba(244, 71, 71, …)`

### ❌ 禁止

- 在 Vue/CSS 中写 `#1e1e1e`、`#f48771`、`#9cdcfe` 等固定色（除非在 `colorThemes.ts` 主题定义内）
- 终端/编辑器单独一套“永远深色”主题
- 新面板背景用 `background: #252526`

---

## 终端专用 Token

| Token | 用途 |
|-------|------|
| `--ide-terminal-bg` | 终端背景（随 light/dark 变化） |
| `--ide-terminal-fg` | 默认前景/光标 |
| `--ide-terminal-cursor` | 光标色 |
| `--ide-terminal-selection` | 选区背景 |

xterm ANSI 16 色由 `utils/xtermTheme.ts` 从 `--ide-error/success/warning/accent` 推导，无需逐个 CSS 变量。

---

## 新组件 Checklist

- [ ] 所有颜色来自 `var(--ide-*)`
- [ ] 若使用 Canvas/WebGL/原生控件，是否订阅 `onColorThemeChange`？
- [ ] 是否在 light 主题下目视检查（如 Solarized Light）？
- [ ] 是否避免 duplicate 默认 hex fallback（`var(--ide-editor-bg, #1e1e1e)` 仅作 bootstrap fallback）

---

## Agent 面板专用

| 区域 | 主题机制 |
|------|----------|
| **Markdown 正文** | `styles/chat-markdown.css` → `var(--ide-code-bg)` / `--ide-fg` |
| **Mermaid 图表** | `mermaidTheme.ts` 映射 IDE token；`onColorThemeChange` 重绘；`ChatDiagramLightbox` 放大 |
| **工具输出 / Diff 预览** | `ChatToolPanel` / `ChatChangeTreeNode` → `--ide-code-bg` |
| **Monaco inline diff** | `--ide-success` / `--ide-git-modified` / `--ide-error` + 切主题重绘 |
| **权限 / 回退弹窗** | `--ide-surface-bg` + `color-mix` 遮罩（禁止 `#1e1e1e` fallback） |

第三方 Canvas 控件（Mermaid、xterm）必须通过 `onColorThemeChange` 订阅，禁止写死 `theme: 'dark'`。

---

## 相关文件

| 文件 | 说明 |
|------|------|
| `modules/appearance/types.ts` | Token 契约 |
| `modules/appearance/colorThemes.ts` | 主题表 + `applyColorTheme` |
| `modules/appearance/store.ts` | `onColorThemeChange` |
| `utils/xtermTheme.ts` | 终端 palette 映射 |
| `utils/mermaidTheme.ts` | Mermaid base 主题变量 |
| `styles/chat-markdown.css` | Agent 消息 Markdown |
| `styles/chat-diagram.css` | Mermaid 工具栏 |
| `components/terminal/TerminalPanel.vue` | 主题订阅示例 |

---

## 已知待清理（非阻塞）

以下文件仍有少量历史硬编码色，后续 PR 可逐步改为 token：

- `GitLogView.vue` / `GitSyncView.vue` 部分链接色
- `SearchSidebar.vue` 部分高亮
- `FileTreeIcon.vue` 图标 tint（SVG 品牌色可保留）

新增代码 **不要** 再扩大此列表。
