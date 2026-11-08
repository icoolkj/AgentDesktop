package com.agentdesktop.server.agent.llm;

public enum LlmRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL;

    public String apiValue() {
        return name().toLowerCase();
    }

    public static LlmRole fromApiValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("LLM role is required");
        }
        return LlmRole.valueOf(value.trim().toUpperCase());
    }
}
