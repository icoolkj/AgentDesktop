package com.agentdesktop.server.agent.tool.builtin;

import com.agentdesktop.server.agent.llm.LlmToolDefinition;
import com.agentdesktop.server.agent.tool.Tool;
import com.agentdesktop.server.agent.tool.ToolContext;
import com.agentdesktop.server.agent.tool.ToolResult;
import com.agentdesktop.server.agent.tool.WorkspacePaths;
import com.agentdesktop.server.agent.tool.WorkspaceTextFiles;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SearchReplaceTool implements Tool {

    private final ObjectMapper objectMapper;

    public SearchReplaceTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "search_replace";
    }

    @Override
    public String description() {
        return """
                Replace exact text in a workspace file. Prefer this over write_file for small edits.
                old_string must match exactly (including whitespace). Use replace_all to change every occurrence.
                """;
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
                                "old_string", Map.of(
                                        "type", "string",
                                        "description", "Exact text to find"
                                ),
                                "new_string", Map.of(
                                        "type", "string",
                                        "description", "Replacement text"
                                ),
                                "replace_all", Map.of(
                                        "type", "boolean",
                                        "description", "Replace all occurrences (default false — must be unique)"
                                )
                        ),
                        "required", List.of("path", "old_string", "new_string")
                )
        );
    }

    @Override
    public ToolResult execute(String argumentsJson, ToolContext context) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
            String pathArg = args.path("path").asText("");
            String oldString = args.path("old_string").asText("");
            String newString = args.path("new_string").asText(null);
            if (newString == null) {
                newString = "";
            }
            boolean replaceAll = args.path("replace_all").asBoolean(false);

            if (pathArg.isBlank()) {
                return ToolResult.failure("Missing required argument: path");
            }
            if (oldString.isEmpty()) {
                return ToolResult.failure("old_string must not be empty");
            }

            Path file = WorkspacePaths.resolve(context, pathArg);
            String content = WorkspaceTextFiles.readString(file, context);

            int count = countOccurrences(content, oldString);
            if (count == 0) {
                return ToolResult.failure("old_string not found in " + pathArg);
            }
            if (!replaceAll && count > 1) {
                return ToolResult.failure(
                        "old_string appears " + count + " times; use replace_all=true or provide more context");
            }

            String updated = replaceAll
                    ? content.replace(oldString, newString)
                    : content.replaceFirst(Pattern.quote(oldString), Matcher.quoteReplacement(newString));

            int replaced = replaceAll ? count : 1;

            Files.writeString(file, updated, StandardCharsets.UTF_8);
            return ToolResult.success("Replaced " + replaced + " occurrence(s) in " + pathArg);
        } catch (Exception e) {
            return ToolResult.failure(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
