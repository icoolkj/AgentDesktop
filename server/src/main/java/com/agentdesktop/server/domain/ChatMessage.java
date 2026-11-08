package com.agentdesktop.server.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessage {
    private Long id;
    private String sessionId;
    private String role;
    private String content;
    /** JSON array of tool activity (assistant turns). */
    private String toolsJson;
    private LocalDateTime createdAt;
}
