package com.agentdesktop.server.agent.extension;

import com.agentdesktop.server.persistence.FeatureSettingsStore;
import org.springframework.stereotype.Component;

@Component
public class FeatureSettingsLoader {

    private final FeatureSettingsStore featureSettingsStore;

    public FeatureSettingsLoader(FeatureSettingsStore featureSettingsStore) {
        this.featureSettingsStore = featureSettingsStore;
    }

    public AgentFeatureSettings load() {
        return featureSettingsStore.load();
    }
}
