package com.agentdesktop.server.agent.llm.anthropic;

import com.agentdesktop.server.agent.llm.LlmChatRequest;
import com.agentdesktop.server.agent.llm.LlmContentPart;
import com.agentdesktop.server.agent.llm.LlmMessage;
import com.agentdesktop.server.agent.llm.LlmResponse;
import com.agentdesktop.server.agent.llm.LlmRole;
import com.agentdesktop.server.agent.llm.LlmToolCall;
import com.agentdesktop.server.agent.llm.LlmToolDefinition;
import com.agentdesktop.server.agent.llm.LlmUsage;
import com.agentdesktop.server.agent.settings.LlmRuntimeConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Anthropic Messages API ({@code /v1/messages}) — required for image/PDF blocks on CPA
 * where {@code /v1/chat/completions} only accepts text content.
 */
public final class AnthropicMessagesMappers {

    private AnthropicMessagesMappers() {
    }

    public static boolean requestHasMedia(LlmChatRequest request) {
        for (LlmMessage message : request.messages()) {
            for (LlmContentPart part : message.contentParts()) {
                String type = part.type();
                if ("image".equals(type) || "document".equals(type) || "image_url".equals(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static ObjectNode toApiBody(LlmChatRequest request, LlmRuntimeConfig config) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode body = mapper.createObjectNode();

        String model = request.model();
        if (model == null || model.isBlank()) {
            model = config.model();
        }
        body.put("model", model);
        body.put("max_tokens", request.maxTokens() != null ? request.maxTokens() : config.maxTokens());

        if (request.temperature() != null) {
            body.put("temperature", request.temperature());
        } else {
            body.put("temperature", config.temperature());
        }

        body.put("stream", request.stream());

        String systemText = extractSystemText(request.messages());
        if (!systemText.isBlank()) {
            body.put("system", systemText);
        }

        ArrayNode messages = body.putArray("messages");
        for (LlmMessage message : request.messages()) {
            if (message.role() == LlmRole.SYSTEM) {
                continue;
            }
            if (message.role() == LlmRole.TOOL) {
                messages.add(toolResultMessage(mapper, message));
                continue;
            }
            messages.add(roleMessage(mapper, message));
        }

        if (!request.tools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (LlmToolDefinition tool : request.tools()) {
                tools.add(toAnthropicTool(mapper, tool));
            }
        }

        return body;
    }

    public static LlmResponse fromCompletion(JsonNode root) {
        StringBuilder text = new StringBuilder();
        List<LlmToolCall> toolCalls = new ArrayList<>();
        JsonNode content = root.path("content");
        if (content.isArray()) {
            for (JsonNode block : content) {
                String type = block.path("type").asText("");
                if ("text".equals(type)) {
                    text.append(block.path("text").asText(""));
                } else if ("tool_use".equals(type)) {
                    toolCalls.add(new LlmToolCall(
                            block.path("id").asText(""),
                            block.path("name").asText(""),
                            block.path("input").toString()
                    ));
                }
            }
        }
        String stopReason = root.path("stop_reason").asText(null);
        LlmUsage usage = parseUsage(root.path("usage"));
        return new LlmResponse(text.toString(), toolCalls, stopReason, usage);
    }

    public static LlmUsage parseUsage(JsonNode usageNode) {
        if (usageNode.isMissingNode()) {
            return LlmUsage.empty();
        }
        return new LlmUsage(
                usageNode.path("input_tokens").asInt(0),
                usageNode.path("output_tokens").asInt(0),
                usageNode.path("input_tokens").asInt(0) + usageNode.path("output_tokens").asInt(0)
        );
    }

    public static AnthropicStreamAccumulator newStreamAccumulator() {
        return new AnthropicStreamAccumulator();
    }

    private static String extractSystemText(List<LlmMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (LlmMessage message : messages) {
            if (message.role() == LlmRole.SYSTEM && message.content() != null) {
                if (!sb.isEmpty()) {
                    sb.append("\n\n");
                }
                sb.append(message.content());
            }
        }
        return sb.toString();
    }

    private static ObjectNode roleMessage(ObjectMapper mapper, LlmMessage message) {
        ObjectNode node = mapper.createObjectNode();
        node.put("role", message.role() == LlmRole.ASSISTANT ? "assistant" : "user");

        if (!message.contentParts().isEmpty()) {
            ArrayNode content = node.putArray("content");
            for (LlmContentPart part : message.contentParts()) {
                appendContentPart(content, part);
            }
        } else if (message.content() != null && !message.content().isBlank()) {
            node.put("content", message.content());
        } else {
            node.put("content", "");
        }

        if (message.role() == LlmRole.ASSISTANT && !message.toolCalls().isEmpty()) {
            if (!node.has("content") || node.get("content").isTextual()) {
                String text = node.path("content").asText("");
                node.remove("content");
                ArrayNode content = node.putArray("content");
                if (!text.isBlank()) {
                    ObjectNode textNode = content.addObject();
                    textNode.put("type", "text");
                    textNode.put("text", text);
                }
                for (LlmToolCall call : message.toolCalls()) {
                    ObjectNode toolUse = content.addObject();
                    toolUse.put("type", "tool_use");
                    toolUse.put("id", call.id());
                    toolUse.put("name", call.name());
                    try {
                        toolUse.set("input", mapper.readTree(
                                call.argumentsJson() == null ? "{}" : call.argumentsJson()));
                    } catch (Exception e) {
                        toolUse.set("input", mapper.createObjectNode());
                    }
                }
            }
        }

        return node;
    }

    private static ObjectNode toolResultMessage(ObjectMapper mapper, LlmMessage message) {
        ObjectNode node = mapper.createObjectNode();
        node.put("role", "user");
        ArrayNode content = node.putArray("content");
        ObjectNode block = content.addObject();
        block.put("type", "tool_result");
        block.put("tool_use_id", message.toolCallId());
        block.put("content", message.content() == null ? "" : message.content());
        return node;
    }

    private static void appendContentPart(ArrayNode content, LlmContentPart part) {
        if ("text".equals(part.type())) {
            ObjectNode textNode = content.addObject();
            textNode.put("type", "text");
            textNode.put("text", part.text() == null ? "" : part.text());
        } else if ("image".equals(part.type())) {
            ObjectNode imageNode = content.addObject();
            imageNode.put("type", "image");
            ObjectNode source = imageNode.putObject("source");
            source.put("type", "base64");
            source.put("media_type", part.documentMime() == null ? "image/png" : part.documentMime());
            source.put("data", part.documentBase64() == null ? "" : part.documentBase64());
        } else if ("document".equals(part.type())) {
            ObjectNode docNode = content.addObject();
            docNode.put("type", "document");
            ObjectNode source = docNode.putObject("source");
            source.put("type", "base64");
            source.put("media_type", part.documentMime() == null ? "application/pdf" : part.documentMime());
            source.put("data", part.documentBase64() == null ? "" : part.documentBase64());
        } else if ("image_url".equals(part.type()) && part.imageUrl() != null && part.imageUrl().startsWith("data:")) {
            int comma = part.imageUrl().indexOf(',');
            if (comma > 5) {
                String header = part.imageUrl().substring(5, comma);
                String mime = header.contains(";") ? header.substring(0, header.indexOf(';')) : header;
                appendContentPart(content, LlmContentPart.image(mime, part.imageUrl().substring(comma + 1)));
            }
        }
    }

    private static ObjectNode toAnthropicTool(ObjectMapper mapper, LlmToolDefinition tool) {
        ObjectNode node = mapper.createObjectNode();
        node.put("name", tool.name());
        node.put("description", tool.description());
        node.set("input_schema", mapper.valueToTree(tool.parameters()));
        return node;
    }

    public static final class AnthropicStreamAccumulator {

        private final StringBuilder text = new StringBuilder();
        private final List<LlmToolCall> toolCalls = new ArrayList<>();
        private String finishReason;
        private LlmUsage usage = LlmUsage.empty();

        public void applyEvent(JsonNode event) {
            String type = event.path("type").asText("");
            if ("content_block_start".equals(type)) {
                JsonNode block = event.path("content_block");
                if ("tool_use".equals(block.path("type").asText())) {
                    toolCalls.add(new LlmToolCall(
                            block.path("id").asText(""),
                            block.path("name").asText(""),
                            "{}"
                    ));
                }
            } else if ("content_block_delta".equals(type)) {
                JsonNode delta = event.path("delta");
                if ("text_delta".equals(delta.path("type").asText())) {
                    text.append(delta.path("text").asText(""));
                } else if ("input_json_delta".equals(delta.path("type").asText()) && !toolCalls.isEmpty()) {
                    LlmToolCall last = toolCalls.get(toolCalls.size() - 1);
                    String partial = delta.path("partial_json").asText("");
                    toolCalls.set(toolCalls.size() - 1, new LlmToolCall(
                            last.id(), last.name(), last.argumentsJson() + partial));
                }
            } else if ("message_delta".equals(type)) {
                finishReason = event.path("delta").path("stop_reason").asText(null);
                usage = parseUsage(event.path("usage"));
            } else if ("message_stop".equals(type)) {
                finishReason = event.path("message").path("stop_reason").asText(finishReason);
            }
        }

        public LlmResponse toResponse() {
            return new LlmResponse(text.toString(), toolCalls, finishReason, usage);
        }
    }
}
