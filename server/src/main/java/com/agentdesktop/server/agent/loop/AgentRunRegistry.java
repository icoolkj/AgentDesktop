package com.agentdesktop.server.agent.loop;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentRunRegistry {

    private final ConcurrentHashMap<String, AgentRunContext> active = new ConcurrentHashMap<>();

    public AgentRunContext begin(String wsSessionId) {
        AgentRunContext context = new AgentRunContext(wsSessionId);
        active.put(wsSessionId, context);
        return context;
    }

    public boolean hasActiveRun(String wsSessionId) {
        return active.containsKey(wsSessionId);
    }

    public void cancel(String wsSessionId) {
        AgentRunContext context = active.get(wsSessionId);
        if (context != null) {
            context.cancel();
        }
    }

    public void end(String wsSessionId) {
        active.remove(wsSessionId);
    }

    /** Clear a stuck run so a new chat.send can proceed (stale finally / crashed worker). */
    public void forceEnd(String wsSessionId) {
        AgentRunContext context = active.remove(wsSessionId);
        if (context != null) {
            context.cancel();
        }
    }
}
