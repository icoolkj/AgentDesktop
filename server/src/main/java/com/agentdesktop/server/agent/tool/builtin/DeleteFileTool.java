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

@Component
public class DeleteFileTool implements Tool {

    private final ObjectMapper objectMapper;

    public DeleteFileTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "delete_file";
    }

    @Override
    public String description() {
        return "Delete a file in the workspace. Fails if the path is not a regular file.";
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
                                )
                        ),
                        "required", java.util.List.of("path")
                )
        );
    }

    @Override
    public ToolResult execute(String argumentsJson, ToolContext context) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
            String pathArg = args.path("path").asText("");
            if (pathArg.isBlank()) {
                return ToolResult.failure("Missing required argument: path");
            }

            Path file = WorkspacePaths.resolve(context, pathArg);
            if (!Files.isRegularFile(file)) {
                return ToolResult.failure("File not found or not a regular file: " + pathArg);
            }
            Files.delete(file);
            return ToolResult.success("Deleted " + pathArg);
        } catch (Exception e) {
            return ToolResult.failure(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }
}
