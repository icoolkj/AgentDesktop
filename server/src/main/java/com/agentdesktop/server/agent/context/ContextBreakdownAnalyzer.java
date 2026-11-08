package com.agentdesktop.server.agent.context;

import com.agentdesktop.server.agent.llm.LlmContentPart;
import com.agentdesktop.server.agent.llm.LlmMessage;
import com.agentdesktop.server.agent.llm.LlmRole;
import com.agentdesktop.server.agent.llm.LlmToolDefinition;
import com.agentdesktop.server.agent.tool.ToolContext;
import com.agentdesktop.server.agent.tool.ToolRegistry;
import com.agentdesktop.server.dto.ChatAttachmentDto;
import com.agentdesktop.server.dto.ChatSendParams;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Estimated context breakdown — categories aligned with Claude Code {@code analyzeContext.ts}
 * and Cursor Context panel (system / tools / rules / attachments / conversation / buffer).
 */
public final class ContextBreakdownAnalyzer {

    public static final String COLOR_SYSTEM = "#999999";
    public static final String COLOR_TOOLS = "#9333ea";
    public static final String COLOR_RULES = "#16a34a";
    public static final String COLOR_GIT = "#0891b2";
    public static final String COLOR_ATTACHMENTS = "#eab308";
    public static final String COLOR_CONVERSATION = "#ea580c";
    public static final String COLOR_BUFFER = "#666666";
    public static final String COLOR_FREE = "#444444";

    private ContextBreakdownAnalyzer() {
    }

    public record Category(String id, String label, int chars, String color) {
    }

    public record Breakdown(
            int totalChars,
            int maxChars,
            int percent,
            String model,
            List<Category> categories
    ) {
    }

    public static Breakdown analyze(
            List<LlmMessage> messages,
            ToolContext toolContext,
            ChatSendParams params,
            List<LlmToolDefinition> tools,
            int maxChars,
            int bufferChars,
            String model) {
        List<Category> categories = new ArrayList<>();
        int systemChars = 0;
        int conversationChars = 0;

        for (LlmMessage message : messages) {
            int chars = messageChars(message);
            if (message.role() == LlmRole.SYSTEM) {
                systemChars += chars;
            } else {
                conversationChars += chars;
            }
        }

        categories.add(new Category("system", "System prompt", systemChars, COLOR_SYSTEM));

        int toolChars = estimateToolDefinitionChars(tools);
        if (toolChars > 0) {
            categories.add(new Category("tools", "Tool definitions", toolChars, COLOR_TOOLS));
        }

        int rulesChars = params != null && params.getProjectRules() != null
                ? params.getProjectRules().length()
                : 0;
        if (rulesChars > 0) {
            categories.add(new Category("rules", "Rules", rulesChars, COLOR_RULES));
        }

        int gitChars = params != null && params.getGitStatusSummary() != null
                ? params.getGitStatusSummary().length()
                : 0;
        if (gitChars > 0) {
            categories.add(new Category("git", "Git status", gitChars, COLOR_GIT));
        }

        int attachmentChars = estimateAttachmentChars(params);
        if (attachmentChars > 0) {
            categories.add(new Category("attachments", "Attachments", attachmentChars, COLOR_ATTACHMENTS));
        }

        categories.add(new Category("conversation", "Conversation", conversationChars, COLOR_CONVERSATION));

        int used = categories.stream().mapToInt(Category::chars).sum();
        int buffer = Math.min(bufferChars, Math.max(0, maxChars - used));
        if (buffer > 0) {
            categories.add(new Category("buffer", "Autocompact buffer", buffer, COLOR_BUFFER));
        }

        int total = used + buffer;
        int free = Math.max(0, maxChars - total);
        if (free > 0) {
            categories.add(new Category("free", "Free space", free, COLOR_FREE));
        }

        int percent = maxChars > 0 ? Math.min(100, (int) Math.round(used * 100.0 / maxChars)) : 0;
        return new Breakdown(used, maxChars, percent, model == null ? "" : model, categories);
    }

    private static int messageChars(LlmMessage message) {
        if (!message.contentParts().isEmpty()) {
            int total = 0;
            for (LlmContentPart part : message.contentParts()) {
                if ("text".equals(part.type()) && part.text() != null) {
                    total += part.text().length();
                } else if ("image".equals(part.type()) || "image_url".equals(part.type())) {
                    total += 2_000;
                } else if ("document".equals(part.type()) && part.documentBase64() != null) {
                    total += Math.min(part.documentBase64().length(), 20_000);
                }
            }
            return total;
        }
        return message.content() == null ? 0 : message.content().length();
    }

    private static int estimateToolDefinitionChars(List<LlmToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return 0;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            int total = 0;
            for (LlmToolDefinition tool : tools) {
                total += tool.name().length() + tool.description().length();
                total += mapper.writeValueAsString(tool.parameters()).length();
            }
            return total;
        } catch (Exception e) {
            return tools.size() * 512;
        }
    }

    private static int estimateAttachmentChars(ChatSendParams params) {
        if (params == null || params.getAttachments() == null) {
            return 0;
        }
        int total = 0;
        for (ChatAttachmentDto att : params.getAttachments()) {
            if (att == null) {
                continue;
            }
            if ("image".equals(att.getKind()) || "pdf".equals(att.getKind())) {
                if (att.getBase64() != null) {
                    total += Math.min(att.getBase64().length(), 50_000);
                } else {
                    total += 2_000;
                }
            } else if (att.getContent() != null) {
                total += att.getContent().length();
            }
        }
        return total;
    }

    public static Map<String, Object> toEventMap(Breakdown breakdown) {
        List<Map<String, Object>> cats = new ArrayList<>();
        for (Category cat : breakdown.categories()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", cat.id());
            row.put("label", cat.label());
            row.put("chars", cat.chars());
            row.put("color", cat.color());
            cats.add(row);
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalChars", breakdown.totalChars());
        map.put("maxChars", breakdown.maxChars());
        map.put("percent", breakdown.percent());
        map.put("model", breakdown.model());
        map.put("categories", cats);
        return map;
    }
}
