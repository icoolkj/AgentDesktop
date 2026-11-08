package com.agentdesktop.server.transcript;

import com.agentdesktop.server.dto.LlmHistoryMessageDto;
import com.agentdesktop.server.dto.LlmHistoryToolCallDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads Claude JSONL transcript from disk for chat.send — Claude Code never uploads history over the wire.
 */
@Component
public class ServerTranscriptLoader {

    private static final Logger log = LoggerFactory.getLogger(ServerTranscriptLoader.class);
    private static final int MAX_HISTORY = 48;

    private final ObjectMapper objectMapper;

    public ServerTranscriptLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<LlmHistoryMessageDto> loadHistory(String sessionId, String workspaceRoot) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        Path file = AgentDesktopPaths.transcriptFile(sessionId, workspaceRoot);
        if (!Files.isRegularFile(file)) {
            log.debug("No transcript file at {}", file);
            return List.of();
        }
        try {
            List<JsonNode> entries = new ArrayList<>();
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    entries.add(objectMapper.readTree(trimmed));
                } catch (Exception ignored) {
                    // skip malformed line
                }
            }
            List<JsonNode> chain = orderByChain(entries);
            List<LlmHistoryMessageDto> history = toLlmHistory(chain);
            if (history.size() > MAX_HISTORY) {
                return history.subList(history.size() - MAX_HISTORY, history.size());
            }
            return history;
        } catch (Exception e) {
            log.warn("Failed to load transcript {}: {}", file, e.getMessage());
            return List.of();
        }
    }

    /** Port of orderEntriesByChain + buildConversationChain (simplified). */
    static List<JsonNode> orderByChain(List<JsonNode> entries) {
        Map<String, JsonNode> messages = new HashMap<>();
        for (JsonNode entry : entries) {
            if (!isTranscriptMessage(entry)) {
                continue;
            }
            String uuid = textOrNull(entry, "uuid");
            if (uuid != null) {
                messages.put(uuid, entry);
            }
        }
        if (messages.isEmpty()) {
            return List.of();
        }

        Set<String> parentUuids = new HashSet<>();
        for (JsonNode msg : messages.values()) {
            String parent = textOrNull(msg, "parentUuid");
            if (parent != null) {
                parentUuids.add(parent);
            }
        }

        JsonNode leaf = messages.values().stream()
                .filter(m -> {
                    String uuid = textOrNull(m, "uuid");
                    return uuid != null && !parentUuids.contains(uuid);
                })
                .max(Comparator.comparing(ServerTranscriptLoader::parseTimestamp))
                .orElse(null);

        if (leaf == null) {
            return List.copyOf(messages.values());
        }

        List<JsonNode> chain = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        JsonNode current = leaf;
        while (current != null) {
            String uuid = textOrNull(current, "uuid");
            if (uuid == null || seen.contains(uuid)) {
                break;
            }
            seen.add(uuid);
            chain.add(current);
            String parent = textOrNull(current, "parentUuid");
            current = parent != null ? messages.get(parent) : null;
        }
        chain = new ArrayList<>(chain);
        java.util.Collections.reverse(chain);
        return chain;
    }

    static List<LlmHistoryMessageDto> toLlmHistory(List<JsonNode> chain) {
        List<LlmHistoryMessageDto> out = new ArrayList<>();
        for (JsonNode entry : chain) {
            String type = textOrNull(entry, "type");
            JsonNode message = entry.get("message");
            if (message == null || message.isMissingNode()) {
                continue;
            }
            if ("user".equals(type)) {
                appendUserMessage(out, message);
            } else if ("assistant".equals(type)) {
                appendAssistantMessage(out, message);
            }
        }
        return out;
    }

    private static void appendUserMessage(List<LlmHistoryMessageDto> out, JsonNode message) {
        JsonNode content = message.get("content");
        if (content == null) {
            return;
        }
        if (content.isTextual()) {
            String text = content.asText("").trim();
            if (!text.isEmpty()) {
                out.add(user(text));
            }
            return;
        }
        if (!content.isArray()) {
            return;
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode block : content) {
            String blockType = textOrNull(block, "type");
            if ("tool_result".equals(blockType)) {
                LlmHistoryMessageDto tool = new LlmHistoryMessageDto();
                tool.setRole("tool");
                tool.setToolCallId(textOrNull(block, "tool_use_id"));
                tool.setToolName("tool");
                tool.setContent(block.path("content").asText(""));
                out.add(tool);
            } else if ("text".equals(blockType)) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(block.path("text").asText(""));
            }
        }
        if (!text.isEmpty()) {
            out.add(user(text.toString().trim()));
        }
    }

    private static void appendAssistantMessage(List<LlmHistoryMessageDto> out, JsonNode message) {
        JsonNode content = message.get("content");
        if (content == null) {
            return;
        }
        if (content.isTextual()) {
            String text = content.asText("").trim();
            if (!text.isEmpty()) {
                out.add(assistant(text, null));
            }
            return;
        }
        if (!content.isArray()) {
            return;
        }
        StringBuilder text = new StringBuilder();
        List<LlmHistoryToolCallDto> toolCalls = new ArrayList<>();
        for (JsonNode block : content) {
            String blockType = textOrNull(block, "type");
            if ("tool_use".equals(blockType)) {
                LlmHistoryToolCallDto call = new LlmHistoryToolCallDto();
                call.setId(textOrNull(block, "id"));
                call.setName(textOrNull(block, "name"));
                JsonNode input = block.get("input");
                call.setArgumentsJson(input == null || input.isNull() ? "{}" : input.toString());
                toolCalls.add(call);
            } else if ("text".equals(blockType)) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(block.path("text").asText(""));
            }
        }
        if (!toolCalls.isEmpty()) {
            out.add(assistant(text.toString().trim(), toolCalls));
        } else if (!text.isEmpty()) {
            out.add(assistant(text.toString().trim(), null));
        }
    }

    private static LlmHistoryMessageDto user(String text) {
        LlmHistoryMessageDto dto = new LlmHistoryMessageDto();
        dto.setRole("user");
        dto.setContent(text);
        return dto;
    }

    private static LlmHistoryMessageDto assistant(String text, List<LlmHistoryToolCallDto> toolCalls) {
        LlmHistoryMessageDto dto = new LlmHistoryMessageDto();
        dto.setRole("assistant");
        dto.setContent(text == null ? "" : text);
        if (toolCalls != null && !toolCalls.isEmpty()) {
            dto.setToolCalls(toolCalls);
        }
        return dto;
    }

    private static boolean isTranscriptMessage(JsonNode entry) {
        String type = textOrNull(entry, "type");
        return "user".equals(type) || "assistant".equals(type) || "attachment".equals(type) || "system".equals(type);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        return v.asText(null);
    }

    private static long parseTimestamp(JsonNode node) {
        String ts = textOrNull(node, "timestamp");
        if (ts == null) {
            return 0;
        }
        try {
            return java.time.Instant.parse(ts).toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }
}
