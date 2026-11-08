package com.agentdesktop.server.persistence;

import com.agentdesktop.server.agent.mcp.McpServerConfig;
import com.agentdesktop.server.domain.McpServerConfigRow;
import com.agentdesktop.server.mapper.McpServerConfigMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP server configs — MySQL {@code mcp_server_config} + Redis cache, legacy {@code mcp.json} migration.
 */
@Component
public class McpConfigStore {

    private static final Logger log = LoggerFactory.getLogger(McpConfigStore.class);
    public static final String MYSQL_LABEL = "MySQL: agent_desktop.mcp_server_config";

    private final ObjectMapper objectMapper;
    private final McpServerConfigMapper mcpServerConfigMapper;
    private final StringRedisTemplate redisTemplate;
    private final Duration configTtl;
    private final Path legacyFilePath;

    public McpConfigStore(
            ObjectMapper objectMapper,
            McpServerConfigMapper mcpServerConfigMapper,
            StringRedisTemplate redisTemplate,
            @Value("${agent.cache.config-ttl-minutes:10}") long configTtlMinutes) {
        this.objectMapper = objectMapper;
        this.mcpServerConfigMapper = mcpServerConfigMapper;
        this.redisTemplate = redisTemplate;
        this.configTtl = Duration.ofMinutes(configTtlMinutes);
        this.legacyFilePath = Path.of(System.getProperty("user.home"), ".agent-desktop", "mcp.json");
    }

    public Path getLegacyFilePath() {
        return legacyFilePath;
    }

    public String storageDescription() {
        return mcpServerConfigMapper != null ? MYSQL_LABEL : legacyFilePath.toString();
    }

    public Map<String, McpServerConfig> loadAllServers() {
        Map<String, McpServerConfig> cached = loadFromRedis();
        if (!cached.isEmpty()) {
            return cached;
        }

        Map<String, McpServerConfig> fromMysql = loadFromMysql();
        if (!fromMysql.isEmpty()) {
            cacheToRedis(fromMysql);
            return fromMysql;
        }

        Map<String, McpServerConfig> fromFile = loadFromLegacyFile();
        if (!fromFile.isEmpty() && mcpServerConfigMapper != null) {
            log.info("Migrating MCP servers from {} to MySQL", legacyFilePath);
            saveAllToMysql(fromFile);
            cacheToRedis(fromFile);
        }
        return fromFile;
    }

    public synchronized void saveAll(Map<String, McpServerConfig> servers) {
        invalidateRedis();
        if (mcpServerConfigMapper != null) {
            saveAllToMysql(servers);
            cacheToRedis(servers);
            return;
        }
        saveToLegacyFile(servers);
    }

    public void invalidateCache() {
        invalidateRedis();
    }

    private Map<String, McpServerConfig> loadFromRedis() {
        try {
            String json = redisTemplate.opsForValue().get(AgentConfigKeys.REDIS_MCP_ALL);
            if (json == null || json.isBlank()) {
                return Map.of();
            }
            return parseServersRoot(objectMapper.readTree(json).path("servers"));
        } catch (Exception e) {
            log.debug("Redis MCP cache miss: {}", e.getMessage());
            return Map.of();
        }
    }

    private void cacheToRedis(Map<String, McpServerConfig> servers) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode serversNode = root.putObject("servers");
            for (Map.Entry<String, McpServerConfig> entry : servers.entrySet()) {
                serversNode.set(entry.getKey(), toJsonNode(entry.getValue()));
            }
            redisTemplate.opsForValue().set(
                    AgentConfigKeys.REDIS_MCP_ALL,
                    objectMapper.writeValueAsString(root),
                    configTtl);
        } catch (Exception e) {
            log.debug("Failed to cache MCP configs in Redis: {}", e.getMessage());
        }
    }

    private void invalidateRedis() {
        try {
            redisTemplate.delete(AgentConfigKeys.REDIS_MCP_ALL);
        } catch (Exception e) {
            log.debug("Failed to invalidate MCP Redis cache: {}", e.getMessage());
        }
    }

    private Map<String, McpServerConfig> loadFromMysql() {
        if (mcpServerConfigMapper == null) {
            return Map.of();
        }
        try {
            List<McpServerConfigRow> rows = mcpServerConfigMapper.findAll();
            if (rows == null || rows.isEmpty()) {
                return Map.of();
            }
            Map<String, McpServerConfig> result = new LinkedHashMap<>();
            for (McpServerConfigRow row : rows) {
                McpServerConfig config = parseServerNode(objectMapper.readTree(row.getConfigJson()));
                if (config == null) {
                    continue;
                }
                boolean enabled = row.isEnabled();
                result.put(row.getName(), new McpServerConfig(
                        config.transport(),
                        config.command(),
                        config.args(),
                        config.env(),
                        config.cwd(),
                        config.url(),
                        config.headers(),
                        enabled));
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to load MCP configs from MySQL: {}", e.getMessage());
            return Map.of();
        }
    }

    private void saveAllToMysql(Map<String, McpServerConfig> servers) {
        try {
            mcpServerConfigMapper.deleteAll();
            for (Map.Entry<String, McpServerConfig> entry : servers.entrySet()) {
                McpServerConfig config = entry.getValue();
                if (!isValidConfig(config)) {
                    continue;
                }
                McpServerConfigRow row = new McpServerConfigRow();
                row.setName(entry.getKey());
                row.setConfigJson(objectMapper.writeValueAsString(toJsonNode(config)));
                row.setEnabled(config.enabled());
                mcpServerConfigMapper.upsert(row);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save MCP configs to MySQL", e);
        }
    }

    private Map<String, McpServerConfig> loadFromLegacyFile() {
        try {
            if (!Files.isRegularFile(legacyFilePath)) {
                return Map.of();
            }
            JsonNode root = objectMapper.readTree(Files.readString(legacyFilePath));
            return parseServersRoot(root.path("servers"));
        } catch (Exception e) {
            log.warn("Failed to read legacy MCP config {}: {}", legacyFilePath, e.getMessage());
            return Map.of();
        }
    }

    private void saveToLegacyFile(Map<String, McpServerConfig> servers) {
        try {
            Files.createDirectories(legacyFilePath.getParent());
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode serversNode = root.putObject("servers");
            for (Map.Entry<String, McpServerConfig> entry : servers.entrySet()) {
                McpServerConfig config = entry.getValue();
                if (!isValidConfig(config)) {
                    continue;
                }
                serversNode.set(entry.getKey(), toJsonNode(config));
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(legacyFilePath.toFile(), root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save MCP config to " + legacyFilePath, e);
        }
    }

    private Map<String, McpServerConfig> parseServersRoot(JsonNode servers) {
        if (!servers.isObject()) {
            return Map.of();
        }
        Map<String, McpServerConfig> result = new LinkedHashMap<>();
        servers.fields().forEachRemaining(entry -> {
            McpServerConfig config = parseServerNode(entry.getValue());
            if (config != null) {
                result.put(entry.getKey(), config);
            }
        });
        return result;
    }

    private ObjectNode toJsonNode(McpServerConfig config) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("transport", config.transport());
        if (config.command() != null && !config.command().isBlank()) {
            node.put("command", config.command());
        }
        if (config.url() != null && !config.url().isBlank()) {
            node.put("url", config.url());
        }
        node.set("args", objectMapper.valueToTree(config.args()));
        node.set("env", objectMapper.valueToTree(config.env()));
        node.set("headers", objectMapper.valueToTree(config.headers()));
        if (config.cwd() != null && !config.cwd().isBlank()) {
            node.put("cwd", config.cwd());
        }
        node.put("enabled", config.enabled());
        return node;
    }

    static boolean isValidConfig(McpServerConfig config) {
        if (config.isRemoteTransport()) {
            return config.url() != null && !config.url().isBlank();
        }
        return config.command() != null && !config.command().isBlank();
    }

    McpServerConfig parseServerNode(JsonNode node) {
        String transport = node.path("transport").asText("");
        String command = node.path("command").asText("");
        String url = node.path("url").asText("");
        if (transport.isBlank()) {
            transport = !url.isBlank() ? "http" : "stdio";
        }
        if ("stdio".equalsIgnoreCase(transport) && command.isBlank()) {
            return null;
        }
        if (("http".equalsIgnoreCase(transport) || "sse".equalsIgnoreCase(transport)) && url.isBlank()) {
            return null;
        }
        List<String> args = node.has("args")
                ? objectMapper.convertValue(node.get("args"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class))
                : List.of();
        Map<String, String> env = node.has("env")
                ? objectMapper.convertValue(node.get("env"),
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class))
                : Map.of();
        Map<String, String> headers = node.has("headers")
                ? objectMapper.convertValue(node.get("headers"),
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class))
                : Map.of();
        String cwd = node.path("cwd").asText(null);
        boolean enabled = !node.has("enabled") || node.path("enabled").asBoolean(true);
        return new McpServerConfig(transport, command, args, env, cwd, url, headers, enabled);
    }
}
