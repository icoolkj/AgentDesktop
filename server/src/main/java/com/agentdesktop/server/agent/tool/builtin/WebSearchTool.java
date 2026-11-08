package com.agentdesktop.server.agent.tool.builtin;

import com.agentdesktop.server.agent.llm.LlmToolDefinition;
import com.agentdesktop.server.agent.tool.Tool;
import com.agentdesktop.server.agent.tool.ToolContext;
import com.agentdesktop.server.agent.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
public class WebSearchTool implements Tool {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public WebSearchTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "WebSearch";
    }

    @Override
    public String description() {
        return "Search the public web for up-to-date information.";
    }

    @Override
    public boolean concurrencySafe() {
        return true;
    }

    @Override
    public boolean available(ToolContext context) {
        return context.featuresOrDefault().webSearchEnabled();
    }

    @Override
    public LlmToolDefinition definition() {
        return new LlmToolDefinition(
                name(),
                description(),
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "search_term", Map.of(
                                        "type", "string",
                                        "description", "Specific search query.")),
                        "required", List.of("search_term")));
    }

    @Override
    public ToolResult execute(String argumentsJson, ToolContext context) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson == null ? "{}" : argumentsJson);
            String term = args.path("search_term").asText("").trim();
            if (term.isBlank()) {
                return ToolResult.failure("search_term is required");
            }
            String provider = context.featuresOrDefault().webSearchProvider();
            if ("duckduckgo".equalsIgnoreCase(provider)) {
                return ToolResult.success(searchDuckDuckGo(term));
            }
            return ToolResult.failure("Unknown webSearchProvider: " + provider);
        } catch (Exception e) {
            return ToolResult.failure(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private String searchDuckDuckGo(String term) throws Exception {
        String encoded = URLEncoder.encode(term, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.duckduckgo.com/?q=" + encoded + "&format=json&no_html=1"))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            return "Search failed: HTTP " + response.statusCode();
        }
        JsonNode root = objectMapper.readTree(response.body());
        StringBuilder sb = new StringBuilder();
        sb.append("Query: ").append(term).append('\n');
        if (!root.path("AbstractText").asText("").isBlank()) {
            sb.append("\nAbstract: ").append(root.path("AbstractText").asText()).append('\n');
            if (!root.path("AbstractURL").asText("").isBlank()) {
                sb.append("Source: ").append(root.path("AbstractURL").asText()).append('\n');
            }
        }
        JsonNode topics = root.path("RelatedTopics");
        if (topics.isArray() && !topics.isEmpty()) {
            sb.append("\nRelated:\n");
            String related = StreamSupport.stream(topics.spliterator(), false)
                    .limit(8)
                    .map(node -> node.path("Text").asText(""))
                    .filter(text -> !text.isBlank())
                    .collect(Collectors.joining("\n"));
            sb.append(related);
        }
        if (sb.length() <= term.length() + 10) {
            sb.append("\nNo instant answers — try WebFetch on a specific URL.");
        }
        return sb.toString().trim();
    }
}
