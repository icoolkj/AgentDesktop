package com.agentdesktop.server.agent.settings;

import com.agentdesktop.server.agent.llm.openai.OpenAiCompatibleLlmProvider;
import com.agentdesktop.server.agent.loop.AgentStreamPolicy;

public record LlmRuntimeConfig(
        boolean enabled,
        String provider,
        String baseUrl,
        String apiKey,
        String model,
        double temperature,
        int maxTokens,
        int timeoutSeconds,
        boolean streamToolTurns,
        boolean streamAnswerTurns
) {
    public boolean isConfigured() {
        return enabled
                && OpenAiCompatibleLlmProvider.PROVIDER_ID.equalsIgnoreCase(provider)
                && apiKey != null
                && !apiKey.isBlank();
    }

    public AgentStreamPolicy streamPolicy() {
        return new AgentStreamPolicy(streamToolTurns, streamAnswerTurns);
    }
}
