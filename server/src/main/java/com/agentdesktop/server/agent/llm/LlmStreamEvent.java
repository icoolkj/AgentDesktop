package com.agentdesktop.server.agent.llm;

public sealed interface LlmStreamEvent {

    record ContentDelta(String delta) implements LlmStreamEvent {
    }

    record ToolCallDelta(int index, String id, String name, String argumentsDelta) implements LlmStreamEvent {
    }

    record Completed(LlmResponse response) implements LlmStreamEvent {
    }

    record Error(String message) implements LlmStreamEvent {
    }
}
