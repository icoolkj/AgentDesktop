package com.agentdesktop.server.agent.llm;

public record LlmUsage(
        int promptTokens,
        int completionTokens,
        int totalTokens
) {
    public static LlmUsage empty() {
        return new LlmUsage(0, 0, 0);
    }
}
