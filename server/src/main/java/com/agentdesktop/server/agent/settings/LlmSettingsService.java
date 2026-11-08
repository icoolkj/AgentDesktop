package com.agentdesktop.server.agent.settings;

import com.agentdesktop.server.agent.llm.openai.OpenAiCompatibleLlmProvider;
import com.agentdesktop.server.config.LlmProperties;
import com.agentdesktop.server.dto.LlmModelDto;
import com.agentdesktop.server.dto.LlmModelsResponseDto;
import com.agentdesktop.server.dto.LlmSettingsDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class LlmSettingsService {

    private static final Logger log = LoggerFactory.getLogger(LlmSettingsService.class);
    public static final String API_KEY_MASK = "********";

    private final LlmProperties defaults;
    private final AgentSettingsStore settingsStore;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private StoredLlmSettings stored = new StoredLlmSettings();

    public LlmSettingsService(LlmProperties defaults, AgentSettingsStore settingsStore, ObjectMapper objectMapper) {
        this.defaults = defaults;
        this.settingsStore = settingsStore;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @PostConstruct
    void loadFromDisk() {
        stored = settingsStore.load();
    }

    public LlmRuntimeConfig getRuntimeConfig() {
        return new LlmRuntimeConfig(
                pickBoolean(stored.getEnabled(), defaults.isEnabled()),
                pickString(stored.getProvider(), defaults.getProvider()),
                pickString(stored.getBaseUrl(), defaults.getBaseUrl()),
                pickString(stored.getApiKey(), defaults.getApiKey()),
                pickString(stored.getModel(), defaults.getModel()),
                pickDouble(stored.getTemperature(), defaults.getTemperature()),
                pickInt(stored.getMaxTokens(), defaults.getMaxTokens()),
                pickInt(stored.getTimeoutSeconds(), defaults.getTimeoutSeconds()),
                pickBoolean(stored.getStreamToolTurns(), defaults.isStreamToolTurns()),
                pickBoolean(stored.getStreamAnswerTurns(), defaults.isStreamAnswerTurns())
        );
    }

    public LlmSettingsDto getPublicSettings() {
        LlmRuntimeConfig runtime = getRuntimeConfig();
        LlmSettingsDto dto = new LlmSettingsDto();
        dto.setEnabled(runtime.enabled());
        dto.setProvider(runtime.provider());
        dto.setBaseUrl(runtime.baseUrl());
        dto.setModel(runtime.model());
        dto.setTemperature(runtime.temperature());
        dto.setMaxTokens(runtime.maxTokens());
        dto.setTimeoutSeconds(runtime.timeoutSeconds());
        dto.setStreamToolTurns(runtime.streamToolTurns());
        dto.setStreamAnswerTurns(runtime.streamAnswerTurns());
        dto.setConfigured(runtime.isConfigured());
        dto.setApiKeySet(hasApiKey(runtime.apiKey()));
        dto.setApiKey(hasApiKey(runtime.apiKey()) ? API_KEY_MASK : "");
        dto.setSettingsPath(settingsStore.getStorageDescription());
        return dto;
    }

    public LlmSettingsDto updateSettings(LlmSettingsDto input) {
        StoredLlmSettings next = copyStored(stored);

        next.setEnabled(input.isEnabled());
        if (input.getProvider() != null && !input.getProvider().isBlank()) {
            next.setProvider(input.getProvider().trim());
        }
        if (input.getBaseUrl() != null && !input.getBaseUrl().isBlank()) {
            next.setBaseUrl(input.getBaseUrl().trim());
        }
        if (input.getModel() != null && !input.getModel().isBlank()) {
            next.setModel(input.getModel().trim());
        }
        next.setTemperature(input.getTemperature());
        next.setMaxTokens(input.getMaxTokens());
        next.setTimeoutSeconds(input.getTimeoutSeconds());
        next.setStreamToolTurns(input.isStreamToolTurns());
        next.setStreamAnswerTurns(input.isStreamAnswerTurns());

        if (input.getApiKey() != null && !input.getApiKey().isBlank() && !API_KEY_MASK.equals(input.getApiKey())) {
            next.setApiKey(input.getApiKey().trim());
        }

        stored = next;
        settingsStore.save(stored);
        return getPublicSettings();
    }

    public LlmModelsResponseDto listAvailableModels() {
        LlmRuntimeConfig config = getRuntimeConfig();
        LlmModelsResponseDto response = new LlmModelsResponseDto();
        response.setDefaultModel(config.model());

        if (!config.isConfigured()) {
            response.setError("LLM 未配置 — 请先在 LLM 配置中填写 API Key");
            return response;
        }

        try {
            String base = OpenAiCompatibleLlmProvider.normalizeBaseUrl(config.baseUrl());
            URI uri = URI.create(base + "/models");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(Math.min(config.timeoutSeconds(), 30)))
                    .header("Authorization", "Bearer " + config.apiKey().trim())
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                response.setError("GET /models 失败 (" + httpResponse.statusCode() + ")");
                return response;
            }

            JsonNode root = objectMapper.readTree(httpResponse.body());
            JsonNode data = root.path("data");
            List<LlmModelDto> models = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode node : data) {
                    String id = node.path("id").asText(null);
                    if (id == null || id.isBlank()) {
                        continue;
                    }
                    String ownedBy = node.path("owned_by").asText(null);
                    models.add(new LlmModelDto(id, ownedBy));
                }
            }
            models.sort(Comparator.comparing(LlmModelDto::getId, String.CASE_INSENSITIVE_ORDER));
            response.setModels(models);
            return response;
        } catch (Exception e) {
            log.warn("Failed to list models from {}", config.baseUrl(), e);
            response.setError(e.getMessage() == null ? "无法获取模型列表" : e.getMessage());
            return response;
        }
    }

    private static StoredLlmSettings copyStored(StoredLlmSettings source) {
        StoredLlmSettings copy = new StoredLlmSettings();
        copy.setEnabled(source.getEnabled());
        copy.setProvider(source.getProvider());
        copy.setBaseUrl(source.getBaseUrl());
        copy.setApiKey(source.getApiKey());
        copy.setModel(source.getModel());
        copy.setTemperature(source.getTemperature());
        copy.setMaxTokens(source.getMaxTokens());
        copy.setTimeoutSeconds(source.getTimeoutSeconds());
        copy.setStreamToolTurns(source.getStreamToolTurns());
        copy.setStreamAnswerTurns(source.getStreamAnswerTurns());
        return copy;
    }

    private static boolean hasApiKey(String apiKey) {
        return apiKey != null && !apiKey.isBlank();
    }

    private static String pickString(String storedValue, String defaultValue) {
        return storedValue != null && !storedValue.isBlank() ? storedValue : defaultValue;
    }

    private static boolean pickBoolean(Boolean storedValue, boolean defaultValue) {
        return storedValue != null ? storedValue : defaultValue;
    }

    private static double pickDouble(Double storedValue, double defaultValue) {
        return storedValue != null ? storedValue : defaultValue;
    }

    private static int pickInt(Integer storedValue, int defaultValue) {
        return storedValue != null ? storedValue : defaultValue;
    }
}
