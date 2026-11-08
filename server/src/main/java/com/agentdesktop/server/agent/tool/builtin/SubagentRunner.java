package com.agentdesktop.server.agent.tool.builtin;

import com.agentdesktop.server.agent.extension.HookContext;
import com.agentdesktop.server.agent.extension.HookEvents;
import com.agentdesktop.server.agent.extension.HookRunner;
import com.agentdesktop.server.agent.extension.SubagentTypes;
import com.agentdesktop.server.agent.llm.LlmMessage;
import com.agentdesktop.server.agent.loop.ActiveAgentRun;
import com.agentdesktop.server.agent.loop.AgentLoopService;
import com.agentdesktop.server.agent.loop.AgentRunContext;
import com.agentdesktop.server.agent.tool.ToolContext;
import com.agentdesktop.server.ws.NdjsonEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.UUID;

@Component
public class SubagentRunner {

    private final AgentLoopService agentLoopService;
    private final ActiveAgentRun activeAgentRun;
    private final NdjsonEventPublisher events;
    private final HookRunner hookRunner;

    public SubagentRunner(
            @Lazy AgentLoopService agentLoopService,
            ActiveAgentRun activeAgentRun,
            NdjsonEventPublisher events,
            HookRunner hookRunner) {
        this.agentLoopService = agentLoopService;
        this.activeAgentRun = activeAgentRun;
        this.events = events;
        this.hookRunner = hookRunner;
    }

    public String run(String prompt, String description, String subagentType, ToolContext parentContext) {
        var features = parentContext.featuresOrDefault();
        if (!features.subagentsEnabled()) {
            return "Subagents are disabled in feature settings.";
        }
        if (parentContext.subagentDepth() >= features.maxSubagentDepth()) {
            return "Maximum subagent depth (" + features.maxSubagentDepth() + ") reached.";
        }
        if (prompt == null || prompt.isBlank()) {
            return "Subagent prompt is empty.";
        }

        String normalizedType = SubagentTypes.normalize(subagentType);
        int childDepth = parentContext.subagentDepth() + 1;
        ToolContext childContext = parentContext.withSubagentProfile(normalizedType, childDepth);

        AgentRunContext run = activeAgentRun.get();
        if (run == null) {
            run = new AgentRunContext("subagent");
        }
        WebSocketSession session = activeAgentRun.session();

        String subagentId = UUID.randomUUID().toString().substring(0, 8);
        String header = description == null || description.isBlank()
                ? "Focused sub-agent task"
                : description.trim();

        events.subagentStart(session, subagentId, normalizedType, header, childDepth);

        List<LlmMessage> messages = List.of(
                LlmMessage.system(systemPromptForType(normalizedType)),
                LlmMessage.user(header + "\n\n" + prompt.trim()));

        String result;
        boolean ok;
        try {
            result = agentLoopService.runSubagent(session, messages, childContext, run, features.maxSubagentTurns());
            ok = result != null
                    && !result.startsWith("Subagent cancelled.")
                    && !result.startsWith("Subagent failed:")
                    && !result.startsWith("Subagent unavailable:");
        } catch (Exception e) {
            result = "Subagent failed: " + (e.getMessage() == null ? e.toString() : e.getMessage());
            ok = false;
        }

        events.subagentDone(session, subagentId, normalizedType, ok, truncate(result, 500));

        hookRunner.runEvent(
                parentContext.extensionBundleOrEmpty().hooks(),
                HookEvents.SUBAGENT_STOP,
                HookContext.forSubagentStop(
                        normalizedType,
                        childDepth,
                        result,
                        parentContext.workspaceRoot(),
                        parentContext.sessionId()));

        return result;
    }

    private static String systemPromptForType(String type) {
        if (SubagentTypes.EXPLORE.equals(type)) {
            return """
                    You are a read-only exploration sub-agent inside Agent Desktop.
                    Search and read the codebase; do not modify files or run destructive shell commands.
                    Return a concise Markdown summary of findings.
                    """;
        }
        if (SubagentTypes.SHELL.equals(type)) {
            return """
                    You are a shell/terminal sub-agent inside Agent Desktop.
                    Prefer run_terminal_cmd for git, builds, tests, and package managers.
                    Return a concise Markdown summary including key command output.
                    """;
        }
        return """
                You are a focused sub-agent working inside Agent Desktop.
                Complete the assigned task using tools, then return a concise Markdown summary.
                Do not spawn further sub-agents unless absolutely necessary.
                """;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "…";
    }
}
