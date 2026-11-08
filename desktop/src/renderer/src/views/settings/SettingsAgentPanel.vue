<script setup lang="ts">
import { storeToRefs } from 'pinia'
import type { PermissionMode } from '@agent-desktop/shared-types'
import { useAgentSettingsStore } from '@renderer/stores/agentSettingsStore'
import '@renderer/modules/settings/settings.css'

const agentSettings = useAgentSettingsStore()
const { permissionMode, permissionModeLabel } = storeToRefs(agentSettings)

function onModeChange(event: Event) {
  agentSettings.setPermissionMode((event.target as HTMLSelectElement).value as PermissionMode)
}
</script>

<template>
  <div class="settings-panel">
    <section class="settings-section">
      <h2>Agent 行为</h2>
      <p class="settings-section__hint">
        控制 Agent 调用写文件工具（write_file / search_replace）时的确认策略。Plan 模式仅暴露只读工具。
      </p>

      <label class="settings-field">
        <span>文件修改权限</span>
        <select :value="permissionMode" @change="onModeChange">
          <option value="ask">Ask — 每次询问（推荐）</option>
          <option value="auto">Auto — 自动允许写文件</option>
          <option value="plan">Plan — 只读（禁用写工具）</option>
        </select>
      </label>

      <p class="settings-section__hint">当前：{{ permissionModeLabel }}</p>

      <ul class="settings-list">
        <li><strong>Ask</strong>：写文件前弹出确认对话框，可单次允许或切换为 Auto。</li>
        <li><strong>Auto</strong>：写文件立即落盘（与 Claude Code 一致），可在 Chat 面板 Reject 回滚。</li>
        <li><strong>Plan</strong>：Agent 只能 read/list/grep/glob，适合分析规划。</li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.settings-list {
  margin: 12px 0 0;
  padding-left: 18px;
  font-size: 0.78rem;
  color: var(--ide-muted);
  line-height: 1.6;
}
</style>
