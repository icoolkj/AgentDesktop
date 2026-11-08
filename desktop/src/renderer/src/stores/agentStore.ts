import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { AgentEvent, JsonRpcRequest, JsonRpcResponse } from '@agent-desktop/shared-types'
import { parseNdjsonLine } from '@agent-desktop/shared-types'

export type ConnectionStatus = 'connecting' | 'connected' | 'disconnected' | 'error'
export type AgentEventListener = (event: AgentEvent) => void

const PING_INTERVAL_MS = 25_000
const RECONNECT_BASE_MS = 2_000
const RECONNECT_MAX_MS = 30_000

export const useAgentStore = defineStore('agent', () => {
  const status = ref<ConnectionStatus>('connecting')
  const wsUrl = ref<string | null>(null)
  const error = ref<string | null>(null)
  const ws = ref<WebSocket | null>(null)
  /** True while connect/reconnect is replacing the socket — ignore transient disconnects. */
  const reconnecting = ref(false)

  let disconnectListener: (() => void) | null = null
  let pingTimer: ReturnType<typeof setInterval> | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let reconnectAttempt = 0
  let manualClose = false
  let pingRequestId = 0
  let rpcRequestId = 0
  const pendingRpc = new Map<
    number | string,
    { resolve: (value: unknown) => void; reject: (reason: Error) => void }
  >()
  const eventListeners = new Set<AgentEventListener>()

  function setError(message: string | null) {
    error.value = message
  }

  function subscribeEvents(listener: AgentEventListener): () => void {
    eventListeners.add(listener)
    return () => eventListeners.delete(listener)
  }

  function dispatchAgentEvent(event: AgentEvent) {
    for (const listener of eventListeners) {
      listener(event)
    }
  }

  function clearPingTimer() {
    if (pingTimer) {
      clearInterval(pingTimer)
      pingTimer = null
    }
  }

  function clearReconnectTimer() {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  function scheduleReconnect() {
    if (manualClose || reconnectTimer) return

    const delay = Math.min(RECONNECT_BASE_MS * 2 ** reconnectAttempt, RECONNECT_MAX_MS)
    reconnectAttempt += 1
    status.value = 'connecting'
    error.value = `连接中断，${Math.round(delay / 1000)} 秒后自动重连…`

    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      void connectInternal(false)
    }, delay)
  }

  function startPing(socket: WebSocket) {
    clearPingTimer()
    pingTimer = setInterval(() => {
      if (socket.readyState !== WebSocket.OPEN) return
      const request: JsonRpcRequest = {
        jsonrpc: '2.0',
        method: 'ping',
        id: ++pingRequestId,
      }
      socket.send(JSON.stringify(request))
    }, PING_INTERVAL_MS)
  }

  function bindWebSocketHandlers(socket: WebSocket) {
    socket.onmessage = (event: MessageEvent) => {
      const raw = event.data
      const data =
        typeof raw === 'string'
          ? raw
          : raw instanceof ArrayBuffer
            ? new TextDecoder().decode(raw)
            : ''
      for (const line of data.split('\n')) {
        const trimmed = line.trim()
        if (!trimmed) continue
        try {
          const parsed = JSON.parse(trimmed) as JsonRpcResponse
          if (parsed.jsonrpc === '2.0' && parsed.id != null && (parsed.result !== undefined || parsed.error)) {
            const pending = pendingRpc.get(parsed.id)
            if (pending) {
              pendingRpc.delete(parsed.id)
              if (parsed.error) {
                pending.reject(new Error(parsed.error.message))
              } else {
                pending.resolve(parsed.result)
              }
              continue
            }
          }
        } catch {
          // not JSON-RPC
        }
        const parsed = parseNdjsonLine(line)
        if (parsed) dispatchAgentEvent(parsed)
      }
    }

    socket.onclose = () => {
      clearPingTimer()
      status.value = 'disconnected'
      if (!manualClose) {
        scheduleReconnect()
      }
    }

    socket.onerror = () => {
      if (status.value === 'connected') return
      status.value = 'error'
      error.value = 'WebSocket 连接失败'
    }
  }

  async function connectInternal(resetBackoff: boolean): Promise<WebSocket | null> {
    reconnecting.value = true
    clearReconnectTimer()
    if (resetBackoff) {
      reconnectAttempt = 0
      manualClose = false
    }

    status.value = 'connecting'
    if (resetBackoff) {
      error.value = null
    }

    try {
      const info = await window.agentApi.getConnection()
      wsUrl.value = info.wsUrl

      manualClose = true
      ws.value?.close()
      manualClose = false

      const socket = new WebSocket(info.wsUrl)
      ws.value = socket
      bindWebSocketHandlers(socket)

      await new Promise<void>((resolve, reject) => {
        const timeout = setTimeout(() => reject(new Error('WebSocket 连接超时（15s）')), 15_000)

        socket.onopen = () => {
          clearTimeout(timeout)
          status.value = 'connected'
          error.value = null
          reconnectAttempt = 0
          startPing(socket)
          resolve()
        }

        const priorOnError = socket.onerror
        socket.onerror = event => {
          priorOnError?.call(socket, event)
          clearTimeout(timeout)
          status.value = 'error'
          error.value = 'WebSocket 连接失败'
          reject(new Error('WebSocket 连接失败'))
        }
      })

      return socket
    } catch (err) {
      status.value = 'error'
      error.value = err instanceof Error ? err.message : String(err)
      if (!manualClose) {
        scheduleReconnect()
      }
      return null
    } finally {
      reconnecting.value = false
    }
  }

  async function connect(): Promise<WebSocket | null> {
    return connectInternal(true)
  }

  async function reconnect(): Promise<WebSocket | null> {
    clearReconnectTimer()
    reconnectAttempt = 0
    return connectInternal(true)
  }

  async function restart(): Promise<WebSocket | null> {
    clearReconnectTimer()
    clearPingTimer()
    reconnectAttempt = 0
    manualClose = true
    ws.value?.close()
    manualClose = false
    await window.agentApi.restart()
    return connectInternal(true)
  }

  function initLifecycle() {
    if (disconnectListener) return

    disconnectListener = window.agentApi.onDisconnected(() => {
      status.value = 'disconnected'
      error.value = 'Agent 后端进程已退出，正在自动重启…'
      scheduleReconnect()
    })
  }

  async function invokeRpc<T = unknown>(
    method: string,
    params?: unknown,
    timeoutMs = 30_000,
  ): Promise<T> {
    const socket = ws.value
    if (!socket || socket.readyState !== WebSocket.OPEN) {
      throw new Error('Not connected to agent')
    }
    const id = ++rpcRequestId
    const request: JsonRpcRequest = {
      jsonrpc: '2.0',
      method,
      params,
      id,
    }
    return new Promise<T>((resolve, reject) => {
      pendingRpc.set(id, {
        resolve: value => resolve(value as T),
        reject,
      })
      socket.send(JSON.stringify(request))
      setTimeout(() => {
        if (pendingRpc.has(id)) {
          pendingRpc.delete(id)
          reject(new Error(`RPC timeout: ${method}`))
        }
      }, timeoutMs)
    })
  }

  function cleanup() {
    disconnectListener?.()
    disconnectListener = null
    clearPingTimer()
    clearReconnectTimer()
    eventListeners.clear()
    pendingRpc.clear()
    manualClose = true
    ws.value?.close()
    ws.value = null
    manualClose = false
  }

  return {
    status,
    wsUrl,
    error,
    ws,
    reconnecting,
    setError,
    subscribeEvents,
    connect,
    reconnect,
    restart,
    invokeRpc,
    initLifecycle,
    cleanup,
  }
})
