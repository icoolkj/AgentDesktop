package com.agentdesktop.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.llm")
public class LlmProperties {

    /** openai-compatible | disabled */
    private String provider = "openai-compatible";

    private boolean enabled = true;

    /** API base URL — default CLIProxyAPI-Java local proxy (native tool_calls). */
    private String baseUrl = "http://127.0.0.1:8317/v1";

    private String apiKey = "";

    private String model = "gpt-4o-mini";

    /** Tool planning turns ({@code tools=auto}). CPA ToolTest: 完整两轮建议非流式. */
    private boolean streamToolTurns = false;

    /** Final text turns ({@code tool_choice=none}). Cursor-like: stream Markdown to user. */
    private boolean streamAnswerTurns = true;

    private double temperature = 0.7;

    private int maxTokens = 4096;

    private int timeoutSeconds = 120;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
}
