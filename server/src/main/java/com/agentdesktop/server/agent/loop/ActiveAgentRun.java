package com.agentdesktop.server.agent.loop;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * Binds the active {@link AgentRunContext} and parent WebSocket for nested tools (e.g. Task sub-agents).
 */
@Component
public class ActiveAgentRun {

    private final ThreadLocal<Binding> current = new ThreadLocal<>();

    public record Binding(AgentRunContext run, WebSocketSession session) {
    }

    public void bind(AgentRunContext run, WebSocketSession session) {
        current.set(new Binding(run, session));
    }

    public void bind(AgentRunContext run) {
        Binding existing = current.get();
        current.set(new Binding(run, existing == null ? null : existing.session()));
    }

    public void unbind() {
        current.remove();
    }

    public AgentRunContext get() {
        Binding binding = current.get();
        return binding == null ? null : binding.run();
    }

    public WebSocketSession session() {
        Binding binding = current.get();
        return binding == null ? null : binding.session();
    }
}
