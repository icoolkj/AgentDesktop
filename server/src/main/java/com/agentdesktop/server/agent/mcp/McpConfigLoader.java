package com.agentdesktop.server.agent.mcp;

import com.agentdesktop.server.persistence.McpConfigStore;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Facade for MCP configuration — delegates to {@link McpConfigStore} (MySQL + Redis).
 */
@Component
public class McpConfigLoader {

    private final McpConfigStore configStore;

    public McpConfigLoader(McpConfigStore configStore) {
        this.configStore = configStore;
    }

    public Path getConfigPath() {
        return configStore.getLegacyFilePath();
    }

    public String getStorageDescription() {
        return configStore.storageDescription();
    }

    /** Loads enabled servers only (for agent tool execution). */
    public Map<String, McpServerConfig> loadServers() {
        return loadAllServers().entrySet().stream()
                .filter(entry -> entry.getValue().enabled())
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), Map::putAll);
    }

    /** Loads all configured servers including disabled ones. */
    public Map<String, McpServerConfig> loadAllServers() {
        return configStore.loadAllServers();
    }

    public synchronized void saveServers(Map<String, McpServerConfig> servers) {
        configStore.saveAll(servers);
    }

    public void invalidateCache() {
        configStore.invalidateCache();
    }
}
