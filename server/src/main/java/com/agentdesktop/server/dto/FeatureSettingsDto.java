package com.agentdesktop.server.dto;

public class FeatureSettingsDto {

    private boolean subagentsEnabled = true;
    private int maxSubagentDepth = 2;
    private int maxSubagentTurns = 8;
    private boolean webFetchEnabled = true;
    private int webFetchMaxBytes = 524288;
    private boolean webSearchEnabled = true;
    private String webSearchProvider = "duckduckgo";
    private boolean bashSandboxEnabled = true;
    private String bashSandboxMode = "strict";
    private String storage;

    public boolean isSubagentsEnabled() {
        return subagentsEnabled;
    }

    public void setSubagentsEnabled(boolean subagentsEnabled) {
        this.subagentsEnabled = subagentsEnabled;
    }

    public int getMaxSubagentDepth() {
        return maxSubagentDepth;
    }

    public void setMaxSubagentDepth(int maxSubagentDepth) {
        this.maxSubagentDepth = maxSubagentDepth;
    }

    public int getMaxSubagentTurns() {
        return maxSubagentTurns;
    }

    public void setMaxSubagentTurns(int maxSubagentTurns) {
        this.maxSubagentTurns = maxSubagentTurns;
    }

    public boolean isWebFetchEnabled() {
        return webFetchEnabled;
    }

    public void setWebFetchEnabled(boolean webFetchEnabled) {
        this.webFetchEnabled = webFetchEnabled;
    }

    public int getWebFetchMaxBytes() {
        return webFetchMaxBytes;
    }

    public void setWebFetchMaxBytes(int webFetchMaxBytes) {
        this.webFetchMaxBytes = webFetchMaxBytes;
    }

    public boolean isWebSearchEnabled() {
        return webSearchEnabled;
    }

    public void setWebSearchEnabled(boolean webSearchEnabled) {
        this.webSearchEnabled = webSearchEnabled;
    }

    public String getWebSearchProvider() {
        return webSearchProvider;
    }

    public void setWebSearchProvider(String webSearchProvider) {
        this.webSearchProvider = webSearchProvider;
    }

    public boolean isBashSandboxEnabled() {
        return bashSandboxEnabled;
    }

    public void setBashSandboxEnabled(boolean bashSandboxEnabled) {
        this.bashSandboxEnabled = bashSandboxEnabled;
    }

    public String getBashSandboxMode() {
        return bashSandboxMode;
    }

    public void setBashSandboxMode(String bashSandboxMode) {
        this.bashSandboxMode = bashSandboxMode;
    }

    public String getStorage() {
        return storage;
    }

    public void setStorage(String storage) {
        this.storage = storage;
    }
}
