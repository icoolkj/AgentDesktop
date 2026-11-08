package com.agentdesktop.server.agent.llm;

/**
 * Pluggable LLM backend (OpenAI-compatible APIs, Azure, local Ollama, etc.).
 */
public interface LlmProvider {

    String providerId();

    boolean isConfigured();

    LlmResponse complete(LlmChatRequest request);

    void streamComplete(LlmChatRequest request, LlmStreamHandler handler);
}
