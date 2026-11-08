package com.agentdesktop.server.agent.extension;

/**
 * Runtime feature gates — loaded from {@code ~/.agent-desktop/features.json}.
 * All extension tools consult this instead of hard-coded enablement.
 */
public record AgentFeatureSettings(
        boolean subagentsEnabled,
        int maxSubagentDepth,
        int maxSubagentTurns,
        boolean webFetchEnabled,
        int webFetchMaxBytes,
        boolean webSearchEnabled,
        String webSearchProvider,
        boolean bashSandboxEnabled,
        String bashSandboxMode) {

    public static AgentFeatureSettings defaults() {
        return new AgentFeatureSettings(
                true,
                2,
                8,
                true,
                512 * 1024,
                true,
                "duckduckgo",
                true,
                "strict");
    }
}
