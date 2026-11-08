package com.agentdesktop.server.agent.settings;

import com.agentdesktop.server.agent.extension.AgentFeatureSettings;
import com.agentdesktop.server.dto.FeatureSettingsDto;
import com.agentdesktop.server.persistence.FeatureSettingsStore;
import org.springframework.stereotype.Service;

@Service
public class ExtensionSettingsService {

    private final FeatureSettingsStore featureSettingsStore;

    public ExtensionSettingsService(FeatureSettingsStore featureSettingsStore) {
        this.featureSettingsStore = featureSettingsStore;
    }

    public FeatureSettingsDto getFeatures() {
        return toDto(featureSettingsStore.load());
    }

    public FeatureSettingsDto updateFeatures(FeatureSettingsDto body) {
        AgentFeatureSettings settings = new AgentFeatureSettings(
                body.isSubagentsEnabled(),
                body.getMaxSubagentDepth(),
                body.getMaxSubagentTurns(),
                body.isWebFetchEnabled(),
                body.getWebFetchMaxBytes(),
                body.isWebSearchEnabled(),
                body.getWebSearchProvider(),
                body.isBashSandboxEnabled(),
                body.getBashSandboxMode());
        featureSettingsStore.save(settings);
        return getFeatures();
    }

    private FeatureSettingsDto toDto(AgentFeatureSettings settings) {
        FeatureSettingsDto dto = new FeatureSettingsDto();
        dto.setSubagentsEnabled(settings.subagentsEnabled());
        dto.setMaxSubagentDepth(settings.maxSubagentDepth());
        dto.setMaxSubagentTurns(settings.maxSubagentTurns());
        dto.setWebFetchEnabled(settings.webFetchEnabled());
        dto.setWebFetchMaxBytes(settings.webFetchMaxBytes());
        dto.setWebSearchEnabled(settings.webSearchEnabled());
        dto.setWebSearchProvider(settings.webSearchProvider());
        dto.setBashSandboxEnabled(settings.bashSandboxEnabled());
        dto.setBashSandboxMode(settings.bashSandboxMode());
        dto.setStorage(featureSettingsStore.storageDescription() + " / Redis cache");
        return dto;
    }
}
