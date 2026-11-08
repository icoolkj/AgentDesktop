package com.agentdesktop.server.agent.chat;

import com.agentdesktop.server.agent.tool.ToolContext;

/**
 * Run-mode routing — Claude Code {@code query.ts} parity.
 * <p>
 * Claude Code does <b>not</b> keyword-classify user messages. The REPL always enters
 * {@code queryLoop} with tools when a project is open; {@code permissionMode} comes from the UI;
 * loop continuation is {@code needsFollowUp = tool_use blocks present} only.
 * <p>
 * We mirror that structurally: workspace open → agent loop; no workspace → stream-only chat.
 */
public final class AgentRunMode {

    /** WebSocket agent_step.mode when tools are registered (Claude queryLoop). */
    public static final String WIRE_AGENT = "agent";
    /** WebSocket agent_step.mode for no-workspace streamed answer (no tool registry). */
    public static final String WIRE_STREAM = "stream";

    public enum Mode {
        /** No workspace — single streamed answer, tools not registered. */
        STREAM_ONLY,
        /** Full tool loop ({@code AgentLoopService.run}) — model decides tool use per turn. */
        AGENT_LOOP
    }

    private AgentRunMode() {
    }

    public static Mode resolve(ToolContext toolContext) {
        if (toolContext == null || !toolContext.hasWorkspace()) {
            return Mode.STREAM_ONLY;
        }
        return Mode.AGENT_LOOP;
    }
}
