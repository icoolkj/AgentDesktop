package com.agentdesktop.server.service;

import com.agentdesktop.server.domain.ChatMessage;
import com.agentdesktop.server.domain.ChatSession;
import com.agentdesktop.server.mapper.ChatMessageMapper;
import com.agentdesktop.server.mapper.ChatSessionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * 会话持久化：MySQL 为主存储，Redis 缓存活跃 session。
 * Redis/MySQL 不可用时降级；聊天仍可响应，但历史/会话列表可能不可用。
 */
@Service
public class ChatPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ChatPersistenceService.class);
    private static final String REDIS_SESSION_PREFIX = "agent:session:";

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final StringRedisTemplate redisTemplate;
    private final Duration sessionTtl;

    public ChatPersistenceService(
            ChatSessionMapper chatSessionMapper,
            ChatMessageMapper chatMessageMapper,
            StringRedisTemplate redisTemplate,
            @Value("${agent.cache.session-ttl-minutes:30}") long sessionTtlMinutes) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.redisTemplate = redisTemplate;
        this.sessionTtl = Duration.ofMinutes(sessionTtlMinutes);
    }

    public String ensureSession(String sessionId) {
        String id = (sessionId == null || sessionId.isBlank()) ? "default" : sessionId;

        try {
            String cacheKey = REDIS_SESSION_PREFIX + id;

            if (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))) {
                redisTemplate.expire(cacheKey, sessionTtl);
                chatSessionMapper.touchUpdatedAt(id);
                return id;
            }

            ChatSession existing = chatSessionMapper.findById(id);
            if (existing == null) {
                ChatSession session = new ChatSession();
                session.setId(id);
                session.setTitle("Chat " + id);
                chatSessionMapper.insert(session);
            } else {
                chatSessionMapper.touchUpdatedAt(id);
            }

            redisTemplate.opsForValue().set(cacheKey, "1", sessionTtl);
        } catch (Exception e) {
            log.warn("Persistence degraded (check Redis/MySQL). session={} reason={}", id, e.getMessage());
        }

        return id;
    }

    public void saveMessage(String sessionId, String role, String content) {
        saveMessage(sessionId, role, content, null);
    }

    public void saveMessage(String sessionId, String role, String content, String toolsJson) {
        try {
            ChatMessage message = new ChatMessage();
            message.setSessionId(sessionId);
            message.setRole(role);
            message.setContent(content == null ? "" : content);
            message.setToolsJson(toolsJson);
            chatMessageMapper.insert(message);
        } catch (Exception e) {
            log.warn("Failed to save message to MySQL: {}", e.getMessage());
        }
    }

    /**
     * Persist one assistant turn including tool transcript (Claude Code JSONL parity).
     */
    public void saveAssistantTurn(String sessionId, String content, String toolsJson) {
        boolean hasTools = toolsJson != null && !toolsJson.isBlank();
        String text = content == null ? "" : content.trim();
        if (text.isEmpty() && !hasTools) {
            return;
        }
        if (text.isEmpty() && hasTools) {
            text = "（无文字输出，请展开工具详情查看分析过程）";
        }
        saveMessage(sessionId, "assistant", text, toolsJson);
    }

    public void maybeSetSessionTitle(String sessionId, String userText) {
        if (userText == null || userText.isBlank()) {
            return;
        }
        String trimmed = userText.trim().replaceAll("\\s+", " ");
        String title = trimmed.length() > 80 ? trimmed.substring(0, 77) + "..." : trimmed;
        try {
            chatSessionMapper.updateTitleIfDefault(sessionId, title);
        } catch (Exception e) {
            log.warn("Failed to update session title for {}: {}", sessionId, e.getMessage());
        }
    }

    public List<ChatMessage> listMessages(String sessionId, int limit) {
        try {
            List<ChatMessage> all = chatMessageMapper.listBySessionId(sessionId);
            if (all == null || all.isEmpty()) {
                return List.of();
            }
            if (all.size() <= limit) {
                return all;
            }
            return all.subList(all.size() - limit, all.size());
        } catch (Exception e) {
            log.warn("Failed to load messages for session {}: {}", sessionId, e.getMessage());
            return List.of();
        }
    }

    public List<ChatSession> listRecentSessions(int limit, String workspace) {
        try {
            String normalized = workspace == null ? null : workspace.trim();
            if (normalized != null && normalized.isEmpty()) {
                normalized = null;
            }
            return chatSessionMapper.listRecent(limit, normalized);
        } catch (Exception e) {
            log.warn("Failed to list sessions: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public void updateWorkspace(String sessionId, String workspaceRoot) {
        if (workspaceRoot == null || workspaceRoot.isBlank()) {
            return;
        }
        try {
            chatSessionMapper.updateWorkspace(sessionId, workspaceRoot);
        } catch (Exception e) {
            log.warn("Failed to update workspace for session {}: {}", sessionId, e.getMessage());
        }
    }
}
