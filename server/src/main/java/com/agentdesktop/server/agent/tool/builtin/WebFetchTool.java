package com.agentdesktop.server.agent.tool.builtin;

import com.agentdesktop.server.agent.llm.LlmToolDefinition;
import com.agentdesktop.server.agent.tool.Tool;
import com.agentdesktop.server.agent.tool.ToolContext;
import com.agentdesktop.server.agent.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class WebFetchTool implements Tool {

    private static final Pattern TAGS = Pattern.compile("<[^>]+>");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public WebFetchTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "WebFetch";
    }

    @Override
    public String description() {
        return "Fetch a public HTTP/HTTPS URL and return readable text content.";
    }

    @Override
    public boolean concurrencySafe() {
        return true;
    }

    @Override
    public boolean available(ToolContext context) {
        return context.featuresOrDefault().webFetchEnabled();
    }

    @Override
    public LlmToolDefinition definition() {
        return new LlmToolDefinition(
                name(),
                description(),
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "url", Map.of("type", "string", "description", "HTTP or HTTPS URL to fetch.")),
                        "required", List.of("url")));
    }

    @Override
    public ToolResult execute(String argumentsJson, ToolContext context) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson == null ? "{}" : argumentsJson);
            String url = args.path("url").asText("").trim();
            if (url.isBlank()) {
                return ToolResult.failure("url is required");
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return ToolResult.failure("Only http/https URLs are supported");
            }

            int maxBytes = context.featuresOrDefault().webFetchMaxBytes();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "AgentDesktop/1.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                return ToolResult.failure("HTTP " + response.statusCode());
            }
            byte[] body = response.body();
            if (body.length > maxBytes) {
                byte[] truncated = new byte[maxBytes];
                System.arraycopy(body, 0, truncated, 0, maxBytes);
                body = truncated;
            }
            String text = new String(body, StandardCharsets.UTF_8);
            text = TAGS.matcher(text).replaceAll(" ");
            text = text.replaceAll("\\s+", " ").trim();
            return ToolResult.success(text);
        } catch (Exception e) {
            return ToolResult.failure(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }
}
