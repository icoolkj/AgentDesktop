package com.agentdesktop.server.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP over HTTP/SSE URL — JSON-RPC POST to {@link McpServerConfig#url()}.
 * Supports remote MCP servers without stdio subprocesses.
 */
@Component
public class McpHttpClient implements McpClient {

    private static final Logger log = LoggerFactory.getLogger(McpHttpClient.class);

    private final ObjectMapper objectMapper;
    private final McpConfigLoader configLoader;
    private final HttpClient httpClient;
    private final Map<String, String> sessionIds = new ConcurrentHashMap<>();
    private final Map<String, McpServerConfig> configs = new ConcurrentHashMap<>();
    private final AtomicLong requestId = new AtomicLong(1);

    public McpHttpClient(ObjectMapper objectMapper, McpConfigLoader configLoader) {
        this.objectMapper = objectMapper;
        this.configLoader = configLoader;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    @Override
    public boolean isConnected(String serverName) {
        return sessionIds.containsKey(serverName);
    }

    @Override
    public void shutdownServer(String serverName) {
        sessionIds.remove(serverName);
        configs.remove(serverName);
    }

    @Override
    public void shutdownAll() {
        sessionIds.clear();
        configs.clear();
    }

    @Override
    public List<McpToolInfo> listTools(String serverName, McpServerConfig config) throws Exception {
        ensureInitialized(serverName, config);
        JsonNode result = rpc(serverName, config, "tools/list", Map.of());
        List<McpToolInfo> tools = new ArrayList<>();
        JsonNode list = result.path("tools");
        if (list.isArray()) {
            for (JsonNode node : list) {
                Map<String, Object> schema = Map.of();
                JsonNode inputSchema = node.path("inputSchema");
                if (inputSchema.isObject()) {
                    schema = objectMapper.convertValue(inputSchema, Map.class);
                }
                tools.add(new McpToolInfo(
                        node.path("name").asText(""),
                        node.path("description").asText(""),
                        schema));
            }
        }
        return tools;
    }

    @Override
    public List<McpPromptInfo> listPrompts(String serverName, McpServerConfig config) throws Exception {
        ensureInitialized(serverName, config);
        JsonNode result;
        try {
            result = rpc(serverName, config, "prompts/list", Map.of());
        } catch (Exception e) {
            return List.of();
        }
        List<McpPromptInfo> prompts = new ArrayList<>();
        JsonNode list = result.path("prompts");
        if (list.isArray()) {
            for (JsonNode node : list) {
                List<String> argNames = new ArrayList<>();
                JsonNode args = node.path("arguments");
                if (args.isArray()) {
                    for (JsonNode arg : args) {
                        argNames.add(arg.path("name").asText(""));
                    }
                }
                prompts.add(new McpPromptInfo(
                        node.path("name").asText(""),
                        node.path("description").asText(""),
                        List.copyOf(argNames)));
            }
        }
        return prompts;
    }

    @Override
    public List<McpResourceInfo> listResources(String serverName, McpServerConfig config) throws Exception {
        ensureInitialized(serverName, config);
        JsonNode result;
        try {
            result = rpc(serverName, config, "resources/list", Map.of());
        } catch (Exception e) {
            return List.of();
        }
        List<McpResourceInfo> resources = new ArrayList<>();
        JsonNode list = result.path("resources");
        if (list.isArray()) {
            for (JsonNode node : list) {
                resources.add(new McpResourceInfo(
                        node.path("uri").asText(""),
                        node.path("name").asText(""),
                        node.path("description").asText(""),
                        node.path("mimeType").asText("")));
            }
        }
        return resources;
    }

    @Override
    public String getPrompt(String serverName, String promptName, Map<String, String> arguments) throws Exception {
        McpServerConfig config = configs.get(serverName);
        if (config == null) {
            config = configLoader.loadServers().get(serverName);
        }
        if (config == null) {
            throw new IllegalStateException("MCP server not configured: " + serverName);
        }
        ensureInitialized(serverName, config);
        JsonNode result = rpc(serverName, config, "prompts/get", Map.of(
                "name", promptName,
                "arguments", arguments == null ? Map.of() : arguments));
        return extractMessageText(result.path("messages"));
    }

    @Override
    public String readResource(String serverName, String uri) throws Exception {
        McpServerConfig config = configs.get(serverName);
        if (config == null) {
            config = configLoader.loadServers().get(serverName);
        }
        if (config == null) {
            throw new IllegalStateException("MCP server not configured: " + serverName);
        }
        ensureInitialized(serverName, config);
        JsonNode result = rpc(serverName, config, "resources/read", Map.of("uri", uri));
        JsonNode contents = result.path("contents");
        if (contents.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode block : contents) {
                if (block.has("text")) {
                    sb.append(block.path("text").asText()).append('\n');
                }
            }
            return sb.toString().trim();
        }
        return result.toString();
    }

    private static String extractMessageText(JsonNode messages) {
        if (!messages.isArray()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode message : messages) {
            JsonNode content = message.path("content");
            if (content.isTextual()) {
                sb.append(content.asText()).append('\n');
            } else if (content.isObject() && "text".equals(content.path("type").asText())) {
                sb.append(content.path("text").asText()).append('\n');
            } else if (content.isArray()) {
                for (JsonNode block : content) {
                    if ("text".equals(block.path("type").asText())) {
                        sb.append(block.path("text").asText()).append('\n');
                    }
                }
            }
        }
        return sb.toString().trim();
    }

    @Override
    public String callTool(String serverName, String toolName, Map<String, Object> arguments) throws Exception {
        McpServerConfig config = configs.get(serverName);
        if (config == null) {
            config = configLoader.loadServers().get(serverName);
        }
        if (config == null) {
            throw new IllegalStateException("MCP server not configured: " + serverName);
        }
        ensureInitialized(serverName, config);
        JsonNode result = rpc(serverName, config, "tools/call", Map.of(
                "name", toolName,
                "arguments", arguments == null ? Map.of() : arguments));
        JsonNode content = result.path("content");
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText())) {
                    sb.append(block.path("text").asText()).append('\n');
                }
            }
            return sb.toString().trim();
        }
        return result.toString();
    }

    private void ensureInitialized(String serverName, McpServerConfig config) throws Exception {
        configs.put(serverName, config);
        if (sessionIds.containsKey(serverName)) {
            return;
        }
        rpc(serverName, config, "initialize", Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "agent-desktop", "version", "0.1.0")));
        rpc(serverName, config, "notifications/initialized", Map.of());
    }

    private JsonNode rpc(String serverName, McpServerConfig config, String method, Map<String, Object> params)
            throws Exception {
        if (config.url() == null || config.url().isBlank()) {
            throw new IllegalStateException("MCP server missing url: " + serverName);
        }

        long id = requestId.getAndIncrement();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.put("params", params);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.url()))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)));

        if (config.headers() != null) {
            config.headers().forEach(builder::header);
        }
        String sessionId = sessionIds.get(serverName);
        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.headers().firstValue("Mcp-Session-Id").isPresent()) {
            sessionIds.put(serverName, response.headers().firstValue("Mcp-Session-Id").get());
        }

        if (response.statusCode() >= 400) {
            throw new IllegalStateException("MCP HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonNode body = objectMapper.readTree(response.body() == null ? "{}" : response.body());
        if (body.has("error")) {
            throw new IllegalStateException(body.path("error").path("message").asText("MCP error"));
        }
        return body.path("result");
    }
}
