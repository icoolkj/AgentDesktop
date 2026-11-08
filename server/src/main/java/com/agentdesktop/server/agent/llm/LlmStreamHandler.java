package com.agentdesktop.server.agent.llm;

@FunctionalInterface
public interface LlmStreamHandler {

    void onEvent(LlmStreamEvent event);

    default void onError(String message) {
        onEvent(new LlmStreamEvent.Error(message));
    }
}
