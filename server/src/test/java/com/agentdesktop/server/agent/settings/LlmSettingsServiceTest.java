package com.agentdesktop.server.agent.settings;

import com.agentdesktop.server.config.LlmProperties;
import com.agentdesktop.server.dto.LlmSettingsDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LlmSettingsServiceTest {

    @TempDir
    Path tempDir;

    private LlmSettingsService service;

    @BeforeEach
    void setUp() {
        LlmProperties defaults = new LlmProperties();
        defaults.setApiKey("");
        defaults.setBaseUrl("https://api.openai.com/v1");
        defaults.setModel("gpt-4o-mini");

        AgentSettingsStore store = new AgentSettingsStore(new ObjectMapper(), tempDir.resolve("settings.json"));
        service = new LlmSettingsService(defaults, store, new ObjectMapper());
        service.loadFromDisk();
    }

    @Test
    void updateSettings_persistsApiKeyAndMasksOnRead() {
        LlmSettingsDto input = new LlmSettingsDto();
        input.setEnabled(true);
        input.setProvider("openai-compatible");
        input.setBaseUrl("http://127.0.0.1:11434/v1");
        input.setApiKey("sk-test-key");
        input.setModel("qwen2.5");
        input.setTemperature(0.5);
        input.setMaxTokens(2048);
        input.setTimeoutSeconds(60);

        LlmSettingsDto saved = service.updateSettings(input);

        assertThat(saved.isConfigured()).isTrue();
        assertThat(saved.isApiKeySet()).isTrue();
        assertThat(saved.getApiKey()).isEqualTo(LlmSettingsService.API_KEY_MASK);
        assertThat(saved.getModel()).isEqualTo("qwen2.5");

        LlmRuntimeConfig runtime = service.getRuntimeConfig();
        assertThat(runtime.apiKey()).isEqualTo("sk-test-key");
        assertThat(runtime.baseUrl()).isEqualTo("http://127.0.0.1:11434/v1");
    }

    @Test
    void updateSettings_keepsExistingApiKeyWhenMaskedPlaceholderSent() {
        LlmSettingsDto first = new LlmSettingsDto();
        first.setEnabled(true);
        first.setProvider("openai-compatible");
        first.setBaseUrl("https://api.openai.com/v1");
        first.setApiKey("sk-secret");
        first.setModel("gpt-4o-mini");
        first.setTemperature(0.7);
        first.setMaxTokens(4096);
        first.setTimeoutSeconds(120);
        service.updateSettings(first);

        LlmSettingsDto second = new LlmSettingsDto();
        second.setEnabled(true);
        second.setProvider("openai-compatible");
        second.setBaseUrl("https://api.openai.com/v1");
        second.setApiKey(LlmSettingsService.API_KEY_MASK);
        second.setModel("gpt-4o");
        second.setTemperature(0.7);
        second.setMaxTokens(4096);
        second.setTimeoutSeconds(120);

        service.updateSettings(second);

        assertThat(service.getRuntimeConfig().apiKey()).isEqualTo("sk-secret");
        assertThat(service.getRuntimeConfig().model()).isEqualTo("gpt-4o");
    }
}
