package com.agentdesktop.server.agent.loop;

import com.agentdesktop.server.agent.checkpoint.FileHistoryService;
import com.agentdesktop.server.agent.checkpoint.ReadFileStateService;
import com.agentdesktop.server.agent.extension.SessionSkillState;
import com.agentdesktop.server.agent.extension.SkillEntry;
import com.agentdesktop.server.agent.extension.SkillPathMatcher;
import com.agentdesktop.server.agent.extension.HookContext;
import com.agentdesktop.server.agent.extension.HookEvents;
import com.agentdesktop.server.agent.extension.HookOutcome;
import com.agentdesktop.server.agent.extension.HookRunner;
import com.agentdesktop.server.agent.llm.LlmToolCall;
import com.agentdesktop.server.agent.permission.PermissionService;
import com.agentdesktop.server.agent.tool.ToolContext;
import com.agentdesktop.server.agent.tool.ToolRegistry;
import com.agentdesktop.server.agent.tool.ToolResult;
import com.agentdesktop.server.agent.tool.WorkspacePaths;
import com.agentdesktop.server.config.AgentLoopProperties;
import com.agentdesktop.server.ws.NdjsonEventPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

/**
 * Executes tool calls from one assistant turn. Read-only tools run in parallel;
 * write tools run serially — aligned with Claude Code {@code toolOrchestration.ts}.
 */
@Component
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private final ToolRegistry toolRegistry;
    private final PermissionService permissionService;
    private final FileHistoryService fileHistoryService;
    private final ReadFileStateService readFileStateService;
    private final AgentLoopProperties loopProperties;
    private final HookRunner hookRunner;
    private final ActiveAgentRun activeAgentRun;
    private final SessionSkillState sessionSkillState;

    public ToolExecutor(
            ToolRegistry toolRegistry,
            PermissionService permissionService,
            FileHistoryService fileHistoryService,
            ReadFileStateService readFileStateService,
            AgentLoopProperties loopProperties,
            HookRunner hookRunner,
            ActiveAgentRun activeAgentRun,
            SessionSkillState sessionSkillState) {
        this.toolRegistry = toolRegistry;
        this.permissionService = permissionService;
        this.fileHistoryService = fileHistoryService;
        this.readFileStateService = readFileStateService;
        this.loopProperties = loopProperties;
        this.hookRunner = hookRunner;
        this.activeAgentRun = activeAgentRun;
        this.sessionSkillState = sessionSkillState;
    }

    public List<ToolExecutionResult> executeBatch(
            WebSocketSession session,
            NdjsonEventPublisher events,
            List<LlmToolCall> toolCalls,
            ToolContext toolContext,
            AgentRunContext run) {
        List<ToolExecutionResult> results = new ArrayList<>();
        List<LlmToolCall> parallelBatch = new ArrayList<>();

        for (LlmToolCall call : toolCalls) {
            run.checkNotCancelled();
            if (toolRegistry.isConcurrencySafe(call.name())) {
                parallelBatch.add(call);
            } else {
                results.addAll(runParallelBatch(session, events, parallelBatch, toolContext, run));
                parallelBatch.clear();
                results.add(runOne(session, events, call, toolContext, run));
            }
        }

        results.addAll(runParallelBatch(session, events, parallelBatch, toolContext, run));
        return results;
    }

    private List<ToolExecutionResult> runParallelBatch(
            WebSocketSession session,
            NdjsonEventPublisher events,
            List<LlmToolCall> batch,
            ToolContext toolContext,
            AgentRunContext run) {
        if (batch.isEmpty()) {
            return List.of();
        }
        run.checkNotCancelled();

        int maxConcurrent = Math.max(1, loopProperties.getMaxToolConcurrency());
        Semaphore limit = new Semaphore(maxConcurrent);
        List<CompletableFuture<ToolExecutionResult>> futures = batch.stream()
                .map(call -> CompletableFuture.supplyAsync(() -> {
                    try {
                        limit.acquire();
                        return runOne(session, events, call, toolContext, run);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return new ToolExecutionResult(call, ToolResult.failure("interrupted"));
                    } finally {
                        limit.release();
                    }
                }))
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    private ToolExecutionResult runOne(
            WebSocketSession session,
            NdjsonEventPublisher events,
            LlmToolCall call,
            ToolContext toolContext,
            AgentRunContext run) {
        run.checkNotCancelled();
        events.toolStart(session, call.id(), call.name(), call.argumentsJson());

        HookOutcome hookOutcome = hookRunner.runEvent(
                toolContext.extensionBundleOrEmpty().hooks(),
                HookEvents.PRE_TOOL_USE,
                HookContext.forTool(
                        HookEvents.PRE_TOOL_USE,
                        call.name(),
                        call.argumentsJson(),
                        null,
                        true,
                        toolContext.workspaceRoot(),
                        toolContext.sessionId()));
        if (!hookOutcome.allowed()) {
            ToolResult denied = ToolResult.failure(hookOutcome.reason());
            events.toolResult(session, call.id(), call.name(), false, denied.forUiPreview());
            return new ToolExecutionResult(call, denied);
        }

        WriteTarget target = parseWriteTarget(call, toolContext);
        if (permissionService.isWriteTool(call.name()) || permissionService.isBashTool(call.name())) {
            if ("plan".equals(toolContext.permissionModeOrDefault())) {
                ToolResult denied = ToolResult.failure(
                        "Plan 模式已启用：写工具 " + call.name() + " 被禁用。请切换到 Ask/Auto 模式后再修改文件。");
                events.toolResult(session, call.id(), call.name(), false, denied.forUiPreview());
                return new ToolExecutionResult(call, denied);
            }
            String summary = buildPermissionSummary(call.name(), target);
            boolean allowed = permissionService.shouldAllowTool(
                    session,
                    events,
                    toolContext.permissionModeOrDefault(),
                    toolContext.permissionRulesOrEmpty(),
                    call.name(),
                    summary,
                    target.relativePath());
            if (!allowed) {
                ToolResult denied = ToolResult.failure("用户拒绝了 " + call.name() + " 操作");
                events.toolResult(session, call.id(), call.name(), false, denied.forUiPreview());
                return new ToolExecutionResult(call, denied);
            }
        } else {
            PermissionBehaviorCheck readCheck = evaluateReadTool(call.name(), target, toolContext);
            if (readCheck == PermissionBehaviorCheck.DENY) {
                ToolResult denied = ToolResult.failure("权限规则拒绝了 " + call.name());
                events.toolResult(session, call.id(), call.name(), false, denied.forUiPreview());
                return new ToolExecutionResult(call, denied);
            }
        }

        if (permissionService.isWriteTool(call.name()) && target.path() != null) {
            Optional<String> stalenessError = validateWriteStaleness(call.name(), target, toolContext);
            if (stalenessError.isPresent()) {
                ToolResult denied = ToolResult.failure(stalenessError.get());
                events.toolResult(session, call.id(), call.name(), false, denied.forUiPreview());
                return new ToolExecutionResult(call, denied);
            }
        }

        String previousContent = readFileBeforeMutation(call, toolContext);
        ToolContext effectiveContext = toolContext.withActiveSkillAllowedTools(
                sessionSkillState.activeAllowedTools(toolContext.sessionId()));
        activeAgentRun.bind(run, session);
        ToolResult result;
        try {
            result = toolRegistry.execute(call.name(), call.argumentsJson(), effectiveContext);
        } finally {
            activeAgentRun.unbind();
        }

        ToolResult reported = result;
        if (result.ok()) {
            activateConditionalSkillsForTool(call, effectiveContext, target);
        }
        if (result.ok() && permissionService.isWriteTool(call.name()) && target.path() != null) {
            fileHistoryService.trackEdit(toolContext.sessionId(), target.path(), previousContent);
            Path absolute = Path.of(target.path());
            if ("delete_file".equals(call.name())) {
                readFileStateService.recordDelete(toolContext.sessionId(), absolute);
            } else {
                readFileStateService.recordMutation(toolContext.sessionId(), absolute);
            }
            if (!maybeEmitFileChanged(session, events, call, toolContext, result, previousContent)) {
                reported = appendSyncWarning(result);
            }
        }

        events.toolResult(session, call.id(), call.name(), reported.ok(), reported.forUiPreview());

        hookRunner.runEvent(
                toolContext.extensionBundleOrEmpty().hooks(),
                HookEvents.POST_TOOL_USE,
                HookContext.forTool(
                        HookEvents.POST_TOOL_USE,
                        call.name(),
                        call.argumentsJson(),
                        reported.forModelContext(),
                        reported.ok(),
                        toolContext.workspaceRoot(),
                        toolContext.sessionId()));

        return new ToolExecutionResult(call, reported);
    }

    private Optional<String> validateWriteStaleness(String toolName, WriteTarget target, ToolContext toolContext) {
        Path absolute = Path.of(target.path());
        ReadFileStateService.MutationKind kind = switch (toolName) {
            case "write_file" -> ReadFileStateService.MutationKind.WRITE;
            case "search_replace" -> ReadFileStateService.MutationKind.EDIT;
            case "delete_file" -> ReadFileStateService.MutationKind.DELETE;
            default -> ReadFileStateService.MutationKind.WRITE;
        };
        return readFileStateService.validateMutation(toolContext.sessionId(), absolute, toolContext, kind);
    }

    private static ToolResult appendSyncWarning(ToolResult result) {
        String warning = "\n\n⚠️ [sync warning] File was written to disk but the editor could not be notified. "
                + "Reload the file or check the Review panel.";
        return ToolResult.success((result.output() == null ? "" : result.output()) + warning);
    }

    private enum PermissionBehaviorCheck {
        ALLOW,
        DENY
    }

    private static PermissionBehaviorCheck evaluateReadTool(
            String toolName,
            WriteTarget target,
            ToolContext toolContext) {
        var behavior = toolContext.permissionRulesOrEmpty().evaluate(toolName, target.relativePath());
        if (behavior == com.agentdesktop.server.agent.permission.PermissionBehavior.DENY) {
            return PermissionBehaviorCheck.DENY;
        }
        return PermissionBehaviorCheck.ALLOW;
    }

    private record WriteTarget(String path, String relativePath) {}

    private WriteTarget parseWriteTarget(LlmToolCall call, ToolContext toolContext) {
        try {
            JsonNode args = toolRegistry.parseArguments(call.argumentsJson());
            String pathArg = args.path("path").asText("");
            if (pathArg.isBlank()) {
                return new WriteTarget(null, pathArg);
            }
            Path absolute = WorkspacePaths.resolve(toolContext, pathArg);
            return new WriteTarget(absolute.toString(), pathArg);
        } catch (Exception e) {
            return new WriteTarget(null, "");
        }
    }

    private static String buildPermissionSummary(String toolName, WriteTarget target) {
        if (target.relativePath() != null && !target.relativePath().isBlank()) {
            return toolName + " → " + target.relativePath();
        }
        return toolName;
    }

    private String readFileBeforeMutation(LlmToolCall call, ToolContext toolContext) {
        String toolName = call.name();
        if (!permissionService.isWriteTool(toolName)) {
            return null;
        }
        try {
            JsonNode args = toolRegistry.parseArguments(call.argumentsJson());
            String pathArg = args.path("path").asText("");
            if (pathArg.isBlank()) {
                return null;
            }
            Path absolute = WorkspacePaths.resolve(toolContext, pathArg);
            if (java.nio.file.Files.isRegularFile(absolute)) {
                return java.nio.file.Files.readString(absolute, java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            // best effort
        }
        return null;
    }

    /**
     * @return true when file_changed was emitted (or not needed); false when emit failed
     */
    private boolean maybeEmitFileChanged(
            WebSocketSession session,
            NdjsonEventPublisher events,
            LlmToolCall call,
            ToolContext toolContext,
            ToolResult result,
            String previousContent) {
        if (!result.ok()) {
            return true;
        }
        String toolName = call.name();
        if (!permissionService.isWriteTool(toolName)) {
            return true;
        }
        try {
            JsonNode args = toolRegistry.parseArguments(call.argumentsJson());
            String pathArg = args.path("path").asText("");
            if (pathArg.isBlank()) {
                return true;
            }
            Path absolute = WorkspacePaths.resolve(toolContext, pathArg);
            if ("delete_file".equals(toolName)) {
                return events.fileChanged(session, absolute.toString(), "deleted", null, previousContent);
            }
            if (!java.nio.file.Files.isRegularFile(absolute)) {
                return true;
            }
            String content = java.nio.file.Files.readString(absolute, java.nio.charset.StandardCharsets.UTF_8);
            String kind = previousContent == null ? "created" : "modified";
            return events.fileChanged(session, absolute.toString(), kind, content, previousContent);
        } catch (Exception e) {
            log.error(
                    "file_changed emit failed for tool {} session {}: {}",
                    call.name(),
                    toolContext.sessionId(),
                    e.toString(),
                    e);
            return false;
        }
    }

    private void activateConditionalSkillsForTool(LlmToolCall call, ToolContext toolContext, WriteTarget target) {
        if (toolContext.sessionId() == null || toolContext.workspacePath() == null) {
            return;
        }
        Path touched = resolveTouchedPath(call, toolContext, target);
        if (touched == null) {
            return;
        }
        for (SkillEntry skill : toolContext.extensionBundleOrEmpty().skills()) {
            if (!skill.conditional()) {
                continue;
            }
            if (SkillPathMatcher.matchesAny(skill.pathPatterns(), toolContext.workspacePath(), touched)) {
                sessionSkillState.activateConditional(toolContext.sessionId(), skill.name());
            }
        }
    }

    private Path resolveTouchedPath(LlmToolCall call, ToolContext toolContext, WriteTarget target) {
        if (target.path() != null && !target.path().isBlank()) {
            return Path.of(target.path());
        }
        try {
            JsonNode args = toolRegistry.parseArguments(call.argumentsJson());
            String pathArg = args.path("path").asText("");
            if (pathArg.isBlank()) {
                return null;
            }
            return WorkspacePaths.resolve(toolContext, pathArg);
        } catch (Exception e) {
            return null;
        }
    }
}
