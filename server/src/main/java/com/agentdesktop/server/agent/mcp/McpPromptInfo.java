package com.agentdesktop.server.agent.mcp;

import java.util.List;

public record McpPromptInfo(
        String name,
        String description,
        List<String> argumentNames) {
}
