import http from 'node:http'
import fs from 'node:fs/promises'
import path from 'node:path'

import { WebSocketServer, type WebSocket } from 'ws'

export interface MockAgentServer {
  wsUrl: string
  close: () => Promise<void>
}

interface JsonRpcRequest {
  jsonrpc?: string
  id?: number | string
  method?: string
  params?: {
    message?: string
    workspaceRoot?: string
    sessionId?: string
  }
}

function sendJsonRpc(ws: WebSocket, id: number | string | undefined, result: unknown): void {
  ws.send(JSON.stringify({ jsonrpc: '2.0', id, result }))
}

function sendNdjson(ws: WebSocket, event: Record<string, unknown>): void {
  ws.send(`${JSON.stringify(event)}\n`)
}

async function simulateWriteFileTurn(ws: WebSocket, workspaceRoot: string | undefined): Promise<void> {
  if (!workspaceRoot) {
    sendNdjson(ws, { type: 'error', message: 'missing workspaceRoot' })
    sendNdjson(ws, { type: 'done' })
    return
  }

  const targetFile = path.join(workspaceRoot, 'hello.txt')
  let previousContent = ''
  try {
    previousContent = await fs.readFile(targetFile, 'utf8')
  } catch {
    previousContent = ''
  }

  const newContent = 'from-agent-ws'
  await fs.writeFile(targetFile, newContent, 'utf8')

  sendNdjson(ws, { type: 'agent_step', phase: 'tool', label: 'write_file', turn: 1 })
  sendNdjson(ws, {
    type: 'tool_start',
    toolCallId: 'e2e-tc-1',
    name: 'write_file',
    argsJson: JSON.stringify({ path: 'hello.txt', content: newContent }),
  })
  sendNdjson(ws, {
    type: 'tool_result',
    toolCallId: 'e2e-tc-1',
    name: 'write_file',
    ok: true,
    output: 'Wrote hello.txt',
  })
  sendNdjson(ws, {
    type: 'file_changed',
    path: targetFile,
    changeKind: previousContent ? 'modified' : 'created',
    content: newContent,
    previousContent: previousContent || undefined,
  })
  sendNdjson(ws, { type: 'text_delta', delta: 'Updated hello.txt via mock agent.' })
  sendNdjson(ws, { type: 'done', sessionId: 'e2e-session' })
}

export async function startMockAgentServer(): Promise<MockAgentServer> {
  const server = http.createServer()
  const wss = new WebSocketServer({ noServer: true })

  server.on('upgrade', (request, socket, head) => {
    const url = request.url ?? ''
    if (!url.startsWith('/ws')) {
      socket.destroy()
      return
    }
    wss.handleUpgrade(request, socket, head, ws => {
      wss.emit('connection', ws, request)
    })
  })

  wss.on('connection', ws => {
    ws.on('message', raw => {
      const text = typeof raw === 'string' ? raw : raw.toString()
      for (const line of text.split('\n')) {
        const trimmed = line.trim()
        if (!trimmed) continue
        let req: JsonRpcRequest
        try {
          req = JSON.parse(trimmed) as JsonRpcRequest
        } catch {
          continue
        }

        if (req.method === 'ping') {
          sendJsonRpc(ws, req.id, { pong: true })
          continue
        }

        console.log(`[mock] received ${req.method} workspaceRoot=${req.params?.workspaceRoot ?? 'none'}`)

        if (req.method === 'chat.send') {
          sendJsonRpc(ws, req.id, { ok: true })
          void simulateWriteFileTurn(ws, req.params?.workspaceRoot)
        }
      }
    })
  })

  await new Promise<void>(resolve => {
    server.listen(0, '127.0.0.1', () => resolve())
  })

  const address = server.address()
  if (!address || typeof address === 'string') {
    throw new Error('mock agent server failed to bind')
  }

  const wsUrl = `ws://127.0.0.1:${address.port}/ws`

  return {
    wsUrl,
    close: async () => {
      await new Promise<void>((resolve, reject) => {
        wss.close(err => (err ? reject(err) : resolve()))
      })
      await new Promise<void>((resolve, reject) => {
        server.close(err => (err ? reject(err) : resolve()))
      })
    },
  }
}
