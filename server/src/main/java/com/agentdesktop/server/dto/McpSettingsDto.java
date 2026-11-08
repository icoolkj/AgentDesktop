package com.agentdesktop.server.dto;

import java.util.ArrayList;
import java.util.List;

public class McpSettingsDto {

    private String configPath;
    private List<McpServerDto> servers = new ArrayList<>();

    public String getConfigPath() {
        return configPath;
    }

    public void setConfigPath(String configPath) {
        this.configPath = configPath;
    }

    public List<McpServerDto> getServers() {
        return servers;
    }

    public void setServers(List<McpServerDto> servers) {
        this.servers = servers;
    }
}
