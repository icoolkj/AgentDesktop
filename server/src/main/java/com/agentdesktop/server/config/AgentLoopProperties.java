package com.agentdesktop.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent loop limits — aligned with Claude Code {@code query.ts} main thread.
 */
@ConfigurationProperties(prefix = "agent.loop")
public class AgentLoopProperties {

    /**
     * Max agentic turns (each LLM call that invokes tools = 1 turn).
     * 0 = unlimited — same as Claude Code interactive REPL when maxTurns is not set.
     */
    private int maxTurns = 0;

    /** Estimated char budget before compaction (Claude uses token window − buffer). */
    private int maxEstimatedChars = 72_000;

    /** Reserve below max before autocompact triggers — Claude {@code AUTOCOMPACT_BUFFER_TOKENS} analogue. */
    private int autoCompactBufferChars = 13_000;

    /** Parallel read-only tool cap — Claude {@code CLAUDE_CODE_MAX_TOOL_USE_CONCURRENCY} default 10. */
    private int maxToolConcurrency = 10;

    private boolean autoCompactEnabled = true;

    public int getMaxTurns() {
        return maxTurns;
    }

    public void setMaxTurns(int maxTurns) {
        this.maxTurns = maxTurns;
    }

    public boolean isUnlimited() {
        return maxTurns <= 0;
    }

    public int getMaxEstimatedChars() {
        return maxEstimatedChars;
    }

    public void setMaxEstimatedChars(int maxEstimatedChars) {
        this.maxEstimatedChars = maxEstimatedChars;
    }

    public int getAutoCompactBufferChars() {
        return autoCompactBufferChars;
    }

    public void setAutoCompactBufferChars(int autoCompactBufferChars) {
        this.autoCompactBufferChars = autoCompactBufferChars;
    }

    /** Threshold at which autocompact runs inside queryLoop. */
    public int autoCompactThresholdChars() {
        return Math.max(8_000, maxEstimatedChars - autoCompactBufferChars);
    }

    public int getMaxToolConcurrency() {
        return maxToolConcurrency;
    }

    public void setMaxToolConcurrency(int maxToolConcurrency) {
        this.maxToolConcurrency = maxToolConcurrency;
    }

    public boolean isAutoCompactEnabled() {
        return autoCompactEnabled;
    }

    public void setAutoCompactEnabled(boolean autoCompactEnabled) {
        this.autoCompactEnabled = autoCompactEnabled;
    }
}
