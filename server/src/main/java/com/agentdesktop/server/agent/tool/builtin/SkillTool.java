package com.agentdesktop.server.agent.tool.builtin;

import com.agentdesktop.server.agent.extension.SkillCatalog;
import com.agentdesktop.server.agent.extension.SkillContentLoader;
import com.agentdesktop.server.agent.extension.SkillEntry;
import com.agentdesktop.server.agent.extension.SkillMessages;
import com.agentdesktop.server.agent.extension.SessionSkillState;
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
public class SkillTool implements Tool {

    public static final String TOOL_NAME = "Skill";

    private final ObjectMapper objectMapper;
    private final SkillContentLoader skillContentLoader;
    private final SessionSkillState sessionSkillState;
    private final SubagentRunner subagentRunner;

    public SkillTool(
            ObjectMapper objectMapper,
            SkillContentLoader skillContentLoader,
            SessionSkillState sessionSkillState,
            SubagentRunner subagentRunner) {
        this.objectMapper = objectMapper;
        this.skillContentLoader = skillContentLoader;
        this.sessionSkillState = sessionSkillState;
        this.subagentRunner = subagentRunner;
    }

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return """
                Execute a skill within the main conversation.
                Skills provide specialized capabilities and domain knowledge.
                When users reference a slash command or /skill-name, invoke the matching skill here.
                Available skills are listed in system-reminder messages on the user turn.
                When a skill matches the task, call this tool BEFORE generating other work about the task.
                Do not invoke a skill that is already loaded in the current turn.
                Examples: skill="pdf" or skill="commit", args="-m 'Fix bug'".
                """;
    }

    @Override
    public boolean concurrencySafe() {
        return true;
    }

    @Override
    public boolean available(ToolContext context) {
        return context.hasWorkspace()
                && !SkillCatalog.modelInvocableSkills(context.extensionBundleOrEmpty().skills()).isEmpty();
    }

    @Override
    public LlmToolDefinition definition() {
        return new LlmToolDefinition(
                name(),
                description(),
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "skill", Map.of(
                                        "type", "string",
                                        "description", "Skill name, e.g. agent-desktop-dev"),
                                "args", Map.of(
                                        "type", "string",
                                        "description", "Optional arguments for the skill")),
                        "required", List.of("skill")));
    }

    @Override
    public ToolResult execute(String argumentsJson, ToolContext context) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson == null ? "{}" : argumentsJson);
            String skillName = args.path("skill").asText("").trim();
            String skillArgs = args.path("args").asText("");
            if (skillName.isBlank()) {
                return ToolResult.failure("Missing required parameter: skill");
            }

            SkillEntry entry = SkillCatalog.findByName(context.extensionBundleOrEmpty().skills(), skillName);
            if (entry == null) {
                return ToolResult.failure("Unknown skill: " + skillName);
            }
            if (!entry.modelInvocable()) {
                return ToolResult.failure("Skill '" + entry.name() + "' is not model-invocable.");
            }

            String body = skillContentLoader.loadForInvocation(entry, skillArgs);
            String skillPath = resolveSkillPath(entry);
            sessionSkillState.recordInvoked(context.sessionId(), entry.name(), skillPath, body);
            sessionSkillState.setActiveAllowedTools(context.sessionId(), entry.allowedTools());

            if (entry.forkContext()) {
                String agentType = entry.agentType() == null || entry.agentType().isBlank()
                        ? SubagentTypes.GENERAL
                        : SubagentTypes.normalize(entry.agentType());
                String summary = subagentRunner.run(body, "Skill: " + entry.name(), agentType, context);
                return ToolResult.success("Skill fork completed: " + entry.name() + "\n\n" + summary);
            }

            String wrapped = SkillMessages.wrapSystemReminder(body);
            if (!entry.allowedTools().isEmpty()) {
                wrapped = wrapped + "\n\nAllowed tools for this skill: " + String.join(", ", entry.allowedTools());
            }
            return ToolResult.successWithFollowUp("Launching skill: " + entry.name(), wrapped);
        } catch (Exception e) {
            return ToolResult.failure("Skill failed: " + e.getMessage());
        }
    }

    private static String resolveSkillPath(SkillEntry entry) {
        if (entry.skillRoot() != null) {
            return entry.skillRoot().toString();
        }
        if (entry.skillFile() != null) {
            return entry.skillFile().toString();
        }
        if (entry.isBundledInBinary()) {
            return "bundled:" + entry.bundledCatalogId();
        }
        if (entry.isMcpSkill()) {
            return "mcp:" + entry.name();
        }
        return entry.name();
    }
}
