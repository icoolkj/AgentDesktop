package com.agentdesktop.server.agent.llm.openai;

import com.agentdesktop.server.agent.llm.LlmChatRequest;
import com.agentdesktop.server.agent.llm.LlmContentPart;
import com.agentdesktop.server.agent.llm.LlmMessage;
import com.agentdesktop.server.agent.llm.LlmRole;
import com.agentdesktop.server.agent.llm.LlmToolCall;
import com.agentdesktop.server.agent.llm.LlmToolDefinition;
import com.agentdesktop.server.agent.settings.LlmRuntimeConfig;
import com.agentdesktop.server.config.LlmProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class OpenAiRequestMapper {

    private OpenAiRequestMapper() {
    }

    static ObjectNode toApiBody(LlmChatRequest request, LlmRuntimeConfig config) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode body = mapper.createObjectNode();

        String model = request.model();
        if (model == null || model.isBlank()) {
            model = config.model();
        }
        body.put("model", model);
        body.put("stream", request.stream());

        if (request.temperature() != null) {
            body.put("temperature", request.temperature());
        } else {
            body.put("temperature", config.temperature());
        }

        int maxTokens = request.maxTokens() != null ? request.maxTokens() : config.maxTokens();
        body.put("max_tokens", maxTokens);

        ArrayNode messages = body.putArray("messages");
        for (LlmMessage message : request.messages()) {
            messages.add(toApiMessage(mapper, message, config));
        }

        if (!request.tools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (LlmToolDefinition tool : request.tools()) {
                tools.add(toApiTool(mapper, tool));
            }
            if (request.toolChoice() != null && !request.toolChoice().isBlank()) {
                body.put("tool_choice", request.toolChoice());
            } else {
                body.put("tool_choice", "auto");
            }
            body.put("parallel_tool_calls", true);
        } else if (request.toolChoice() != null && !request.toolChoice().isBlank()) {
            body.put("tool_choice", request.toolChoice());
        }

        return body;
    }

    private static ObjectNode toApiMessage(ObjectMapper mapper, LlmMessage message, LlmRuntimeConfig config) {
        ObjectNode node = mapper.createObjectNode();
        node.put("role", message.role().apiValue());

        if (message.role() == LlmRole.TOOL) {
            node.put("tool_call_id", message.toolCallId());
            node.put("content", message.content() == null ? "" : message.content());
            return node;
        }

        if (!message.contentParts().isEmpty()) {
            boolean anthropicBlocks = useAnthropicContentBlocks(config);
            ArrayNode contentArray = node.putArray("content");
            for (LlmContentPart part : message.contentParts()) {
                if ("text".equals(part.type())) {
                    ObjectNode textNode = contentArray.addObject();
                    textNode.put("type", "text");
                    textNode.put("text", part.text() == null ? "" : part.text());
                } else if ("image".equals(part.type())) {
                    appendImageBlock(contentArray, part, anthropicBlocks);
                } else if ("image_url".equals(part.type())) {
                    appendImageUrlBlock(contentArray, part, anthropicBlocks);
                } else if ("document".equals(part.type())) {
                    ObjectNode docNode = contentArray.addObject();
                    docNode.put("type", "document");
                    ObjectNode source = docNode.putObject("source");
                    source.put("type", "base64");
                    source.put("media_type", part.documentMime() == null ? "application/pdf" : part.documentMime());
                    source.put("data", part.documentBase64() == null ? "" : part.documentBase64());
                }
            }
        } else if (message.content() != null) {
            node.put("content", message.content());
        }

        if (message.role() == LlmRole.ASSISTANT && !message.toolCalls().isEmpty()) {
            ArrayNode toolCalls = node.putArray("tool_calls");
            for (LlmToolCall call : message.toolCalls()) {
                ObjectNode callNode = toolCalls.addObject();
                callNode.put("id", call.id());
                callNode.put("type", "function");
                ObjectNode function = callNode.putObject("function");
                function.put("name", call.name());
                function.put("arguments", call.argumentsJson() == null ? "{}" : call.argumentsJson());
            }
        }

        return node;
    }

    /**
     * CPA / Claude models expect Anthropic content blocks ({@code image}, {@code document}),
     * not OpenAI {@code image_url}. Default CPA port 8317 uses Anthropic wire format.
     */
    static boolean useAnthropicContentBlocks(LlmRuntimeConfig config) {
        String model = config.model() == null ? "" : config.model().toLowerCase();
        String base = config.baseUrl() == null ? "" : config.baseUrl().toLowerCase();
        if (model.contains("claude")) {
            return true;
        }
        if (base.contains("8317") || base.contains("cliproxy") || base.contains("anthropic")) {
            return true;
        }
        return false;
    }

    private static void appendImageBlock(ArrayNode contentArray, LlmContentPart part, boolean anthropicBlocks) {
        String mime = part.documentMime() == null || part.documentMime().isBlank() ? "image/png" : part.documentMime();
        String base64 = part.documentBase64() == null ? "" : part.documentBase64();
        if (anthropicBlocks) {
            ObjectNode imageNode = contentArray.addObject();
            imageNode.put("type", "image");
            ObjectNode source = imageNode.putObject("source");
            source.put("type", "base64");
            source.put("media_type", mime);
            source.put("data", base64);
            return;
        }
        ObjectNode imageNode = contentArray.addObject();
        imageNode.put("type", "image_url");
        ObjectNode urlNode = imageNode.putObject("image_url");
        urlNode.put("url", "data:" + mime + ";base64," + base64);
    }

    private static void appendImageUrlBlock(ArrayNode contentArray, LlmContentPart part, boolean anthropicBlocks) {
        String url = part.imageUrl() == null ? "" : part.imageUrl();
        if (anthropicBlocks && url.startsWith("data:")) {
            int comma = url.indexOf(',');
            if (comma > 5) {
                String header = url.substring(5, comma);
                String mime = header.contains(";") ? header.substring(0, header.indexOf(';')) : header;
                String base64 = url.substring(comma + 1);
                appendImageBlock(contentArray, LlmContentPart.image(mime, base64), true);
                return;
            }
        }
        ObjectNode imageNode = contentArray.addObject();
        imageNode.put("type", "image_url");
        ObjectNode urlNode = imageNode.putObject("image_url");
        urlNode.put("url", url);
    }

    private static ObjectNode toApiTool(ObjectMapper mapper, LlmToolDefinition tool) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "function");
        ObjectNode function = node.putObject("function");
        function.put("name", tool.name());
        function.put("description", tool.description());
        function.set("parameters", mapper.valueToTree(tool.parameters()));
        return node;
    }
}

final class OpenAiResponseMapper {

    private OpenAiResponseMapper() {
    }

    static com.agentdesktop.server.agent.llm.LlmResponse fromCompletion(JsonNode root) {
        JsonNode choice = root.path("choices").path(0);
        JsonNode message = choice.path("message");

        String content = message.path("content").isNull() ? "" : message.path("content").asText("");
        List<LlmToolCall> toolCalls = parseToolCalls(message.path("tool_calls"));
        String finishReason = choice.path("finish_reason").asText(null);
        com.agentdesktop.server.agent.llm.LlmUsage usage = parseUsage(root.path("usage"));

        return new com.agentdesktop.server.agent.llm.LlmResponse(content, toolCalls, finishReason, usage);
    }

    static List<LlmToolCall> parseToolCalls(JsonNode toolCallsNode) {
        if (!toolCallsNode.isArray()) {
            return List.of();
        }

        List<LlmToolCall> calls = new ArrayList<>();
        for (JsonNode callNode : toolCallsNode) {
            JsonNode function = callNode.path("function");
            calls.add(new LlmToolCall(
                    callNode.path("id").asText(""),
                    function.path("name").asText(""),
                    function.path("arguments").asText("{}")
            ));
        }
        return calls;
    }

    static com.agentdesktop.server.agent.llm.LlmUsage parseUsage(JsonNode usageNode) {
        if (usageNode.isMissingNode() || usageNode.isNull()) {
            return com.agentdesktop.server.agent.llm.LlmUsage.empty();
        }
        return new com.agentdesktop.server.agent.llm.LlmUsage(
                usageNode.path("prompt_tokens").asInt(0),
                usageNode.path("completion_tokens").asInt(0),
                usageNode.path("total_tokens").asInt(0)
        );
    }
}

final class OpenAiStreamAccumulator {

    private final StringBuilder content = new StringBuilder();
    private final Map<Integer, ToolCallBuilder> toolCalls = new LinkedHashMap<>();
    private String finishReason;
    private com.agentdesktop.server.agent.llm.LlmUsage usage = com.agentdesktop.server.agent.llm.LlmUsage.empty();

    void applyDelta(JsonNode choice) {
        if (!choice.path("finish_reason").isNull()) {
            finishReason = choice.path("finish_reason").asText(null);
        }

        JsonNode delta = choice.path("delta");
        if (!delta.path("content").isNull()) {
            content.append(delta.path("content").asText(""));
        }

        JsonNode toolCallsNode = delta.path("tool_calls");
        if (toolCallsNode.isArray()) {
            for (JsonNode toolDelta : toolCallsNode) {
                int index = toolDelta.path("index").asInt(0);
                ToolCallBuilder builder = toolCalls.computeIfAbsent(index, ignored -> new ToolCallBuilder());
                if (!toolDelta.path("id").isNull()) {
                    builder.id = toolDelta.path("id").asText("");
                }
                JsonNode function = toolDelta.path("function");
                if (!function.path("name").isNull()) {
                    builder.name = function.path("name").asText("");
                }
                if (!function.path("arguments").isNull()) {
                    builder.arguments.append(function.path("arguments").asText(""));
                }
            }
        }
    }

    void applyUsage(JsonNode usageNode) {
        usage = OpenAiResponseMapper.parseUsage(usageNode);
    }

    com.agentdesktop.server.agent.llm.LlmResponse toResponse() {
        List<LlmToolCall> calls = toolCalls.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getValue().toCall())
                .toList();
        return new com.agentdesktop.server.agent.llm.LlmResponse(
                content.toString(),
                calls,
                finishReason,
                usage
        );
    }

    private static final class ToolCallBuilder {
        private String id = "";
        private String name = "";
        private final StringBuilder arguments = new StringBuilder();

        LlmToolCall toCall() {
            return new LlmToolCall(id, name, arguments.toString());
        }
    }
}
