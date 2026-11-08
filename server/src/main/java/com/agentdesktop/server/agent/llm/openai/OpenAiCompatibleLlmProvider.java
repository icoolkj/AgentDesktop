package com.agentdesktop.server.agent.llm.openai;

import com.agentdesktop.server.agent.llm.LlmChatRequest;
import com.agentdesktop.server.agent.llm.LlmException;
import com.agentdesktop.server.agent.llm.LlmProvider;
import com.agentdesktop.server.agent.llm.LlmResponse;
import com.agentdesktop.server.agent.llm.LlmStreamEvent;
import com.agentdesktop.server.agent.llm.LlmStreamHandler;
import com.agentdesktop.server.agent.llm.anthropic.AnthropicMessagesMappers;
import com.agentdesktop.server.agent.llm.anthropic.AnthropicMessagesMappers.AnthropicStreamAccumulator;
import com.agentdesktop.server.agent.llm.openai.OpenAiRequestMapper;
import com.agentdesktop.server.agent.llm.openai.OpenAiResponseMapper;
import com.agentdesktop.server.agent.llm.openai.OpenAiStreamAccumulator;
import com.agentdesktop.server.agent.settings.LlmSettingsService;
import com.agentdesktop.server.agent.settings.LlmRuntimeConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * OpenAI Chat Completions API compatible provider.
 * Works with OpenAI, Azure OpenAI (when base-url is set), Ollama (/v1), DeepSeek, etc.
 */
public class OpenAiCompatibleLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmProvider.class);
    public static final String PROVIDER_ID = "openai-compatible";

    private final LlmSettingsService llmSettingsService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleLlmProvider(LlmSettingsService llmSettingsService, ObjectMapper objectMapper) {
        this.llmSettingsService = llmSettingsService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /** Package-private for tests with custom HttpClient. */
    OpenAiCompatibleLlmProvider(LlmSettingsService llmSettingsService, ObjectMapper objectMapper, HttpClient httpClient) {
        this.llmSettingsService = llmSettingsService;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean isConfigured() {
        return llmSettingsService.getRuntimeConfig().isConfigured();
    }

    @Override
    public LlmResponse complete(LlmChatRequest request) {
        ensureConfigured();
        LlmChatRequest nonStream = LlmChatRequest.builder()
                .messages(request.messages())
                .model(request.model())
                .tools(request.tools())
                .toolChoice(request.toolChoice())
                .temperature(request.temperature())
                .maxTokens(request.maxTokens())
                .stream(false)
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    buildHttpRequest(nonStream),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            ensureSuccess(response.statusCode(), response.body());
            JsonNode root = objectMapper.readTree(response.body());
            if (useAnthropicMessagesApi(nonStream)) {
                return AnthropicMessagesMappers.fromCompletion(root);
            }
            return OpenAiResponseMapper.fromCompletion(root);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("LLM request failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void streamComplete(LlmChatRequest request, LlmStreamHandler handler) {
        ensureConfigured();
        LlmChatRequest streamRequest = LlmChatRequest.builder()
                .messages(request.messages())
                .model(request.model())
                .tools(request.tools())
                .toolChoice(request.toolChoice())
                .temperature(request.temperature())
                .maxTokens(request.maxTokens())
                .stream(true)
                .build();

        OpenAiStreamAccumulator openAiAccumulator = new OpenAiStreamAccumulator();
        AnthropicStreamAccumulator anthropicAccumulator = AnthropicMessagesMappers.newStreamAccumulator();
        boolean anthropic = useAnthropicMessagesApi(streamRequest);

        try {
            HttpResponse<InputStream> response = httpClient.send(
                    buildHttpRequest(streamRequest),
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new LlmException(response.statusCode(), extractErrorMessage(errorBody));
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    if (anthropic) {
                        if (!line.startsWith("data:")) {
                            continue;
                        }
                        String payload = line.substring(5).trim();
                        if (payload.isEmpty() || "[DONE]".equals(payload)) {
                            continue;
                        }
                        JsonNode event = objectMapper.readTree(payload);
                        anthropicAccumulator.applyEvent(event);
                        String eventType = event.path("type").asText("");
                        if ("content_block_delta".equals(eventType)) {
                            JsonNode delta = event.path("delta");
                            if ("text_delta".equals(delta.path("type").asText())) {
                                String text = delta.path("text").asText("");
                                if (!text.isEmpty()) {
                                    handler.onEvent(new LlmStreamEvent.ContentDelta(text));
                                }
                            }
                        }
                        continue;
                    }

                    if (!line.startsWith("data:")) {
                        continue;
                    }

                    String payload = line.substring(5).trim();
                    if ("[DONE]".equals(payload)) {
                        break;
                    }

                    JsonNode root = objectMapper.readTree(payload);
                    if (!root.path("usage").isMissingNode()) {
                        openAiAccumulator.applyUsage(root.path("usage"));
                    }

                    JsonNode choice = root.path("choices").path(0);
                    JsonNode delta = choice.path("delta");

                    if (!delta.path("content").isNull()) {
                        String contentDelta = delta.path("content").asText("");
                        if (!contentDelta.isEmpty()) {
                            handler.onEvent(new LlmStreamEvent.ContentDelta(contentDelta));
                        }
                    }

                    JsonNode toolCallsNode = delta.path("tool_calls");
                    if (toolCallsNode.isArray()) {
                        for (JsonNode toolDelta : toolCallsNode) {
                            int index = toolDelta.path("index").asInt(0);
                            String id = toolDelta.path("id").isNull() ? null : toolDelta.path("id").asText();
                            JsonNode function = toolDelta.path("function");
                            String name = function.path("name").isNull() ? null : function.path("name").asText();
                            String argsDelta = function.path("arguments").isNull()
                                    ? null
                                    : function.path("arguments").asText();
                            handler.onEvent(new LlmStreamEvent.ToolCallDelta(index, id, name, argsDelta));
                        }
                    }

                    openAiAccumulator.applyDelta(choice);
                }
            }

            LlmResponse completed = anthropic ? anthropicAccumulator.toResponse() : openAiAccumulator.toResponse();
            handler.onEvent(new LlmStreamEvent.Completed(completed));
        } catch (LlmException e) {
            handler.onEvent(new LlmStreamEvent.Error(e.getMessage()));
            throw e;
        } catch (Exception e) {
            String message = "LLM stream failed: " + e.getMessage();
            log.error(message, e);
            handler.onEvent(new LlmStreamEvent.Error(message));
            throw new LlmException(message, e);
        }
    }

    private HttpRequest buildHttpRequest(LlmChatRequest request) throws Exception {
        LlmRuntimeConfig config = llmSettingsService.getRuntimeConfig();
        boolean anthropic = useAnthropicMessagesApi(request);
        String body = anthropic
                ? objectMapper.writeValueAsString(AnthropicMessagesMappers.toApiBody(request, config))
                : objectMapper.writeValueAsString(OpenAiRequestMapper.toApiBody(request, config));
        String path = anthropic ? "/messages" : "/chat/completions";
        URI uri = URI.create(normalizeBaseUrl(config.baseUrl()) + path);

        return HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Accept", request.stream() ? "text/event-stream" : "application/json")
                .header("Authorization", "Bearer " + config.apiKey().trim())
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    /** CPA {@code /v1/chat/completions} rejects image/document blocks — use native Messages API. */
    private boolean useAnthropicMessagesApi(LlmChatRequest request) {
        if (!AnthropicMessagesMappers.requestHasMedia(request)) {
            return false;
        }
        return OpenAiRequestMapper.useAnthropicContentBlocks(llmSettingsService.getRuntimeConfig());
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new LlmException(
                    "LLM is not configured. Open Settings and set API Key, or set LLM_API_KEY.");
        }
    }

    private void ensureSuccess(int statusCode, String body) {
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }
        throw new LlmException(statusCode, extractErrorMessage(body));
    }

    static String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "LLM API error";
        }
        try {
            JsonNode root = new ObjectMapper().readTree(body);
            JsonNode message = root.path("error").path("message");
            if (!message.isMissingNode()) {
                return message.asText();
            }
            String friendly = friendlyEmptyAssistantMessage(root);
            if (friendly != null) {
                return friendly;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return body.length() > 500 ? body.substring(0, 500) : body;
    }

    /** CPA may return HTTP 4xx with an empty Anthropic-shaped assistant message for unsupported media. */
    static String friendlyEmptyAssistantMessage(JsonNode root) {
        if (!"message".equals(root.path("type").asText(""))) {
            return null;
        }
        JsonNode content = root.path("content");
        if (!content.isArray() || !content.isEmpty()) {
            return null;
        }
        String model = root.path("model").asText("unknown");
        JsonNode usage = root.path("usage");
        int input = usage.path("input_tokens").asInt(-1);
        int output = usage.path("output_tokens").asInt(-1);
        if (input != 0 || output != 0) {
            return null;
        }
        return "CPA 网关返回空响应（模型 " + model + " 可能无法处理消息中的图片/PDF 块）。"
                + "请在 CPA 管理台检查 Payload 规则与模型路由，或改用支持多模态的上游模型。";
    }

    public static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.openai.com/v1";
        }
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
