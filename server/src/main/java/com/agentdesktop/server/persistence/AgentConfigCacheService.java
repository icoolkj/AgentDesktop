package com.agentdesktop.server.persistence;

import com.agentdesktop.server.domain.AgentConfigRow;
import com.agentdesktop.server.mapper.AgentConfigMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Generic JSON config store — MySQL primary, Redis hot cache, graceful degradation.
 */
@Component
public class AgentConfigCacheService {

    private static final Logger log = LoggerFactory.getLogger(AgentConfigCacheService.class);
    public static final String MYSQL_LABEL = "MySQL: agent_desktop.agent_config";

    private final ObjectMapper objectMapper;
    private final AgentConfigMapper agentConfigMapper;
    private final StringRedisTemplate redisTemplate;
    private final Duration configTtl;

    public AgentConfigCacheService(
            ObjectMapper objectMapper,
            AgentConfigMapper agentConfigMapper,
            StringRedisTemplate redisTemplate,
            @Value("${agent.cache.config-ttl-minutes:10}") long configTtlMinutes) {
        this.objectMapper = objectMapper;
        this.agentConfigMapper = agentConfigMapper;
        this.redisTemplate = redisTemplate;
        this.configTtl = Duration.ofMinutes(configTtlMinutes);
    }

    public <T> Optional<T> load(String configKey, Class<T> type) {
        Optional<T> cached = loadFromRedis(configKey, type);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<T> fromMysql = loadFromMysql(configKey, type);
        fromMysql.ifPresent(value -> cacheToRedis(configKey, value));
        return fromMysql;
    }

    /** Load raw JSON — use when record fields evolve and need default merge on read. */
    public Optional<JsonNode> loadJson(String configKey) {
        Optional<JsonNode> cached = loadJsonFromRedis(configKey);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<JsonNode> fromMysql = loadJsonFromMysql(configKey);
        fromMysql.ifPresent(node -> cacheJsonToRedis(configKey, node));
        return fromMysql;
    }

    public <T> void save(String configKey, T value) {
        invalidateRedis(configKey);
        if (agentConfigMapper == null) {
            return;
        }
        try {
            AgentConfigRow row = new AgentConfigRow();
            row.setConfigKey(configKey);
            row.setConfigJson(objectMapper.writeValueAsString(value));
            agentConfigMapper.upsert(row);
            cacheToRedis(configKey, value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save config " + configKey + " to MySQL", e);
        }
    }

    public void invalidate(String configKey) {
        invalidateRedis(configKey);
    }

    public String storageDescription() {
        return agentConfigMapper != null ? MYSQL_LABEL : "in-memory only";
    }

    private <T> Optional<T> loadFromRedis(String configKey, Class<T> type) {
        return loadJsonFromRedis(configKey).flatMap(node -> parseJson(node, type));
    }

    private Optional<JsonNode> loadJsonFromRedis(String configKey) {
        try {
            String json = redisTemplate.opsForValue().get(AgentConfigKeys.redisKey(configKey));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readTree(json));
        } catch (Exception e) {
            log.debug("Redis config miss for {}: {}", configKey, e.getMessage());
            return Optional.empty();
        }
    }

    private <T> Optional<T> parseJson(JsonNode node, Class<T> type) {
        try {
            return Optional.of(objectMapper.treeToValue(node, type));
        } catch (Exception e) {
            log.debug("Failed to parse config as {}: {}", type.getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }

    private <T> Optional<T> loadFromMysql(String configKey, Class<T> type) {
        return loadJsonFromMysql(configKey).flatMap(node -> parseJson(node, type));
    }

    private Optional<JsonNode> loadJsonFromMysql(String configKey) {
        if (agentConfigMapper == null) {
            return Optional.empty();
        }
        try {
            AgentConfigRow row = agentConfigMapper.findByKey(configKey);
            if (row == null || row.getConfigJson() == null || row.getConfigJson().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readTree(row.getConfigJson()));
        } catch (Exception e) {
            log.warn("Failed to load config {} from MySQL: {}", configKey, e.getMessage());
            return Optional.empty();
        }
    }

    private void cacheJsonToRedis(String configKey, JsonNode node) {
        try {
            redisTemplate.opsForValue().set(
                    AgentConfigKeys.redisKey(configKey),
                    objectMapper.writeValueAsString(node),
                    configTtl);
        } catch (Exception e) {
            log.debug("Failed to cache config {} in Redis: {}", configKey, e.getMessage());
        }
    }

    private void cacheToRedis(String configKey, Object value) {
        try {
            redisTemplate.opsForValue().set(
                    AgentConfigKeys.redisKey(configKey),
                    objectMapper.writeValueAsString(value),
                    configTtl);
        } catch (Exception e) {
            log.debug("Failed to cache config {} in Redis: {}", configKey, e.getMessage());
        }
    }

    private void invalidateRedis(String configKey) {
        try {
            redisTemplate.delete(AgentConfigKeys.redisKey(configKey));
        } catch (Exception e) {
            log.debug("Failed to invalidate Redis key for {}: {}", configKey, e.getMessage());
        }
    }
}
