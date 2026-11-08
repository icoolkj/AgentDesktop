package com.agentdesktop.server.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LlmSettingsRow {
    private int id;
    private boolean enabled;
    private String provider;
    private String baseUrl;
    private String apiKey;
    private String model;
    private double temperature;
    private int maxTokens;
    private int timeoutSeconds;
    private boolean streamToolTurns;
    private boolean streamAnswerTurns;
    private LocalDateTime updatedAt;
}
