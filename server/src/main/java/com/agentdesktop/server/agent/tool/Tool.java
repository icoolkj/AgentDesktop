package com.agentdesktop.server.agent.tool;

import com.agentdesktop.server.agent.llm.LlmToolDefinition;

public interface Tool {

    String name();

    String description();

    LlmToolDefinition definition();

    ToolResult execute(String argumentsJson, ToolContext context);

    /** Read-only tools may run in parallel within one assistant turn. */
    default boolean concurrencySafe() {
        return false;
    }

    /** Gate tool exposure — consult {@link ToolContext#featuresOrDefault()} instead of hard-coding. */
    default boolean available(ToolContext context) {
        return true;
    }
}
