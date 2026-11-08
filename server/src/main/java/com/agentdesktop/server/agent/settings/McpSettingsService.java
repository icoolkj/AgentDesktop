package com.agentdesktop.server.agent.settings;

import com.agentdesktop.server.agent.mcp.McpConfigLoader;
import com.agentdesktop.server.agent.mcp.McpServerConfig;
import com.agentdesktop.server.agent.mcp.McpToolBridge;
import com.agentdesktop.server.dto.McpServerDto;
import com.agentdesktop.server.dto.McpSettingsDto;
import com.agentdesktop.server.dto.McpTestResultDto;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class McpSettingsService {

    private final McpConfigLoader configLoader;
    private final McpToolBridge mcpToolBridge;

    public McpSettingsService(McpConfigLoader configLoader, McpToolBridge mcpToolBridge) {
        this.configLoader = configLoader;
        this.mcpToolBridge = mcpToolBridge;
    }

    public McpSettingsDto getSettings() {
        McpSettingsDto dto = new McpSettingsDto();
        dto.setConfigPath(configLoader.getStorageDescription());
        dto.setServers(mcpToolBridge.serverStatuses());
        return dto;
    }

    public McpSettingsDto updateSettings(McpSettingsDto body) throws Exception {
        Map<String, McpServerConfig> servers = new LinkedHashMap<>();
        List<McpServerDto> incoming = body.getServers() == null ? List.of() : body.getServers();
        for (McpServerDto server : incoming) {
            if (server.getName() == null || server.getName().isBlank()) {
                continue;
            }
            String transport = server.getTransport() == null ? "stdio" : server.getTransport().trim();
            boolean remote = "http".equalsIgnoreCase(transport) || "sse".equalsIgnoreCase(transport);
            if (remote) {
                if (server.getUrl() == null || server.getUrl().isBlank()) {
                    continue;
                }
            } else if (server.getCommand() == null || server.getCommand().isBlank()) {
                continue;
            }
            servers.put(server.getName().trim(), new McpServerConfig(
                    transport,
                    server.getCommand() == null ? "" : server.getCommand().trim(),
                    server.getArgs() == null ? List.of() : server.getArgs(),
                    server.getEnv() == null ? Map.of() : server.getEnv(),
                    server.getCwd(),
                    server.getUrl(),
                    server.getHeaders() == null ? Map.of() : server.getHeaders(),
                    server.isEnabled()));
        }
        configLoader.saveServers(servers);
        configLoader.invalidateCache();
        mcpToolBridge.invalidateCache();
        return getSettings();
    }

    public McpTestResultDto testServer(String serverName) {
        return mcpToolBridge.testServer(serverName);
    }

    public McpSettingsDto reload() {
        configLoader.invalidateCache();
        mcpToolBridge.invalidateCache();
        return getSettings();
    }
}
