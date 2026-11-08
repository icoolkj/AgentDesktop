package com.agentdesktop.server.agent.tool.builtin;

import com.agentdesktop.server.agent.llm.LlmToolDefinition;
import com.agentdesktop.server.agent.tool.Tool;
import com.agentdesktop.server.agent.tool.ToolContext;
import com.agentdesktop.server.agent.tool.ToolResult;
import com.agentdesktop.server.agent.tool.WorkspacePaths;
import com.agentdesktop.server.agent.tool.WorkspaceScan;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Component
public class GlobTool implements Tool {

    private static final int DEFAULT_LIMIT = 200;

    private final ObjectMapper objectMapper;

    public GlobTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "glob";
    }

    @Override
    public String description() {
        return "Find files by glob pattern under the workspace (e.g. **/*.java, src/**/*.ts).";
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
                                "pattern", Map.of(
                                        "type", "string",
                                        "description", "Glob pattern (e.g. **/*.java)"
                                ),
                                "path", Map.of(
                                        "type", "string",
                                        "description", "Directory to search under (default: workspace root)"
                                ),
                                "limit", Map.of(
                                        "type", "integer",
                                        "description", "Max files to return (default " + DEFAULT_LIMIT + ")"
                                )
                        ),
                        "required", List.of("pattern")
                )
        );
    }

    @Override
    public ToolResult execute(String argumentsJson, ToolContext context) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
            String pattern = args.path("pattern").asText("");
            if (pattern.isBlank()) {
                return ToolResult.failure("Missing required argument: pattern");
            }
            String pathArg = args.path("path").asText(".");
            int limit = args.has("limit") ? args.path("limit").asInt(DEFAULT_LIMIT) : DEFAULT_LIMIT;

            Path searchRoot = WorkspacePaths.resolve(context, pathArg);
            if (!Files.exists(searchRoot)) {
                return ToolResult.failure("Path not found: " + pathArg);
            }
            if (!Files.isDirectory(searchRoot)) {
                return ToolResult.failure("Not a directory: " + pathArg);
            }

            Path workspace = context.workspacePath().toAbsolutePath().normalize();
            List<Path> matches = WorkspaceScan.glob(workspace, searchRoot, pattern, limit);

            if (matches.isEmpty()) {
                return ToolResult.success("No files matched pattern: " + pattern);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Matched ").append(matches.size()).append(" file(s) for `").append(pattern).append("`:\n");
            for (Path file : matches) {
                sb.append("- ").append(workspace.relativize(file.toAbsolutePath().normalize())).append('\n');
            }
            if (matches.size() >= limit) {
                sb.append("\n(limit reached; refine pattern or increase limit)");
            }
            return ToolResult.success(sb.toString());
        } catch (Exception e) {
            return ToolResult.failure(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }
}
