package com.agentdesktop.server.agent.mcp;

import java.util.List;
import java.util.Map;

public record McpToolInfo(String name, String description, Map<String, Object> inputSchema) {
}
