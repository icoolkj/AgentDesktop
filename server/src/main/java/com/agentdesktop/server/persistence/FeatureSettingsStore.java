package com.agentdesktop.server.persistence;

import com.agentdesktop.server.agent.extension.AgentFeatureSettings;
import com.agentdesktop.server.agent.extension.ExtensionPaths;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;

@Component
public class FeatureSettingsStore {

    private static final Logger log = LoggerFactory.getLogger(FeatureSettingsStore.class);

    private final ObjectMapper objectMapper;
    private final AgentConfigCacheService configCache;

    public FeatureSettingsStore(ObjectMapper objectMapper, AgentConfigCacheService configCache) {
        this.objectMapper = objectMapper;
        this.configCache = configCache;
    }

    public AgentFeatureSettings load() {
        return configCache.loadJson(AgentConfigKeys.FEATURES)
                .map(this::parseFeatures)
                .orElseGet(this::loadLegacyOrDefault);
    }

    public void save(AgentFeatureSettings settings) {
        configCache.save(AgentConfigKeys.FEATURES, settings);
    }

    public String storageDescription() {
        return configCache.storageDescription();
    }

    private AgentFeatureSettings parseFeatures(JsonNode root) {
        AgentFeatureSettings defaults = AgentFeatureSettings.defaults();
        AgentFeatureSettings parsed = new AgentFeatureSettings(
                root.path("subagentsEnabled").asBoolean(defaults.subagentsEnabled()),
                root.path("maxSubagentDepth").asInt(defaults.maxSubagentDepth()),
                root.path("maxSubagentTurns").asInt(defaults.maxSubagentTurns()),
                root.path("webFetchEnabled").asBoolean(defaults.webFetchEnabled()),
                root.path("webFetchMaxBytes").asInt(defaults.webFetchMaxBytes()),
                root.path("webSearchEnabled").asBoolean(defaults.webSearchEnabled()),
                root.path("webSearchProvider").asText(defaults.webSearchProvider()),
                root.path("bashSandboxEnabled").asBoolean(defaults.bashSandboxEnabled()),
                root.path("bashSandboxMode").asText(defaults.bashSandboxMode()));
        if (!root.has("bashSandboxEnabled") || !root.has("bashSandboxMode")) {
            log.info("Upgrading agent_config.features with new bash sandbox fields");
            configCache.save(AgentConfigKeys.FEATURES, parsed);
        }
        return parsed;
    }

    private AgentFeatureSettings loadLegacyOrDefault() {
        AgentFeatureSettings fromFile = loadFromLegacyFile();
        if (fromFile != null) {
            log.info("Migrating feature settings from {} to MySQL", ExtensionPaths.featureSettingsFile());
            configCache.save(AgentConfigKeys.FEATURES, fromFile);
            return fromFile;
        }
        return AgentFeatureSettings.defaults();
    }

    private AgentFeatureSettings loadFromLegacyFile() {
        try {
            var path = ExtensionPaths.featureSettingsFile();
            if (!Files.isRegularFile(path)) {
                return null;
            }
            JsonNode root = objectMapper.readTree(Files.readString(path));
            return parseFeatures(root);
        } catch (Exception e) {
            log.warn("Failed to read legacy features.json: {}", e.getMessage());
            return null;
        }
    }
}
