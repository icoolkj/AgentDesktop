package com.agentdesktop.server.agent.mcp;

public record McpResourceInfo(
        String uri,
        String name,
        String description,
        String mimeType) {
}
