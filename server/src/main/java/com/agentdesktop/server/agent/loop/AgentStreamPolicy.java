package com.agentdesktop.server.agent.loop;

/**
 * Per-request streaming flags for the agent loop.
 * <p>
 * Tool calls are always resolved from native API {@code tool_calls} (CLIProxyAPI-Java).
 * Streaming flags only affect SSE delivery — not tool parsing.
 */
public record AgentStreamPolicy(
        boolean streamToolTurns,
        boolean streamAnswerTurns
) {
    /** Default: matches CPA ToolTest「完整两轮」— tool + answer both non-stream. */
    public static AgentStreamPolicy cpaToolTestDefault() {
        return new AgentStreamPolicy(false, false);
    }

    /** Cursor-like: non-stream tools, stream final Markdown to user (GPT-4o / Claude via CPA). */
    public static AgentStreamPolicy cursorLike() {
        return new AgentStreamPolicy(false, true);
    }
}
