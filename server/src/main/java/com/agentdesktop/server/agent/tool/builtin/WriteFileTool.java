package com.agentdesktop.server.agent.tool.builtin;

import com.agentdesktop.server.agent.llm.LlmToolDefinition;
import com.agentdesktop.server.agent.tool.Tool;
import com.agentdesktop.server.agent.tool.ToolContext;
import com.agentdesktop.server.agent.tool.ToolResult;
import com.agentdesktop.server.agent.tool.WorkspacePaths;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Component
public class WriteFileTool implements Tool {

    private static final int MAX_BYTES = 512 * 1024;

    private final ObjectMapper objectMapper;

    public WriteFileTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "write_file";
    }

    @Override
    public String description() {
        return "Write UTF-8 text content to a file in the workspace. Creates parent directories if needed.";
    }

    @Override
    public LlmToolDefinition definition() {
        return new LlmToolDefinition(
                name(),
                description(),
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "path", Map.of(
                                        "type", "string",
                                        "description", "File path relative to workspace root"
                                ),
                                "content", Map.of(
                                        "type", "string",
                                        "description", "Full file content to write"
                                )
                        ),
                        "required", java.util.List.of("path", "content")
                )
        );
    }

    @Override
    public ToolResult execute(String argumentsJson, ToolContext context) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
            String pathArg = args.path("path").asText("");
            String content = args.path("content").asText("");
            if (pathArg.isBlank()) {
                return ToolResult.failure("Missing required argument: path");
            }
            if (content.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
                return ToolResult.failure("Content too large. Max " + MAX_BYTES + " bytes.");
            }

            Path file = WorkspacePaths.resolve(context, pathArg);
            String previous = Files.isRegularFile(file)
                    ? Files.readString(file, StandardCharsets.UTF_8)
                    : null;
            String changeKind = previous == null ? "created" : "modified";

            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, content, StandardCharsets.UTF_8);
            return ToolResult.success("Wrote " + pathArg + " (" + content.length() + " chars, " + changeKind + ")");
        } catch (Exception e) {
            return ToolResult.failure(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }
}
