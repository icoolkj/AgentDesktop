package com.agentdesktop.server.ws;

import com.agentdesktop.server.dto.ChatSendParams;
import com.agentdesktop.server.dto.CheckpointRestoreParams;
import com.agentdesktop.server.dto.JsonRpcRequest;
import com.agentdesktop.server.dto.JsonRpcResponse;
import com.agentdesktop.server.dto.PermissionReplyParams;
import com.agentdesktop.server.dto.SessionIdParams;
import com.agentdesktop.server.agent.permission.PermissionService;
import com.agentdesktop.server.dto.InlineEditParams;
import com.agentdesktop.server.service.AgentChatService;
import com.agentdesktop.server.service.InlineEditService;
import com.agentdesktop.server.service.RewindService;
import com.agentdesktop.server.ws.NdjsonEventPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * WebSocket 入口：JSON-RPC 控制 + NDJSON 事件流（与 shared/ 协议对齐）。
 */
@Component
public class AgentWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentWebSocketHandler.class);

    private final AgentChatService agentChatService;
    private final InlineEditService inlineEditService;
    private final PermissionService permissionService;
    private final RewindService rewindService;
    private final NdjsonEventPublisher events;
    private final ObjectMapper objectMapper;

    public AgentWebSocketHandler(
            AgentChatService agentChatService,
            InlineEditService inlineEditService,
            PermissionService permissionService,
            RewindService rewindService,
            NdjsonEventPublisher events,
            ObjectMapper objectMapper) {
        this.agentChatService = agentChatService;
        this.inlineEditService = inlineEditService;
        this.permissionService = permissionService;
        this.rewindService = rewindService;
        this.events = events;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        // Claude Code: UI loading flips before any heavy work. Ack immediately after
        // we know the method — do not wait for agentExecutor or LLM.
        JsonNode root = objectMapper.readTree(payload);
        String method = root.path("method").asText("");
        if ("chat.send".equals(method)) {
            log.info("chat.send received, payload {} bytes, session {}", payload.length(), session.getId());
            events.agentStep(session, "received", "已收到…");
        }

        JsonRpcRequest request = objectMapper.treeToValue(root, JsonRpcRequest.class);

        if (!"2.0".equals(request.getJsonrpc())) {
            sendJsonRpc(session, JsonRpcResponse.failure(null, -32600, "Invalid JSON-RPC request"));
            return;
        }

        Object id = request.getId();
        String resolvedMethod = request.getMethod() != null ? request.getMethod() : "";

        switch (resolvedMethod) {
            case "chat.send" -> handleChatSend(session, id, request);
            case "chat.cancel" -> handleChatCancel(session, id);
            case "inline.edit" -> handleInlineEdit(session, id, request);
            case "permission.reply" -> handlePermissionReply(session, id, request);
            case "checkpoint.list" -> handleCheckpointList(session, id, request);
            case "checkpoint.restore" -> handleCheckpointRestore(session, id, request);
            case "ping" -> sendJsonRpc(session, JsonRpcResponse.success(id, Map.of("pong", true)));
            default -> sendJsonRpc(session, JsonRpcResponse.failure(id, -32601, "Unknown method: " + method));
        }
    }

    private void handleChatCancel(WebSocketSession session, Object id) {
        agentChatService.handleChatCancel(session);
        sendJsonRpcQuiet(session, JsonRpcResponse.success(id, Map.of("ok", true)));
    }

    private void handleCheckpointList(WebSocketSession session, Object id, JsonRpcRequest request) {
        SessionIdParams params = objectMapper.convertValue(
                request.getParams() != null ? request.getParams() : Map.of(),
                SessionIdParams.class);
        var checkpoints = rewindService.listCheckpoints(params.getSessionId()).stream()
                .map(c -> Map.of(
                        "turnId", c.turnId(),
                        "timestampMs", c.timestampMs(),
                        "fileCount", c.fileCount()))
                .toList();
        sendJsonRpcQuiet(session, JsonRpcResponse.success(id, Map.of("checkpoints", checkpoints)));
    }

    private void handleCheckpointRestore(WebSocketSession session, Object id, JsonRpcRequest request) {
        try {
            CheckpointRestoreParams params = objectMapper.convertValue(
                    request.getParams() != null ? request.getParams() : Map.of(),
                    CheckpointRestoreParams.class);
            RewindService.RestoreResult result =
                    rewindService.restoreFiles(params.getSessionId(), params.getTurnId());
            events.rewindRestored(session, params.getSessionId(), result.paths());
            sendJsonRpcQuiet(session, JsonRpcResponse.success(id, Map.of(
                    "ok", true,
                    "restoredFiles", result.restoredFiles(),
                    "deletedFiles", result.deletedFiles(),
                    "paths", result.paths())));
        } catch (Exception e) {
            sendJsonRpcQuiet(session, JsonRpcResponse.failure(id, -32603, e.getMessage()));
        }
    }

    private void handlePermissionReply(WebSocketSession session, Object id, JsonRpcRequest request) {
        PermissionReplyParams params = objectMapper.convertValue(
                request.getParams() != null ? request.getParams() : Map.of(),
                PermissionReplyParams.class);
        if (params.getRequestId() == null || params.getRequestId().isBlank()) {
            sendJsonRpcQuiet(session, JsonRpcResponse.failure(id, -32602, "Missing requestId"));
            return;
        }
        permissionService.reply(params.getRequestId(), params.isAllowed());
        sendJsonRpcQuiet(session, JsonRpcResponse.success(id, Map.of("ok", true)));
    }

    private void handleInlineEdit(WebSocketSession session, Object id, JsonRpcRequest request) {
        InlineEditParams params = objectMapper.convertValue(
                request.getParams() != null ? request.getParams() : Map.of(),
                InlineEditParams.class);

        inlineEditService.handleInlineEdit(session, params)
                .thenRun(() -> sendJsonRpcQuiet(session, JsonRpcResponse.success(id, Map.of("ok", true))))
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    sendJsonRpcQuiet(session, JsonRpcResponse.failure(
                            id,
                            -32603,
                            cause.getMessage() == null ? "Inline edit failed" : cause.getMessage()));
                    return null;
                });
    }

    private void handleChatSend(WebSocketSession session, Object id, JsonRpcRequest request) {
        ChatSendParams params = objectMapper.convertValue(
                request.getParams() != null ? request.getParams() : Map.of(),
                ChatSendParams.class);

        events.agentStep(session, "received", "已收到，准备执行…");

        agentChatService.handleChatSend(session, params)
                .thenRun(() -> sendJsonRpcQuiet(session, JsonRpcResponse.success(id, Map.of("ok", true))))
                .exceptionally(ex -> {
                    sendJsonRpcQuiet(session, JsonRpcResponse.failure(id, -32603, ex.getMessage()));
                    return null;
                });
    }

    private void sendJsonRpc(WebSocketSession session, JsonRpcResponse response) throws Exception {
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            }
        }
    }

    private void sendJsonRpcQuiet(WebSocketSession session, JsonRpcResponse response) {
        try {
            sendJsonRpc(session, response);
        } catch (Exception e) {
            log.error("Failed to send JSON-RPC response", e);
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        agentChatService.handleChatCancel(session);
        log.info("WebSocket closed: {} {}", session.getId(), status);
    }
}
