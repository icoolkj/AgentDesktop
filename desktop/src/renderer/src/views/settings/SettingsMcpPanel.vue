<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { storeToRefs } from 'pinia'

import { useSettingsStore } from '@renderer/stores/settingsStore'
import { emptyMcpServer, isRemoteTransport, type McpServerEntry } from '@renderer/types/mcpSettings'
import '@renderer/modules/settings/settings.css'

const settings = useSettingsStore()
const { saving, error, success } = storeToRefs(settings)

const loading = ref(false)
const testing = ref<string | null>(null)
const testMessage = ref<string | null>(null)
const servers = ref<McpServerEntry[]>([])
const storageLabel = ref('')
const editingIndex = ref<number | null>(null)
const draft = reactive(emptyMcpServer())

const envText = ref('')
const argsText = ref('')
const headersText = ref('')

function openEditor(index: number | null): void {
  testMessage.value = null
  if (index === null) {
    editingIndex.value = -1
    Object.assign(draft, emptyMcpServer(`server-${servers.value.length + 1}`))
    argsText.value = ''
    envText.value = ''
    headersText.value = ''
    return
  }
  editingIndex.value = index
  const server = servers.value[index]
  if (!server) return
  Object.assign(draft, structuredClone(server))
  argsText.value = server.args.join('\n')
  envText.value = Object.entries(server.env)
    .map(([key, value]) => `${key}=${value}`)
    .join('\n')
  headersText.value = Object.entries(server.headers ?? {})
    .map(([key, value]) => `${key}=${value}`)
    .join('\n')
}

function parseArgs(text: string): string[] {
  return text
    .split('\n')
    .map(line => line.trim())
    .filter(Boolean)
}

function parseKeyValue(text: string): Record<string, string> {
  const map: Record<string, string> = {}
  for (const line of text.split('\n')) {
    const trimmed = line.trim()
    if (!trimmed || !trimmed.includes('=')) continue
    const idx = trimmed.indexOf('=')
    map[trimmed.slice(0, idx).trim()] = trimmed.slice(idx + 1)
  }
  return map
}

function cancelEditor(): void {
  editingIndex.value = null
  Object.assign(draft, emptyMcpServer())
  argsText.value = ''
  envText.value = ''
  headersText.value = ''
}

function validateDraft(): boolean {
  if (!draft.name.trim()) return false
  if (isRemoteTransport(draft.transport)) {
    return Boolean(draft.url.trim())
  }
  return Boolean(draft.command.trim())
}

function applyDraft(): void {
  if (!validateDraft()) return
  const entry: McpServerEntry = {
    ...structuredClone(draft),
    name: draft.name.trim(),
    command: draft.command.trim(),
    url: draft.url.trim(),
    args: parseArgs(argsText.value),
    env: parseKeyValue(envText.value),
    headers: parseKeyValue(headersText.value),
  }
  if (editingIndex.value === -1) {
    servers.value.push(entry)
  } else if (editingIndex.value !== null && editingIndex.value >= 0) {
    servers.value[editingIndex.value] = entry
  }
  editingIndex.value = null
}

function removeServer(index: number): void {
  servers.value.splice(index, 1)
  if (editingIndex.value === index) editingIndex.value = null
}

function serverSummary(server: McpServerEntry): string {
  if (isRemoteTransport(server.transport)) {
    return `${server.transport.toUpperCase()} → ${server.url}`
  }
  return `${server.command} ${server.args.join(' ')}`.trim()
}

async function reload(): Promise<void> {
  loading.value = true
  testMessage.value = null
  try {
    const data = await settings.loadMcpSettings()
    if (data) {
      servers.value = data.servers.map(normalizeServer)
      storageLabel.value = data.configPath
    }
  } finally {
    loading.value = false
  }
}

function normalizeServer(server: McpServerEntry): McpServerEntry {
  return {
    ...emptyMcpServer(server.name),
    ...server,
    transport: server.transport ?? (server.url ? 'http' : 'stdio'),
    url: server.url ?? '',
    headers: server.headers ?? {},
  }
}

async function save(): Promise<void> {
  if (editingIndex.value !== null) applyDraft()
  const ok = await settings.saveMcpSettings({ configPath: storageLabel.value, servers: servers.value })
  if (ok && settings.mcp) {
    servers.value = settings.mcp.servers.map(normalizeServer)
    storageLabel.value = settings.mcp.configPath
  }
}

async function testServer(name: string): Promise<void> {
  testing.value = name
  testMessage.value = null
  try {
    const result = await settings.testMcpServer(name)
    testMessage.value = result ? `${name}: ${result.message}` : 'Test failed'
    if (result?.ok) await reload()
  } finally {
    testing.value = null
  }
}

onMounted(() => {
  void reload()
})
</script>

<template>
  <div class="settings-panel">
    <section class="settings-section">
      <h2>MCP 服务器</h2>
      <p class="settings-section__hint">
        配置 Model Context Protocol 服务器。支持 <strong>stdio</strong> 子进程与
        <strong>http/sse</strong> 远程 URL。配置保存在 MySQL
        <code>mcp_server_config</code>，Redis 缓存；旧版 <code>mcp.json</code> 首次自动迁移。
      </p>

      <p v-if="storageLabel" class="settings-section__hint settings-section__hint--path">{{ storageLabel }}</p>
      <p v-if="loading" class="settings-section__hint">加载中…</p>
      <p v-if="error" class="settings-alert settings-alert--error">{{ error }}</p>
      <p v-if="success" class="settings-alert settings-alert--success">{{ success }}</p>
      <p v-if="testMessage" class="settings-section__hint">{{ testMessage }}</p>

      <div class="mcp-list">
        <div v-for="(server, index) in servers" :key="server.name" class="mcp-card">
          <div class="mcp-card__header">
            <strong>{{ server.name }}</strong>
            <span class="mcp-card__badge">{{ server.transport || 'stdio' }}</span>
            <span
              class="mcp-card__status"
              :class="{
                'mcp-card__status--ok': server.connected,
                'mcp-card__status--off': !server.enabled,
              }"
            >
              {{
                !server.enabled
                  ? '已禁用'
                  : server.connected
                    ? `已连接 · ${server.toolCount ?? 0} 工具`
                    : server.error
                      ? `错误: ${server.error}`
                      : '未连接'
              }}
            </span>
          </div>
          <code class="mcp-card__cmd">{{ serverSummary(server) }}</code>
          <div class="mcp-card__actions">
            <button type="button" class="settings-btn" @click="openEditor(index)">编辑</button>
            <button
              type="button"
              class="settings-btn"
              :disabled="testing === server.name || !server.enabled"
              @click="testServer(server.name)"
            >
              {{ testing === server.name ? '测试中…' : '测试连接' }}
            </button>
            <button type="button" class="settings-btn settings-btn--danger" @click="removeServer(index)">
              删除
            </button>
          </div>
        </div>

        <p v-if="!servers.length && !loading" class="settings-section__hint">尚未配置 MCP 服务器。</p>
      </div>

      <button type="button" class="settings-btn" @click="openEditor(null)">添加服务器</button>

      <div v-if="editingIndex !== null" class="mcp-editor">
        <h3 class="settings-subsection">{{ editingIndex === -1 ? '新建服务器' : '编辑服务器' }}</h3>

        <label class="settings-field">
          <span>名称</span>
          <input v-model="draft.name" type="text" placeholder="filesystem" required />
        </label>

        <label class="settings-field settings-field--checkbox">
          <input v-model="draft.enabled" type="checkbox" />
          启用
        </label>

        <label class="settings-field">
          <span>传输方式</span>
          <select v-model="draft.transport">
            <option value="stdio">stdio（本地子进程）</option>
            <option value="http">http（远程 JSON-RPC）</option>
            <option value="sse">sse（远程 SSE/HTTP）</option>
          </select>
        </label>

        <template v-if="isRemoteTransport(draft.transport)">
          <label class="settings-field">
            <span>URL</span>
            <input v-model="draft.url" type="url" placeholder="https://mcp.example.com/rpc" required />
          </label>
          <label class="settings-field">
            <span>Headers（KEY=VALUE，每行一个）</span>
            <textarea v-model="headersText" rows="2" placeholder="Authorization=Bearer ..." />
          </label>
        </template>

        <template v-else>
          <label class="settings-field">
            <span>Command</span>
            <input v-model="draft.command" type="text" placeholder="npx" required />
          </label>
          <label class="settings-field">
            <span>Args（每行一个）</span>
            <textarea v-model="argsText" rows="3" placeholder="-y&#10;@modelcontextprotocol/server-filesystem" />
          </label>
          <label class="settings-field">
            <span>Env（KEY=VALUE，每行一个）</span>
            <textarea v-model="envText" rows="2" placeholder="API_KEY=..." />
          </label>
          <label class="settings-field">
            <span>工作目录 (cwd，可选)</span>
            <input v-model="draft.cwd" type="text" placeholder="C:/workspace" />
          </label>
        </template>

        <div class="settings-form__actions">
          <button type="button" class="settings-btn" @click="cancelEditor">取消</button>
          <button type="button" class="settings-btn settings-btn--primary" @click="applyDraft">应用到列表</button>
        </div>
      </div>
    </section>

    <div class="settings-form__actions">
      <button type="button" class="settings-btn" :disabled="loading || saving" @click="reload">
        {{ loading ? '加载中…' : '重新加载' }}
      </button>
      <button type="button" class="settings-btn settings-btn--primary" :disabled="saving" @click="save">
        {{ saving ? '保存中…' : '保存到数据库' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.mcp-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 12px 0;
}

.mcp-card {
  padding: 12px 14px;
  border: 1px solid var(--ide-border);
  border-radius: 6px;
  background: color-mix(in srgb, var(--ide-sidebar-bg) 40%, var(--ide-editor-bg));
}

.mcp-card__header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 6px;
}

.mcp-card__badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
  background: var(--ide-hover-overlay);
  color: var(--ide-muted);
  text-transform: uppercase;
}

.mcp-card__status {
  margin-left: auto;
  font-size: 11px;
  color: var(--ide-muted);
}

.mcp-card__status--ok {
  color: var(--ide-success, #3fb950);
}

.mcp-card__status--off {
  color: var(--ide-muted);
}

.mcp-card__cmd {
  display: block;
  font-size: 11px;
  color: var(--ide-muted);
  margin-bottom: 8px;
  word-break: break-all;
}

.mcp-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.mcp-editor {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--ide-border);
}

.settings-btn--danger {
  color: var(--ide-error, #f85149);
}
</style>
