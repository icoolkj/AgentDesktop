package com.agentdesktop.server.dto;

public class LlmSettingsDto {

    private boolean enabled;
    private String provider;
    private String baseUrl;
    /** Masked on GET; send new value on PUT. Leave blank to keep existing. */
    private String apiKey;
    private String model;
    private double temperature;
    private int maxTokens;
    private int timeoutSeconds;
    private boolean streamToolTurns;
    private boolean streamAnswerTurns;
    private boolean configured;
    private boolean apiKeySet;
    private String settingsPath;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
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

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isStreamToolTurns() {
        return streamToolTurns;
    }

    public void setStreamToolTurns(boolean streamToolTurns) {
        this.streamToolTurns = streamToolTurns;
    }

    public boolean isStreamAnswerTurns() {
        return streamAnswerTurns;
    }

    public void setStreamAnswerTurns(boolean streamAnswerTurns) {
        this.streamAnswerTurns = streamAnswerTurns;
    }

    public boolean isConfigured() {
        return configured;
    }

    public void setConfigured(boolean configured) {
        this.configured = configured;
    }

    public boolean isApiKeySet() {
        return apiKeySet;
    }

    public void setApiKeySet(boolean apiKeySet) {
        this.apiKeySet = apiKeySet;
    }

    public String getSettingsPath() {
        return settingsPath;
    }

    public void setSettingsPath(String settingsPath) {
        this.settingsPath = settingsPath;
    }
}
