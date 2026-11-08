package com.agentdesktop.server.agent.mcp;

import com.agentdesktop.server.agent.llm.LlmToolDefinition;
import com.agentdesktop.server.agent.tool.ToolContext;
import com.agentdesktop.server.agent.tool.ToolResult;
import com.agentdesktop.server.dto.McpServerDto;
import com.agentdesktop.server.dto.McpTestResultDto;
import com.agentdesktop.server.dto.McpToolDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads MCP server configs and exposes their tools to the agent loop.
 */
@Component
public class McpToolBridge {

    private static final Logger log = LoggerFactory.getLogger(McpToolBridge.class);

    private final McpConfigLoader configLoader;
    private final McpClientFactory clientFactory;
    private final ObjectMapper objectMapper;

    private volatile List<McpToolDescriptor> cachedTools = List.of();

    public McpToolBridge(McpConfigLoader configLoader, McpClientFactory clientFactory, ObjectMapper objectMapper) {
        this.configLoader = configLoader;
        this.clientFactory = clientFactory;
        this.objectMapper = objectMapper;
    }

    public record McpToolDescriptor(
            String fullName,
            String serverName,
            String toolName,
            String description,
            Map<String, Object> inputSchema) {
    }

    public boolean isMcpTool(String name) {
        return name != null && name.startsWith("mcp__");
    }

    public void invalidateCache() {
        clientFactory.shutdownAll();
        cachedTools = List.of();
    }

    public List<LlmToolDefinition> definitions(ToolContext context) {
        if (!context.hasWorkspace()) {
            return List.of();
        }
        refreshIfNeeded();
        List<LlmToolDefinition> defs = new ArrayList<>();
        for (McpToolDescriptor tool : cachedTools) {
            Map<String, Object> params = tool.inputSchema() != null && !tool.inputSchema().isEmpty()
                    ? tool.inputSchema()
                    : Map.of(
                            "type", "object",
                            "properties", Map.of(),
                            "additionalProperties", true);
            defs.add(new LlmToolDefinition(
                    tool.fullName(),
                    "[MCP:" + tool.serverName() + "] " + tool.description(),
                    params));
        }
        return defs;
    }

    public ToolResult execute(String fullName, String argumentsJson, ToolContext context) {
        refreshIfNeeded();
        McpToolDescriptor descriptor = cachedTools.stream()
                .filter(t -> t.fullName().equals(fullName))
                .findFirst()
                .orElse(null);
        if (descriptor == null) {
            return ToolResult.failure("Unknown MCP tool: " + fullName);
        }

        try {
            JsonNode argsRoot = objectMapper.readTree(
                    argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
            Map<String, Object> argMap = parseToolArguments(argsRoot);
            McpServerConfig config = configLoader.loadServers().get(descriptor.serverName());
            if (config == null) {
                return ToolResult.failure("MCP server not configured: " + descriptor.serverName());
            }
            McpClient client = clientFactory.clientFor(config);
            String output = client.callTool(descriptor.serverName(), descriptor.toolName(), argMap);
            return ToolResult.success(output == null ? "" : output);
        } catch (Exception e) {
            log.warn("MCP tool {} failed", fullName, e);
            return ToolResult.failure("MCP error: " + e.getMessage());
        }
    }

    public List<McpServerDto> serverStatuses() {
        Map<String, McpServerConfig> servers = configLoader.loadAllServers();
        List<McpServerDto> result = new ArrayList<>();
        for (Map.Entry<String, McpServerConfig> entry : servers.entrySet()) {
            McpServerDto dto = toDto(entry.getKey(), entry.getValue());
            if (!entry.getValue().enabled()) {
                dto.setConnected(false);
                dto.setToolCount(0);
                result.add(dto);
                continue;
            }
            try {
                McpClient client = clientFactory.clientFor(entry.getValue());
                List<McpToolInfo> tools = client.listTools(entry.getKey(), entry.getValue());
                dto.setConnected(client.isConnected(entry.getKey()));
                dto.setToolCount(tools.size());
            } catch (Exception e) {
                dto.setConnected(false);
                dto.setToolCount(0);
                dto.setError(e.getMessage());
            }
            result.add(dto);
        }
        return result;
    }

    public McpTestResultDto testServer(String serverName) {
        McpTestResultDto result = new McpTestResultDto();
        McpServerConfig config = configLoader.loadAllServers().get(serverName);
        if (config == null) {
            result.setOk(false);
            result.setMessage("Server not found: " + serverName);
            return result;
        }
        if (!config.enabled()) {
            result.setOk(false);
            result.setMessage("Server is disabled: " + serverName);
            return result;
        }

        McpClient client = clientFactory.clientFor(config);
        client.shutdownServer(serverName);
        try {
            List<McpToolInfo> tools = client.listTools(serverName, config);
            result.setOk(true);
            result.setMessage("Connected — " + tools.size() + " tool(s)");
            List<McpToolDto> toolDtos = new ArrayList<>();
            for (McpToolInfo tool : tools) {
                McpToolDto toolDto = new McpToolDto();
                toolDto.setServerName(serverName);
                toolDto.setName(tool.name());
                toolDto.setDescription(tool.description());
                toolDtos.add(toolDto);
            }
            result.setTools(toolDtos);
        } catch (Exception e) {
            result.setOk(false);
            result.setMessage(e.getMessage());
        }
        return result;
    }

    private Map<String, Object> parseToolArguments(JsonNode argsRoot) {
        if (argsRoot.isObject() && argsRoot.has("arguments") && argsRoot.size() == 1) {
            JsonNode toolArgs = argsRoot.path("arguments");
            if (toolArgs.isObject()) {
                return objectMapper.convertValue(toolArgs, Map.class);
            }
            return Map.of();
        }
        if (argsRoot.isObject()) {
            return objectMapper.convertValue(argsRoot, Map.class);
        }
        return Map.of();
    }

    private synchronized void refreshIfNeeded() {
        Map<String, McpServerConfig> servers = configLoader.loadServers();
        if (servers.isEmpty()) {
            cachedTools = List.of();
            return;
        }

        List<McpToolDescriptor> tools = new ArrayList<>();
        for (Map.Entry<String, McpServerConfig> entry : servers.entrySet()) {
            try {
                McpClient client = clientFactory.clientFor(entry.getValue());
                List<McpToolInfo> listed = client.listTools(entry.getKey(), entry.getValue());
                for (McpToolInfo info : listed) {
                    String fullName = "mcp__" + entry.getKey() + "__" + info.name();
                    tools.add(new McpToolDescriptor(
                            fullName,
                            entry.getKey(),
                            info.name(),
                            info.description(),
                            info.inputSchema()));
                }
            } catch (Exception e) {
                log.warn("Failed to list MCP tools for server {}", entry.getKey(), e);
            }
        }
        cachedTools = List.copyOf(tools);
    }

    private McpServerDto toDto(String name, McpServerConfig config) {
        McpServerDto dto = new McpServerDto();
        dto.setName(name);
        dto.setTransport(config.transport());
        dto.setCommand(config.command());
        dto.setUrl(config.url());
        dto.setArgs(config.args());
        dto.setEnv(config.env());
        dto.setHeaders(config.headers());
        dto.setCwd(config.cwd());
        dto.setEnabled(config.enabled());
        return dto;
    }
}
