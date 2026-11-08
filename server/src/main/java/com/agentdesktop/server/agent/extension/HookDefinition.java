package com.agentdesktop.server.agent.extension;

import java.util.List;

public record HookDefinition(
        String event,
        String matcher,
        List<HookAction> actions) {

    public record HookAction(String type, String command, int timeoutSeconds) {
        public HookAction {
            timeoutSeconds = timeoutSeconds <= 0 ? 30 : timeoutSeconds;
        }
    }
}
