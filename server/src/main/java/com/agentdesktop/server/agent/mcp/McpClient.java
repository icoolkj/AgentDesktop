package com.agentdesktop.server.agent.mcp;

import java.util.List;
import java.util.Map;

public interface McpClient {

    boolean isConnected(String serverName);

    void shutdownServer(String serverName);

    void shutdownAll();

    List<McpToolInfo> listTools(String serverName, McpServerConfig config) throws Exception;

    List<McpPromptInfo> listPrompts(String serverName, McpServerConfig config) throws Exception;

    List<McpResourceInfo> listResources(String serverName, McpServerConfig config) throws Exception;

    String getPrompt(String serverName, String promptName, Map<String, String> arguments) throws Exception;

    String readResource(String serverName, String uri) throws Exception;

    String callTool(String serverName, String toolName, Map<String, Object> arguments) throws Exception;
}
