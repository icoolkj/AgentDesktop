package com.agentdesktop.server.web;

import com.agentdesktop.server.domain.ChatMessage;
import com.agentdesktop.server.domain.ChatSession;
import com.agentdesktop.server.dto.ChatMessageDto;
import com.agentdesktop.server.dto.ChatSessionDto;
import com.agentdesktop.server.dto.ChatToolRecordDto;
import com.agentdesktop.server.service.ChatPersistenceService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * Legacy MySQL chat REST API.
 *
 * @deprecated Session history is persisted client-side as JSONL (transcript IPC).
 *             UI uses {@code window.transcriptApi}; these endpoints remain for migration/debug only.
 */
@Deprecated(since = "0.2.0", forRemoval = true)
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final TypeReference<List<ChatToolRecordDto>> TOOL_LIST = new TypeReference<>() {};

    private final ChatPersistenceService chatPersistenceService;
    private final ObjectMapper objectMapper;

    public ChatController(ChatPersistenceService chatPersistenceService, ObjectMapper objectMapper) {
        this.chatPersistenceService = chatPersistenceService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/sessions")
    public List<ChatSessionDto> listSessions(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String workspace) {
        int capped = Math.min(Math.max(limit, 1), 100);
        return chatPersistenceService.listRecentSessions(capped, workspace).stream()
                .map(this::toSessionDto)
                .toList();
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public List<ChatMessageDto> listMessages(@PathVariable String sessionId) {
        return chatPersistenceService.listMessages(sessionId, 500).stream()
                .map(this::toMessageDto)
                .toList();
    }

    private ChatSessionDto toSessionDto(ChatSession session) {
        ChatSessionDto dto = new ChatSessionDto();
        dto.setId(session.getId());
        dto.setTitle(session.getTitle());
        dto.setWorkspace(session.getWorkspace());
        dto.setUpdatedAt(session.getUpdatedAt());
        return dto;
    }

    private ChatMessageDto toMessageDto(ChatMessage message) {
        ChatMessageDto dto = new ChatMessageDto();
        dto.setId(message.getId());
        dto.setRole(message.getRole());
        dto.setContent(message.getContent());
        dto.setTools(parseTools(message.getToolsJson()));
        dto.setCreatedAt(message.getCreatedAt());
        return dto;
    }

    private List<ChatToolRecordDto> parseTools(String toolsJson) {
        if (toolsJson == null || toolsJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(toolsJson, TOOL_LIST);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
