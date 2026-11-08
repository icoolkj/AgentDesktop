package com.agentdesktop.server.agent.tool.builtin;

import com.agentdesktop.server.agent.llm.LlmToolDefinition;
import com.agentdesktop.server.agent.tool.Tool;
import com.agentdesktop.server.agent.tool.ToolContext;
import com.agentdesktop.server.agent.tool.ToolResult;
import com.agentdesktop.server.agent.tool.WorkspacePaths;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ListDirTool implements Tool {

    private final ObjectMapper objectMapper;

    public ListDirTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "list_dir";
    }

    @Override
    public String description() {
        return "List files and directories in a workspace folder (non-recursive).";
    }

    @Override
    public boolean concurrencySafe() {
        return true;
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
                                        "description", "Directory path relative to workspace root (default: .)"
                                )
                        )
                )
        );
    }

    @Override
    public ToolResult execute(String argumentsJson, ToolContext context) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
            String pathArg = args.path("path").asText(".");
            Path dir = WorkspacePaths.resolve(context, pathArg);
            if (!Files.exists(dir)) {
                return ToolResult.failure("Directory not found: " + pathArg);
            }
            if (!Files.isDirectory(dir)) {
                return ToolResult.failure("Not a directory: " + pathArg);
            }

            String listing;
            try (var stream = Files.list(dir)) {
                listing = stream
                        .sorted((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()))
                        .map(p -> (Files.isDirectory(p) ? "[dir]  " : "[file] ") + p.getFileName())
                        .collect(Collectors.joining("\n"));
            }
            if (listing.isBlank()) {
                listing = "(empty directory)";
            }
            return ToolResult.success(listing);
        } catch (Exception e) {
            return ToolResult.failure(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }
}
