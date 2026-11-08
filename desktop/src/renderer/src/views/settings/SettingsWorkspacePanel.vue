<script setup lang="ts">
import { onMounted } from 'vue'
import { storeToRefs } from 'pinia'

import { useWorkspaceUiStore, type BottomPanelTab } from '@renderer/stores/workspaceUiStore'
import '@renderer/modules/settings/settings.css'

const workspaceUi = useWorkspaceUiStore()
const { prefs, loaded } = storeToRefs(workspaceUi)

onMounted(() => {
  void workspaceUi.init()
})

function setStartupOpen(value: boolean): void {
  void workspaceUi.updatePrefs({ bottomPanelOpenOnStartup: value })
}

function setStartupTab(tab: BottomPanelTab): void {
  void workspaceUi.updatePrefs({ bottomPanelTabOnStartup: tab })
}

function setAutoScan(value: boolean): void {
  void workspaceUi.updatePrefs({ autoScanProblemsOnOpen: value })
}
</script>

<template>
  <section class="settings-block">
    <h2>工作区面板</h2>
    <p class="settings-block__hint">
      保存后立即生效。关闭「启动时显示底部面板」后，终端与问题面板会隐藏；仍可通过菜单或快捷键临时打开。仅面板高度会在下次启动时记住。
    </p>

    <div v-if="!loaded" class="settings-block__loading">加载中…</div>

    <template v-else>
      <label class="settings-row settings-row--checkbox">
        <input
          type="checkbox"
          :checked="prefs.bottomPanelOpenOnStartup"
          @change="setStartupOpen(($event.target as HTMLInputElement).checked)"
        />
        <span class="settings-row__body">
          <span class="settings-row__title">启动时显示底部面板</span>
          <span class="settings-row__desc">关闭后，新工作区默认不展开终端或问题面板（仍可通过菜单或快捷键打开）。</span>
        </span>
      </label>

      <fieldset class="settings-fieldset" :disabled="!prefs.bottomPanelOpenOnStartup">
        <legend class="settings-fieldset__legend">默认标签页</legend>
        <label class="settings-row settings-row--radio">
          <input
            type="radio"
            name="startup-bottom-tab"
            value="terminal"
            :checked="prefs.bottomPanelTabOnStartup === 'terminal'"
            @change="setStartupTab('terminal')"
          />
          <span class="settings-row__title">终端</span>
        </label>
        <label class="settings-row settings-row--radio">
          <input
            type="radio"
            name="startup-bottom-tab"
            value="problems"
            :checked="prefs.bottomPanelTabOnStartup === 'problems'"
            @change="setStartupTab('problems')"
          />
          <span class="settings-row__title">问题</span>
        </label>
      </fieldset>

      <label class="settings-row settings-row--checkbox">
        <input
          type="checkbox"
          :checked="prefs.autoScanProblemsOnOpen"
          @change="setAutoScan(($event.target as HTMLInputElement).checked)"
        />
        <span class="settings-row__body">
          <span class="settings-row__title">打开工作区时自动扫描问题</span>
          <span class="settings-row__desc">
            关闭后不会自动运行 TypeScript / ESLint 全项目扫描；问题面板仍显示当前打开文件的编辑器诊断，也可手动点击「扫描工作区」。
          </span>
        </span>
      </label>
    </template>
  </section>
</template>

<style scoped>
.settings-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 14px;
  cursor: pointer;
}

.settings-row input {
  margin-top: 3px;
  flex-shrink: 0;
}

.settings-row__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.settings-row__title {
  font-size: 0.9rem;
  color: var(--ide-fg);
}

.settings-row__desc {
  font-size: 0.8rem;
  color: var(--ide-muted);
  line-height: 1.45;
}

.settings-fieldset {
  margin: 0 0 16px;
  padding: 12px 14px 6px;
  border: 1px solid var(--ide-border);
  border-radius: 6px;
}

.settings-fieldset:disabled {
  opacity: 0.55;
}

.settings-fieldset__legend {
  padding: 0 6px;
  font-size: 0.8rem;
  color: var(--ide-muted);
}

.settings-row--radio {
  margin-bottom: 8px;
}
</style>
