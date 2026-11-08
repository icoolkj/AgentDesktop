package com.agentdesktop.server.agent.extension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Claude Code hooks runtime — all standard hook events via command hooks.
 */
@Component
public class HookRunner {

    private static final Logger log = LoggerFactory.getLogger(HookRunner.class);

    private final ObjectMapper objectMapper;

    public HookRunner(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public HookOutcome runEvent(List<HookDefinition> hooks, String eventName, HookContext context) {
        if (hooks == null || hooks.isEmpty() || eventName == null) {
            return HookOutcome.allow();
        }
        String matchTarget = resolveMatcherTarget(eventName, context);
        for (HookDefinition rule : hooks) {
            if (!eventName.equalsIgnoreCase(rule.event())) {
                continue;
            }
            if (!matches(rule.matcher(), matchTarget)) {
                continue;
            }
            for (HookDefinition.HookAction action : rule.actions()) {
                HookOutcome outcome = runCommand(action, context);
                if (!outcome.allowed()) {
                    return outcome;
                }
                if (outcome.updatedUserPrompt() != null && !outcome.updatedUserPrompt().isBlank()) {
                    return outcome;
                }
            }
        }
        return HookOutcome.allow();
    }

    /** @deprecated use {@link #runEvent(List, String, HookContext)} */
    public HookOutcome runPreToolUse(
            List<HookDefinition> hooks,
            String toolName,
            String argsJson,
            String workspaceRoot) {
        return runEvent(
                hooks,
                HookEvents.PRE_TOOL_USE,
                HookContext.forTool(HookEvents.PRE_TOOL_USE, toolName, argsJson, null, true, workspaceRoot, null));
    }

    private static String resolveMatcherTarget(String eventName, HookContext context) {
        if (HookEvents.USER_PROMPT_SUBMIT.equalsIgnoreCase(eventName)) {
            return "prompt";
        }
        if (HookEvents.PRE_TOOL_USE.equalsIgnoreCase(eventName)
                || HookEvents.POST_TOOL_USE.equalsIgnoreCase(eventName)) {
            return context.toolName() == null ? "" : context.toolName();
        }
        if (HookEvents.SUBAGENT_STOP.equalsIgnoreCase(eventName)) {
            return context.subagentType() == null ? "subagent" : context.subagentType();
        }
        return ".*";
    }

    private HookOutcome runCommand(HookDefinition.HookAction action, HookContext context) {
        try {
            ProcessBuilder builder = isWindows()
                    ? new ProcessBuilder("cmd.exe", "/c", action.command())
                    : new ProcessBuilder("sh", "-c", action.command());
            if (context.workspaceRoot() != null && !context.workspaceRoot().isBlank()) {
                builder.directory(java.nio.file.Path.of(context.workspaceRoot()).toFile());
            }
            Map<String, String> env = builder.environment();
            env.put("AGENT_HOOK_EVENT", context.event() == null ? "" : context.event());
            env.put("AGENT_TOOL_NAME", context.toolName() == null ? "" : context.toolName());
            env.put("AGENT_TOOL_INPUT", context.toolInputJson() == null ? "{}" : context.toolInputJson());
            env.put("AGENT_TOOL_OUTPUT", context.toolOutput() == null ? "" : context.toolOutput());
            env.put("AGENT_TOOL_OK", context.toolOk() == null ? "" : String.valueOf(context.toolOk()));
            env.put("AGENT_USER_PROMPT", context.userPrompt() == null ? "" : context.userPrompt());
            env.put("AGENT_WORKSPACE", context.workspaceRoot() == null ? "" : context.workspaceRoot());
            env.put("AGENT_SESSION_ID", context.sessionId() == null ? "" : context.sessionId());
            env.put("AGENT_SUBAGENT_TYPE", context.subagentType() == null ? "" : context.subagentType());
            env.put("AGENT_SUBAGENT_DEPTH", String.valueOf(context.subagentDepth()));

            Process process = builder.start();
            boolean finished = process.waitFor(action.timeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                if (HookEvents.POST_TOOL_USE.equalsIgnoreCase(context.event())
                        || HookEvents.STOP.equalsIgnoreCase(context.event())
                        || HookEvents.SESSION_END.equalsIgnoreCase(context.event())
                        || HookEvents.SUBAGENT_STOP.equalsIgnoreCase(context.event())) {
                    log.warn("Hook timed out (non-blocking event): {}", context.event());
                    return HookOutcome.allow();
                }
                return HookOutcome.deny("Hook timed out after " + action.timeoutSeconds() + "s");
            }

            String stdout = readStream(process.getInputStream());
            HookOutcome parsed = parseHookStdout(stdout);
            if (parsed != null) {
                return parsed;
            }
            if (process.exitValue() != 0
                    && HookEvents.PRE_TOOL_USE.equalsIgnoreCase(context.event())) {
                return HookOutcome.deny("Hook exited with code " + process.exitValue());
            }
            return HookOutcome.allow();
        } catch (Exception e) {
            log.warn("Hook command failed: {}", action.command(), e);
            if (HookEvents.PRE_TOOL_USE.equalsIgnoreCase(context.event())
                    || HookEvents.USER_PROMPT_SUBMIT.equalsIgnoreCase(context.event())) {
                return HookOutcome.deny("Hook error: " + e.getMessage());
            }
            return HookOutcome.allow();
        }
    }

    private HookOutcome parseHookStdout(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            return null;
        }
        String trimmed = stdout.trim();
        if ("block".equalsIgnoreCase(trimmed) || "deny".equalsIgnoreCase(trimmed)) {
            return HookOutcome.deny("Blocked by hook");
        }
        if (trimmed.startsWith("{")) {
            try {
                JsonNode node = objectMapper.readTree(trimmed);
                String decision = node.path("decision").asText("");
                if ("block".equalsIgnoreCase(decision) || "deny".equalsIgnoreCase(decision)) {
                    return HookOutcome.deny(node.path("reason").asText("Blocked by hook"));
                }
                if (node.has("updatedPrompt") || node.has("prompt")) {
                    String updated = node.has("updatedPrompt")
                            ? node.path("updatedPrompt").asText("")
                            : node.path("prompt").asText("");
                    if (!updated.isBlank()) {
                        return HookOutcome.allowWithPrompt(updated.trim());
                    }
                }
            } catch (Exception ignored) {
                // not JSON
            }
        }
        return null;
    }

    private static boolean matches(String matcher, String target) {
        if (matcher == null || matcher.isBlank() || ".*".equals(matcher)) {
            return true;
        }
        if (target == null || target.isBlank()) {
            return false;
        }
        try {
            return Pattern.compile(matcher, Pattern.CASE_INSENSITIVE).matcher(target).find();
        } catch (Exception e) {
            return target.toLowerCase().contains(matcher.toLowerCase());
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static String readStream(java.io.InputStream stream) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }
}
