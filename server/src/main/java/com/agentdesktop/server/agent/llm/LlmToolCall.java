package com.agentdesktop.server.agent.llm;

public record LlmToolCall(
        String id,
        String name,
        String argumentsJson
) {
}
