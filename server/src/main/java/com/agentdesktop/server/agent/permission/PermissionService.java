package com.agentdesktop.server.agent.permission;

import com.agentdesktop.server.ws.NdjsonEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tool permission gate — Claude Code {@code hasPermissionsToUseTool} parity.
 * <p>
 * Pipeline: deny rules → ask rules → tool.check (plan/write) → allow rules → mode fallback.
 */
@Service
public class PermissionService {

    private static final long PERMISSION_TIMEOUT_SECONDS = 120;

    private final Map<String, CompletableFuture<Boolean>> pending = new ConcurrentHashMap<>();

    public boolean isWriteTool(String toolName) {
        return "write_file".equals(toolName)
                || "search_replace".equals(toolName)
                || "delete_file".equals(toolName);
    }

    public boolean isBashTool(String toolName) {
        return "run_terminal_cmd".equals(toolName);
    }

    public boolean isDangerousTool(String toolName) {
        return isWriteTool(toolName) || isBashTool(toolName);
    }

    public boolean shouldAllowTool(
            WebSocketSession session,
            NdjsonEventPublisher events,
            String permissionMode,
            PermissionRuleSet rules,
            String toolName,
            String summary,
            String relativePath) {
        PermissionBehavior ruleBehavior = rules == null || rules.isEmpty()
                ? null
                : rules.evaluate(toolName, relativePath);
        if (ruleBehavior == PermissionBehavior.DENY) {
            return false;
        }
        if (ruleBehavior == PermissionBehavior.ASK) {
            return requestPermission(session, events, toolName, summary, relativePath);
        }

        if (isWriteTool(toolName) || isBashTool(toolName)) {
            String mode = permissionMode == null ? "ask" : permissionMode.trim().toLowerCase();
            if ("plan".equals(mode)) {
                return false;
            }
            if (ruleBehavior == PermissionBehavior.ALLOW) {
                return true;
            }
            if ("auto".equals(mode)) {
                return true;
            }
            return requestPermission(session, events, toolName, summary, relativePath);
        }

        if (ruleBehavior == PermissionBehavior.ALLOW) {
            return true;
        }
        return true;
    }

    /** @deprecated use {@link #shouldAllowTool} */
    public boolean shouldAllowWrite(
            WebSocketSession session,
            NdjsonEventPublisher events,
            String permissionMode,
            String toolName,
            String summary,
            String path) {
        return shouldAllowTool(session, events, permissionMode, PermissionRuleSet.EMPTY, toolName, summary, path);
    }

    private boolean requestPermission(
            WebSocketSession session,
            NdjsonEventPublisher events,
            String toolName,
            String summary,
            String path) {
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        pending.put(requestId, future);
        events.permissionRequest(session, requestId, toolName, summary, path);
        try {
            return future.get(PERMISSION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.complete(false);
            return false;
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            pending.remove(requestId);
        }
    }

    public void reply(String requestId, boolean allowed) {
        CompletableFuture<Boolean> future = pending.remove(requestId);
        if (future != null) {
            future.complete(allowed);
        }
    }
}
