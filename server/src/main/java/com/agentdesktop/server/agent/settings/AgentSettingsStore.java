package com.agentdesktop.server.agent.settings;

import com.agentdesktop.server.domain.LlmSettingsRow;
import com.agentdesktop.server.mapper.LlmSettingsMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class AgentSettingsStore {

    private static final Logger log = LoggerFactory.getLogger(AgentSettingsStore.class);
    private static final int SETTINGS_ROW_ID = 1;
    static final String MYSQL_STORAGE_LABEL = "MySQL: agent_desktop.llm_settings";

    private final ObjectMapper objectMapper;
    private final LlmSettingsMapper llmSettingsMapper;
    private final Path legacyFilePath;

    @Autowired
    public AgentSettingsStore(ObjectMapper objectMapper, LlmSettingsMapper llmSettingsMapper) {
        this.objectMapper = objectMapper;
        this.llmSettingsMapper = llmSettingsMapper;
        this.legacyFilePath = Path.of(System.getProperty("user.home"), ".agent-desktop", "settings.json");
    }

    /** Package-private for unit tests without MySQL. */
    AgentSettingsStore(ObjectMapper objectMapper, Path legacyFilePath) {
        this.objectMapper = objectMapper;
        this.llmSettingsMapper = null;
        this.legacyFilePath = legacyFilePath;
    }

    public StoredLlmSettings load() {
        StoredLlmSettings fromMysql = loadFromMysql();
        if (fromMysql != null) {
            return fromMysql;
        }

        StoredLlmSettings fromFile = loadFromLegacyFile();
        if (fromFile != null && llmSettingsMapper != null && hasAnyValue(fromFile)) {
            log.info("Migrating LLM settings from {} to MySQL", legacyFilePath);
            saveToMysql(fromFile);
            return fromFile;
        }
        return fromFile != null ? fromFile : new StoredLlmSettings();
    }

    public void save(StoredLlmSettings settings) {
        if (llmSettingsMapper != null) {
            saveToMysql(settings);
            return;
        }
        saveToLegacyFile(settings);
    }

    public String getStorageDescription() {
        return llmSettingsMapper != null ? MYSQL_STORAGE_LABEL : legacyFilePath.toString();
    }

    private StoredLlmSettings loadFromMysql() {
        if (llmSettingsMapper == null) {
            return null;
        }
        try {
            LlmSettingsRow row = llmSettingsMapper.findById(SETTINGS_ROW_ID);
            if (row == null) {
                return null;
            }
            return toStored(row);
        } catch (Exception e) {
            log.warn("Failed to load LLM settings from MySQL: {}", e.getMessage());
            return null;
        }
    }

    private void saveToMysql(StoredLlmSettings settings) {
        try {
            llmSettingsMapper.upsert(toRow(settings));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save LLM settings to MySQL", e);
        }
    }

    private StoredLlmSettings loadFromLegacyFile() {
        if (!Files.exists(legacyFilePath)) {
            return null;
        }
        try {
            AgentSettingsFile file = objectMapper.readValue(Files.readString(legacyFilePath), AgentSettingsFile.class);
            return file.getLlm() != null ? file.getLlm() : new StoredLlmSettings();
        } catch (IOException e) {
            log.warn("Failed to read legacy settings from {}: {}", legacyFilePath, e.getMessage());
            return null;
        }
    }

    private void saveToLegacyFile(StoredLlmSettings settings) {
        try {
            Files.createDirectories(legacyFilePath.getParent());
            AgentSettingsFile file = new AgentSettingsFile();
            file.setLlm(settings);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(legacyFilePath.toFile(), file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save settings to " + legacyFilePath, e);
        }
    }

    private static boolean hasAnyValue(StoredLlmSettings settings) {
        return settings.getApiKey() != null
                || settings.getBaseUrl() != null
                || settings.getModel() != null
                || settings.getProvider() != null;
    }

    private static StoredLlmSettings toStored(LlmSettingsRow row) {
        StoredLlmSettings stored = new StoredLlmSettings();
        stored.setEnabled(row.isEnabled());
        stored.setProvider(row.getProvider());
        stored.setBaseUrl(row.getBaseUrl());
        stored.setApiKey(row.getApiKey());
        stored.setModel(row.getModel());
        stored.setTemperature(row.getTemperature());
        stored.setMaxTokens(row.getMaxTokens());
        stored.setTimeoutSeconds(row.getTimeoutSeconds());
        stored.setStreamToolTurns(row.isStreamToolTurns());
        stored.setStreamAnswerTurns(row.isStreamAnswerTurns());
        return stored;
    }

    private static LlmSettingsRow toRow(StoredLlmSettings settings) {
        LlmSettingsRow row = new LlmSettingsRow();
        row.setId(SETTINGS_ROW_ID);
        row.setEnabled(settings.getEnabled() == null || settings.getEnabled());
        row.setProvider(defaultString(settings.getProvider(), "openai-compatible"));
        row.setBaseUrl(defaultString(settings.getBaseUrl(), "https://api.openai.com/v1"));
        row.setApiKey(settings.getApiKey());
        row.setModel(defaultString(settings.getModel(), "gpt-4o-mini"));
        row.setTemperature(settings.getTemperature() != null ? settings.getTemperature() : 0.7);
        row.setMaxTokens(settings.getMaxTokens() != null ? settings.getMaxTokens() : 4096);
        row.setTimeoutSeconds(settings.getTimeoutSeconds() != null ? settings.getTimeoutSeconds() : 120);
        row.setStreamToolTurns(settings.getStreamToolTurns() != null && settings.getStreamToolTurns());
        row.setStreamAnswerTurns(settings.getStreamAnswerTurns() != null && settings.getStreamAnswerTurns());
        return row;
    }

    private static String defaultString(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
