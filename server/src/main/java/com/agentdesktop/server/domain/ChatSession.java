package com.agentdesktop.server.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatSession {
    private String id;
    private String title;
    private String workspace;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
