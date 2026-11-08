import type { Router } from 'vue-router'
import { PRODUCT_WEBSITE } from '@renderer/constants/productInfo'
import { useEditorStore } from '@renderer/stores/editorStore'

export interface CommandItem {
  id: string
  label: string
  detail?: string
  keywords?: string[]
  shortcut?: string
  when?: () => boolean
  run: () => void | Promise<void>
}

const commands: CommandItem[] = []

export function registerCommand(command: CommandItem): void {
  const index = commands.findIndex(item => item.id === command.id)
  if (index >= 0) {
    commands[index] = command
  } else {
    commands.push(command)
  }
}

export function getCommands(): CommandItem[] {
  return commands.filter(cmd => (cmd.when ? cmd.when() : true))
}

export function searchCommands(query: string): CommandItem[] {
  const q = query.trim().toLowerCase()
  const available = getCommands()
  if (!q) return available.slice(0, 20)

  return available
    .map(cmd => {
      const haystack = [cmd.label, cmd.detail ?? '', ...(cmd.keywords ?? [])].join(' ').toLowerCase()
      const score = haystack.includes(q) ? (cmd.label.toLowerCase().startsWith(q) ? 0 : 1) : 99
      return { cmd, score }
    })
    .filter(entry => entry.score < 99)
    .sort((a, b) => a.score - b.score || a.cmd.label.localeCompare(b.cmd.label))
    .slice(0, 20)
    .map(entry => entry.cmd)
}

export function registerWorkspaceCommands(deps: {
  router: Router
  toggleSidebar: () => void
  toggleChat: () => void
  toggleTerminal: () => void
  showProblems: () => void
  scanProblems: () => void | Promise<void>
  openSearch: () => void
  openExplorer: () => void
  openGit: () => void
  revealInExplorer: () => void
  focusChat: () => void
  openInlineEdit: () => void
}): void {
  registerCommand({
    id: 'workspace.explorer',
    label: '打开资源管理器',
    keywords: ['explorer', 'files', '资源管理器'],
    shortcut: 'Ctrl+Shift+E',
    run: deps.openExplorer,
  })

  registerCommand({
    id: 'workspace.search',
    label: '工作区搜索',
    detail: '在文件中搜索',
    keywords: ['search', 'find', '搜索'],
    shortcut: 'Ctrl+Shift+F',
    run: deps.openSearch,
  })

  registerCommand({
    id: 'workspace.git',
    label: '打开源代码管理',
    keywords: ['git', 'source control', 'scm'],
    shortcut: 'Ctrl+Shift+G',
    run: deps.openGit,
  })

  registerCommand({
    id: 'workspace.revealInExplorer',
    label: '在资源管理器中定位',
    keywords: ['reveal', 'locate', '定位'],
    when: () => Boolean(useEditorStore().activePath),
    run: deps.revealInExplorer,
  })

  registerCommand({
    id: 'workspace.toggleSidebar',
    label: '切换侧边栏',
    keywords: ['sidebar', 'explorer'],
    run: deps.toggleSidebar,
  })

  registerCommand({
    id: 'workspace.toggleChat',
    label: '切换 Agent 面板',
    keywords: ['chat', 'agent'],
    run: deps.toggleChat,
  })

  registerCommand({
    id: 'workspace.toggleTerminal',
    label: '切换终端',
    keywords: ['terminal', 'pty'],
    shortcut: 'Ctrl+`',
    run: deps.toggleTerminal,
  })

  registerCommand({
    id: 'workspace.showProblems',
    label: '显示问题面板',
    detail: '代码错误与警告',
    keywords: ['problems', 'diagnostics', 'errors', '问题', '诊断'],
    shortcut: 'Ctrl+Shift+M',
    run: deps.showProblems,
  })

  registerCommand({
    id: 'workspace.scanProblems',
    label: '扫描工作区问题',
    detail: '运行 TypeScript / ESLint 全项目检查',
    keywords: ['scan', 'lint', 'tsc', '扫描'],
    run: () => {
      deps.showProblems()
      void deps.scanProblems()
    },
  })

  registerCommand({
    id: 'workspace.focusChat',
    label: '聚焦 Agent 输入框',
    keywords: ['chat', 'composer'],
    run: deps.focusChat,
  })

  registerCommand({
    id: 'editor.inlineEdit',
    label: 'Inline Edit（选中改写）',
    detail: 'Cursor Ctrl+K',
    keywords: ['inline', 'edit', 'ctrl+k', '改写'],
    shortcut: 'Ctrl+K',
    when: () => Boolean(useEditorStore().activePath),
    run: deps.openInlineEdit,
  })

  registerCommand({
    id: 'settings.open',
    label: '打开设置',
    keywords: ['settings', 'preferences'],
    run: () => {
      void deps.router.push({ name: 'settings-color-theme' })
    },
  })

  registerCommand({
    id: 'settings.llm',
    label: '设置：LLM 配置',
    keywords: ['llm', 'model', 'api'],
    run: () => {
      void deps.router.push({ name: 'settings-llm' })
    },
  })

  registerCommand({
    id: 'settings.mcp',
    label: '设置：MCP 服务器',
    keywords: ['mcp', 'tools'],
    run: () => {
      void deps.router.push({ name: 'settings-mcp' })
    },
  })

  registerCommand({
    id: 'settings.permissions',
    label: '设置：权限规则',
    keywords: ['permission', 'allow', 'deny'],
    run: () => {
      void deps.router.push({ name: 'settings-permissions' })
    },
  })

  registerCommand({
    id: 'help.website',
    label: '访问官网',
    detail: PRODUCT_WEBSITE,
    keywords: ['website', 'home', '官网'],
    run: () => {
      void window.desktopApi.openExternal(PRODUCT_WEBSITE)
    },
  })

  registerCommand({
    id: 'help.open',
    label: '打开帮助',
    keywords: ['help', 'docs'],
    run: () => {
      void deps.router.push({ name: 'help' })
    },
  })
}

export function registerSlashPaletteCommands(
  insertSlash: (command: string) => void,
): void {
  registerCommand({
    id: 'slash.compact',
    label: '插入 /compact',
    detail: '压缩对话上下文',
    keywords: ['compact', '压缩'],
    run: () => insertSlash('/compact'),
  })

  registerCommand({
    id: 'slash.context',
    label: '插入 /context',
    detail: '显示上下文占用',
    keywords: ['context'],
    run: () => insertSlash('/context'),
  })

  registerCommand({
    id: 'slash.rewind',
    label: '插入 /rewind',
    detail: '回滚到检查点',
    keywords: ['rewind', 'checkpoint'],
    run: () => insertSlash('/rewind'),
  })
}
