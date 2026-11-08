package com.agentdesktop.server.agent.llm;

import java.util.List;

public record LlmResponse(
        String content,
        List<LlmToolCall> toolCalls,
        String finishReason,
        LlmUsage usage
) {
    public LlmResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        usage = usage == null ? LlmUsage.empty() : usage;
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
