package com.agentdesktop.server.agent.llm;

import java.util.Map;

public record LlmToolDefinition(
        String name,
        String description,
        Map<String, Object> parameters
) {
    public LlmToolDefinition {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
