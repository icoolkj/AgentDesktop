package com.agentdesktop.server.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

@Component
public class NdjsonEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NdjsonEventPublisher.class);

    private final ObjectMapper objectMapper;

    public NdjsonEventPublisher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void send(WebSocketSession session, Map<String, Object> event) {
        if (session == null) {
            return;
        }
        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event) + "\n"));
                }
            }
        } catch (Exception e) {
            log.warn("NDJSON send failed for event type {}: {}", event.get("type"), e.toString());
        }
    }

    public void textDelta(WebSocketSession session, String delta) {
        send(session, Map.of("type", "text_delta", "delta", delta));
    }

    public void toolStart(WebSocketSession session, String toolCallId, String name) {
        send(session, Map.of(
                "type", "tool_start",
                "toolCallId", toolCallId,
                "name", name
        ));
    }

    public void toolStart(WebSocketSession session, String toolCallId, String name, String argsJson) {
        if (argsJson != null && !argsJson.isBlank()) {
            send(session, Map.of(
                    "type", "tool_start",
                    "toolCallId", toolCallId,
                    "name", name,
                    "argsJson", argsJson
            ));
            return;
        }
        toolStart(session, toolCallId, name);
    }

    public void toolResult(WebSocketSession session, String toolCallId, String name, boolean ok, String output) {
        send(session, Map.of(
                "type", "tool_result",
                "toolCallId", toolCallId,
                "name", name,
                "ok", ok,
                "output", output == null ? "" : output
        ));
    }

    public boolean fileChanged(
            WebSocketSession session,
            String path,
            String changeKind,
            String content,
            String previousContent) {
        java.util.Map<String, Object> event = new java.util.LinkedHashMap<>();
        event.put("type", "file_changed");
        event.put("path", path);
        event.put("changeKind", changeKind);
        if (content != null) {
            event.put("content", content);
        }
        if (previousContent != null) {
            event.put("previousContent", previousContent);
        }
        return sendReliable(session, event);
    }

    /** Notifies renderer to clear Review state and reload editors after /rewind file restore. */
    public void rewindRestored(WebSocketSession session, String sessionId, java.util.List<String> paths) {
        java.util.Map<String, Object> event = new java.util.LinkedHashMap<>();
        event.put("type", "rewind_restored");
        event.put("sessionId", sessionId == null ? "" : sessionId);
        event.put("paths", paths == null ? java.util.List.of() : paths);
        send(session, event);
    }

    private boolean sendReliable(WebSocketSession session, java.util.Map<String, Object> event) {
        if (session == null) {
            log.error("NDJSON {} skipped: WebSocket session is null", event.get("type"));
            return false;
        }
        try {
            synchronized (session) {
                if (!session.isOpen()) {
                    log.error("NDJSON {} skipped: WebSocket closed", event.get("type"));
                    return false;
                }
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event) + "\n"));
                return true;
            }
        } catch (Exception e) {
            log.error("NDJSON {} send failed: {}", event.get("type"), e.toString(), e);
            return false;
        }
    }

    public void permissionRequest(
            WebSocketSession session,
            String requestId,
            String toolName,
            String summary,
            String path) {
        java.util.Map<String, Object> event = new java.util.LinkedHashMap<>();
        event.put("type", "permission_request");
        event.put("requestId", requestId);
        event.put("toolName", toolName);
        event.put("summary", summary == null ? toolName : summary);
        if (path != null && !path.isBlank()) {
            event.put("path", path);
        }
        send(session, event);
    }

    public void done(WebSocketSession session, String sessionId) {
        send(session, Map.of("type", "done", "sessionId", sessionId));
    }

    public void error(WebSocketSession session, String message) {
        send(session, Map.of("type", "error", "message", message == null ? "Unknown error" : message));
    }

    public void agentStep(WebSocketSession session, String phase, String label) {
        agentStep(session, phase, label, null, null, null);
    }

    public void agentStep(
            WebSocketSession session,
            String phase,
            String label,
            Integer turn,
            String detail,
            String mode) {
        java.util.Map<String, Object> event = new java.util.LinkedHashMap<>();
        event.put("type", "agent_step");
        event.put("phase", phase);
        event.put("label", label);
        if (turn != null) {
            event.put("turn", turn);
        }
        if (detail != null && !detail.isBlank()) {
            event.put("detail", detail);
        }
        if (mode != null && !mode.isBlank()) {
            event.put("mode", mode);
        }
        send(session, event);
    }

    /** Claude Code {@code compact_boundary} system message for transcript JSONL. */
    public void compactBoundary(
            WebSocketSession session,
            String trigger,
            String summary,
            int usedChars,
            int maxChars) {
        java.util.Map<String, Object> event = new java.util.LinkedHashMap<>();
        event.put("type", "compact_boundary");
        event.put("trigger", trigger);
        event.put("summary", summary == null ? "" : summary);
        event.put("usedChars", usedChars);
        event.put("maxChars", maxChars);
        send(session, event);
    }

    public void contextBreakdown(WebSocketSession session, java.util.Map<String, Object> breakdown) {
        java.util.Map<String, Object> event = new java.util.LinkedHashMap<>();
        event.put("type", "context_breakdown");
        event.put("breakdown", breakdown);
        send(session, event);
    }

    public void subagentStart(
            WebSocketSession session,
            String subagentId,
            String subagentType,
            String description,
            int depth) {
        java.util.Map<String, Object> event = new java.util.LinkedHashMap<>();
        event.put("type", "subagent_start");
        event.put("subagentId", subagentId);
        event.put("subagentType", subagentType);
        event.put("description", description == null ? "" : description);
        event.put("depth", depth);
        send(session, event);
    }

    public void subagentDone(
            WebSocketSession session,
            String subagentId,
            String subagentType,
            boolean ok,
            String summary) {
        java.util.Map<String, Object> event = new java.util.LinkedHashMap<>();
        event.put("type", "subagent_done");
        event.put("subagentId", subagentId);
        event.put("subagentType", subagentType);
        event.put("ok", ok);
        event.put("summary", summary == null ? "" : summary);
        send(session, event);
    }

    /** Cursor Ctrl+K inline edit — streaming replacement chunks. */
    public void inlineEditDelta(WebSocketSession session, String requestId, String delta) {
        java.util.Map<String, Object> event = new java.util.LinkedHashMap<>();
        event.put("type", "inline_edit_delta");
        event.put("requestId", requestId == null ? "" : requestId);
        event.put("delta", delta == null ? "" : delta);
        send(session, event);
    }

    public void inlineEditDone(WebSocketSession session, String requestId, String replacement) {
        java.util.Map<String, Object> event = new java.util.LinkedHashMap<>();
        event.put("type", "inline_edit_done");
        event.put("requestId", requestId == null ? "" : requestId);
        event.put("replacement", replacement == null ? "" : replacement);
        send(session, event);
    }

    public void inlineEditError(WebSocketSession session, String requestId, String message) {
        java.util.Map<String, Object> event = new java.util.LinkedHashMap<>();
        event.put("type", "inline_edit_error");
        event.put("requestId", requestId == null ? "" : requestId);
        event.put("message", message == null ? "Inline edit failed" : message);
        send(session, event);
    }
}
