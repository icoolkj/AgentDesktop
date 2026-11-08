package com.agentdesktop.server.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Minimal MCP stdio client (initialize + tools/list + tools/call). */
@Component
public class McpStdioClient implements McpClient {

    private static final Logger log = LoggerFactory.getLogger(McpStdioClient.class);

    private final McpConfigLoader configLoader;
    private final ObjectMapper objectMapper;
    private final Map<String, Process> processes = new ConcurrentHashMap<>();
    private final Map<String, BufferedWriter> writers = new ConcurrentHashMap<>();
    private final Map<String, BufferedReader> readers = new ConcurrentHashMap<>();
    private final AtomicLong requestId = new AtomicLong(1);

    public McpStdioClient(McpConfigLoader configLoader, ObjectMapper objectMapper) {
        this.configLoader = configLoader;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isConnected(String serverName) {
        Process process = processes.get(serverName);
        return process != null && process.isAlive();
    }

    @Override
    public void shutdownServer(String serverName) {
        Process process = processes.remove(serverName);
        writers.remove(serverName);
        readers.remove(serverName);
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }

    @Override
    public void shutdownAll() {
        for (String name : List.copyOf(processes.keySet())) {
            shutdownServer(name);
        }
    }

    @Override
    public List<McpToolInfo> listTools(String serverName, McpServerConfig config) throws Exception {
        ensureProcess(serverName, config);
        JsonNode result = rpc(serverName, "tools/list", Map.of());
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
        ensureProcess(serverName, config);
        JsonNode result;
        try {
            result = rpc(serverName, "prompts/list", Map.of());
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
        ensureProcess(serverName, config);
        JsonNode result;
        try {
            result = rpc(serverName, "resources/list", Map.of());
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
        McpServerConfig config = configLoader.loadServers().get(serverName);
        if (config == null) {
            throw new IllegalStateException("MCP server not configured: " + serverName);
        }
        ensureProcess(serverName, config);
        JsonNode result = rpc(serverName, "prompts/get", Map.of(
                "name", promptName,
                "arguments", arguments == null ? Map.of() : arguments));
        return extractMessageText(result.path("messages"));
    }

    @Override
    public String readResource(String serverName, String uri) throws Exception {
        McpServerConfig config = configLoader.loadServers().get(serverName);
        if (config == null) {
            throw new IllegalStateException("MCP server not configured: " + serverName);
        }
        ensureProcess(serverName, config);
        JsonNode result = rpc(serverName, "resources/read", Map.of("uri", uri));
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
        McpServerConfig config = configLoader.loadServers().get(serverName);
        if (config == null) {
            throw new IllegalStateException("MCP server not configured: " + serverName);
        }
        ensureProcess(serverName, config);
        JsonNode result = rpc(serverName, "tools/call", Map.of(
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

    private void ensureProcess(String serverName, McpServerConfig config) throws Exception {
        if (processes.containsKey(serverName) && processes.get(serverName).isAlive()) {
            return;
        }

        shutdownServer(serverName);

        List<String> cmd = new ArrayList<>();
        cmd.add(config.command());
        if (config.args() != null) {
            cmd.addAll(config.args());
        }

        ProcessBuilder builder = new ProcessBuilder(cmd);
        if (config.cwd() != null && !config.cwd().isBlank()) {
            builder.directory(Path.of(config.cwd()).toFile());
        }
        Map<String, String> env = builder.environment();
        if (config.env() != null) {
            env.putAll(config.env());
        }

        Process process = builder.start();
        processes.put(serverName, process);
        writers.put(serverName, new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)));
        readers.put(serverName, new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)));

        rpc(serverName, "initialize", Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "agent-desktop", "version", "0.1.0", "website", "https://agentdesktop.cn")));
        rpc(serverName, "notifications/initialized", Map.of());
    }

    private JsonNode rpc(String serverName, String method, Map<String, Object> params) throws Exception {
        BufferedWriter writer = writers.get(serverName);
        BufferedReader reader = readers.get(serverName);
        if (writer == null || reader == null) {
            throw new IllegalStateException("MCP process not started: " + serverName);
        }

        long id = requestId.getAndIncrement();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.put("params", params);

        synchronized (writer) {
            writer.write(objectMapper.writeValueAsString(request));
            writer.write('\n');
            writer.flush();
        }

        String line = reader.readLine();
        if (line == null) {
            throw new IllegalStateException("MCP server closed stdout");
        }
        JsonNode response = objectMapper.readTree(line);
        if (response.has("error")) {
            throw new IllegalStateException(response.path("error").path("message").asText("MCP error"));
        }
        return response.path("result");
    }
}
