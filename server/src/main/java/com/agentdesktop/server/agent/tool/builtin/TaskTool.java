package com.agentdesktop.server.agent.tool.builtin;

import com.agentdesktop.server.agent.extension.SubagentTypes;
import com.agentdesktop.server.agent.llm.LlmToolDefinition;
import com.agentdesktop.server.agent.tool.Tool;
import com.agentdesktop.server.agent.tool.ToolContext;
import com.agentdesktop.server.agent.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TaskTool implements Tool {

    private final ObjectMapper objectMapper;
    private final SubagentRunner subagentRunner;

    public TaskTool(ObjectMapper objectMapper, SubagentRunner subagentRunner) {
        this.objectMapper = objectMapper;
        this.subagentRunner = subagentRunner;
    }

    @Override
    public String name() {
        return "Task";
    }

    @Override
    public String description() {
        return """
                Launch a focused sub-agent to handle a complex sub-task autonomously.
                Use subagent_type to pick explore (read-only), shell (terminal), or generalPurpose.
                """;
    }

    @Override
    public boolean concurrencySafe() {
        return false;
    }

    @Override
    public boolean available(ToolContext context) {
        var features = context.featuresOrDefault();
        return features.subagentsEnabled() && context.subagentDepth() < features.maxSubagentDepth();
    }

    @Override
    public LlmToolDefinition definition() {
        return new LlmToolDefinition(
                name(),
                description(),
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "description", Map.of(
                                        "type", "string",
                                        "description", "Short title for the sub-task (3-5 words)."),
                                "prompt", Map.of(
                                        "type", "string",
                                        "description", "Detailed instructions for the sub-agent."),
                                "subagent_type", Map.of(
                                        "type", "string",
                                        "description", "Sub-agent profile: generalPurpose, explore, or shell.",
                                        "enum", List.of(
                                                SubagentTypes.GENERAL,
                                                SubagentTypes.EXPLORE,
                                                SubagentTypes.SHELL))),
                        "required", List.of("description", "prompt")));
    }

    @Override
    public ToolResult execute(String argumentsJson, ToolContext context) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson == null ? "{}" : argumentsJson);
            String description = args.path("description").asText("");
            String prompt = args.path("prompt").asText("");
            String subagentType = SubagentTypes.normalize(args.path("subagent_type").asText(""));
            String result = subagentRunner.run(prompt, description, subagentType, context);
            return ToolResult.success(result);
        } catch (Exception e) {
            return ToolResult.failure(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }
}
