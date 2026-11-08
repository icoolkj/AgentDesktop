package com.agentdesktop.server.agent.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StoredLlmSettings {

    private Boolean enabled;
    private String provider;
    private String baseUrl;
    private String apiKey;
    private String model;
    private Double temperature;
    private Integer maxTokens;
    private Integer timeoutSeconds;
    private Boolean streamToolTurns;
    private Boolean streamAnswerTurns;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Boolean getStreamToolTurns() {
        return streamToolTurns;
    }

    public void setStreamToolTurns(Boolean streamToolTurns) {
        this.streamToolTurns = streamToolTurns;
    }

    public Boolean getStreamAnswerTurns() {
        return streamAnswerTurns;
    }

    public void setStreamAnswerTurns(Boolean streamAnswerTurns) {
        this.streamAnswerTurns = streamAnswerTurns;
    }
}
