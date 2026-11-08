<script setup lang="ts">
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useWorkspaceStore } from '@renderer/stores/workspaceStore'
import { workspaceDisplayName } from '@renderer/utils/workspacePersistence'
import { PRODUCT_WEBSITE } from '@renderer/constants/productInfo'

defineProps<{
  hasWorkspace: boolean
}>()

const { rootPath } = storeToRefs(useWorkspaceStore())
const desktopApi = window.desktopApi

const workspaceLabel = computed(() =>
  rootPath.value ? workspaceDisplayName(rootPath.value) : '',
)

const mod = computed(() => (window.desktopEnv?.platform === 'darwin' ? '⌘' : 'Ctrl'))

const shortcuts = computed(() => [
  { keys: [`${mod.value}+S`], label: '保存文件' },
  { keys: [`${mod.value}+Shift+F`], label: '搜索工作区' },
  { keys: [`${mod.value}+F`], label: '在文件中查找' },
  { keys: [`${mod.value}+H`], label: '查找并替换' },
])

const tips = [
  'Git 工作区对比：编辑器内绿色为新增、红色为删除',
  'Git 侧栏可查看变更、提交、日志与同步',
  'Agent 面板中输入问题，Enter 发送，Shift+Enter 换行',
]
</script>

<template>
  <div class="editor-welcome">
    <div class="editor-welcome__icon" aria-hidden="true">
      <svg viewBox="0 0 64 64" width="56" height="56">
        <path fill="var(--ide-accent)" opacity="0.35" d="M32 8 8 22l24 14 24-14L32 8z" />
        <path fill="var(--ide-accent)" d="M32 22 8 36l24 14 24-14L32 22z" />
      </svg>
    </div>

    <h2 class="editor-welcome__title">Agent Desktop</h2>

    <p v-if="!hasWorkspace" class="editor-welcome__lead">
      使用菜单 <kbd>文件 → 打开</kbd>，或点击资源管理器中的「打开文件夹」开始。
    </p>
    <p v-else class="editor-welcome__lead">
      工作区 <strong>{{ workspaceLabel }}</strong> 已打开。从资源管理器选择文件，或在 Agent 面板中让智能体协助修改代码。
    </p>

    <section v-if="hasWorkspace" class="editor-welcome__section">
      <h3 class="editor-welcome__section-title">键盘快捷键</h3>
      <ul class="editor-welcome__shortcut-list">
        <li v-for="item in shortcuts" :key="item.label" class="editor-welcome__shortcut">
          <span class="editor-welcome__keys">
            <kbd v-for="key in item.keys" :key="key">{{ key }}</kbd>
          </span>
          <span class="editor-welcome__shortcut-label">{{ item.label }}</span>
        </li>
      </ul>
    </section>

    <section class="editor-welcome__section">
      <h3 class="editor-welcome__section-title">提示</h3>
      <ul class="editor-welcome__tip-list">
        <li v-for="tip in tips" :key="tip">{{ tip }}</li>
      </ul>
    </section>
    <p class="editor-welcome__site">
      <button
        type="button"
        class="editor-welcome__site-link"
        @click="void desktopApi.openExternal(PRODUCT_WEBSITE)"
      >
        {{ PRODUCT_WEBSITE }}
      </button>
    </p>
  </div>
</template>

<style scoped>
.editor-welcome {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  flex: 1;
  padding: 32px 24px 48px;
  text-align: center;
  color: var(--ide-muted);
}

.editor-welcome__icon {
  margin-bottom: 16px;
  opacity: 0.9;
}

.editor-welcome__title {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 600;
  color: var(--ide-fg);
}

.editor-welcome__lead {
  margin: 0 0 28px;
  font-size: 13px;
  max-width: 440px;
  line-height: 1.65;
  color: var(--ide-welcome-fg);
}

.editor-welcome__section {
  width: min(420px, 100%);
  margin-bottom: 20px;
  text-align: left;
}

.editor-welcome__section-title {
  margin: 0 0 10px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--ide-muted);
}

.editor-welcome__shortcut-list,
.editor-welcome__tip-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.editor-welcome__shortcut {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 7px 0;
  border-bottom: 1px solid color-mix(in srgb, var(--ide-border) 65%, transparent);
  font-size: 13px;
}

.editor-welcome__shortcut:last-child {
  border-bottom: none;
}

.editor-welcome__keys {
  display: inline-flex;
  gap: 4px;
  flex-shrink: 0;
}

.editor-welcome__shortcut-label {
  color: var(--ide-fg);
  text-align: right;
}

.editor-welcome__tip-list li {
  position: relative;
  padding: 4px 0 4px 14px;
  font-size: 13px;
  line-height: 1.55;
  color: var(--ide-welcome-fg);
}

.editor-welcome__tip-list li::before {
  content: '·';
  position: absolute;
  left: 0;
  color: var(--ide-accent);
  font-weight: 700;
}

.editor-welcome kbd {
  display: inline-block;
  min-width: 1.5em;
  padding: 2px 7px;
  border-radius: 4px;
  border: 1px solid var(--ide-button-border);
  background: var(--ide-button-bg);
  font-family: ui-monospace, monospace;
  font-size: 11px;
  line-height: 1.4;
  color: var(--ide-fg);
}

.editor-welcome__site {
  margin: 8px 0 0;
}

.editor-welcome__site-link {
  border: none;
  background: transparent;
  color: var(--ide-accent);
  font-size: 12px;
  cursor: pointer;
  padding: 0;
}

.editor-welcome__site-link:hover {
  text-decoration: underline;
}
</style>
