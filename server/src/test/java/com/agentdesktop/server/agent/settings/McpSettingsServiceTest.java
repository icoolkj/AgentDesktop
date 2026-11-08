package com.agentdesktop.server.agent.settings;

import com.agentdesktop.server.agent.mcp.McpConfigLoader;
import com.agentdesktop.server.agent.mcp.McpServerConfig;
import com.agentdesktop.server.agent.mcp.McpToolBridge;
import com.agentdesktop.server.dto.McpServerDto;
import com.agentdesktop.server.dto.McpSettingsDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpSettingsServiceTest {

    @TempDir
    Path tempDir;

    private McpConfigLoader configLoader;
    private McpToolBridge mcpToolBridge;
    private McpSettingsService service;

    @BeforeEach
    void setUp() throws Exception {
        Path configPath = tempDir.resolve("mcp.json");
        Files.writeString(configPath, """
                {
                  "servers": {
                    "demo": {
                      "command": "echo",
                      "args": ["hello"],
                      "enabled": true
                    }
                  }
                }
                """);

        configLoader = mock(McpConfigLoader.class);
        when(configLoader.getConfigPath()).thenReturn(configPath);
        when(configLoader.getStorageDescription()).thenReturn("MySQL: agent_desktop.mcp_server_config");
        when(configLoader.loadAllServers()).thenReturn(Map.of(
                "demo", new McpServerConfig("echo", List.of("hello"), Map.of(), null, true)));
        when(configLoader.loadServers()).thenReturn(Map.of(
                "demo", new McpServerConfig("echo", List.of("hello"), Map.of(), null, true)));

        mcpToolBridge = mock(McpToolBridge.class);
        doNothing().when(mcpToolBridge).invalidateCache();
        doNothing().when(configLoader).saveServers(any());
        doNothing().when(configLoader).invalidateCache();
        when(mcpToolBridge.serverStatuses()).thenReturn(List.of(createServerDto("demo", "echo")));

        service = new McpSettingsService(configLoader, mcpToolBridge);
    }

    @Test
    void getSettingsReturnsConfigPath() {
        McpSettingsDto settings = service.getSettings();
        assertTrue(settings.getConfigPath().contains("mcp_server_config"));
        assertEquals(1, settings.getServers().size());
    }

    @Test
    void updateSettingsSavesAndReloads() throws Exception {
        McpSettingsDto body = new McpSettingsDto();
        McpServerDto server = new McpServerDto();
        server.setName("new-server");
        server.setCommand("node");
        server.setArgs(List.of("server.js"));
        server.setEnabled(true);
        body.setServers(List.of(server));

        when(mcpToolBridge.serverStatuses()).thenReturn(List.of(createServerDto("new-server", "node")));

        McpSettingsDto updated = service.updateSettings(body);
        assertEquals(1, updated.getServers().size());
    }

    private static McpServerDto createServerDto(String name, String command) {
        McpServerDto dto = new McpServerDto();
        dto.setName(name);
        dto.setCommand(command);
        dto.setEnabled(true);
        return dto;
    }
}
