package com.agentdesktop.server.agent.loop;

import com.agentdesktop.server.agent.chat.AgentRunMode;
import com.agentdesktop.server.agent.llm.LlmChatRequest;
import com.agentdesktop.server.agent.llm.LlmException;
import com.agentdesktop.server.agent.llm.LlmMessage;
import com.agentdesktop.server.agent.llm.LlmProvider;
import com.agentdesktop.server.agent.llm.LlmResponse;
import com.agentdesktop.server.agent.llm.LlmStreamEvent;
import com.agentdesktop.server.agent.llm.LlmStreamHandler;
import com.agentdesktop.server.agent.llm.LlmToolCall;
import com.agentdesktop.server.agent.llm.LlmToolDefinition;
import com.agentdesktop.server.agent.context.ContextBreakdownAnalyzer;
import com.agentdesktop.server.config.AgentLoopProperties;
import com.agentdesktop.server.agent.settings.LlmRuntimeConfig;
import com.agentdesktop.server.agent.settings.LlmSettingsService;
import com.agentdesktop.server.agent.tool.ToolContext;
import com.agentdesktop.server.agent.tool.ToolRegistry;
import com.agentdesktop.server.dto.ChatSendParams;
import com.agentdesktop.server.ws.NdjsonEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent loop aligned with Claude Code {@code query.ts} / {@code queryLoop}:
 * <ul>
 *   <li>{@code needsFollowUp} from tool_use presence — not {@code stop_reason} / {@code finish_reason}</li>
 *   <li>Same loop recurses with tools enabled until the model stops calling tools</li>
 *   <li>{@link ToolResultPairing} before each API call (Claude {@code ensureToolResultPairing})</li>
 *   <li>{@link ToolExecutor} partitions read-only tools for parallel exec ({@code toolOrchestration.ts})</li>
 *   <li>{@link ToolCallResolver} — native {@code tool_calls} only (via CLIProxyAPI-Java)</li>
 * </ul>
 */
@Service
public class AgentLoopService {

    private static final Logger log = LoggerFactory.getLogger(AgentLoopService.class);

    private final LlmProvider llmProvider;
    private final LlmSettingsService llmSettingsService;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final AgentContextSummarizer contextSummarizer;
    private final ToolCallResolver toolCallResolver;
    private final NdjsonEventPublisher events;
    private final AgentLoopProperties loopProperties;

    public AgentLoopService(
            LlmProvider llmProvider,
            LlmSettingsService llmSettingsService,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            AgentContextSummarizer contextSummarizer,
            ToolCallResolver toolCallResolver,
            NdjsonEventPublisher events,
            AgentLoopProperties loopProperties) {
        this.llmProvider = llmProvider;
        this.llmSettingsService = llmSettingsService;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.contextSummarizer = contextSummarizer;
        this.toolCallResolver = toolCallResolver;
        this.events = events;
        this.loopProperties = loopProperties;
    }

    public ContextBreakdownAnalyzer.Breakdown emitContextBreakdown(
            WebSocketSession session,
            List<LlmMessage> messages,
            ToolContext toolContext,
            ChatSendParams params) {
        LlmRuntimeConfig config = llmSettingsService.getRuntimeConfig();
        List<LlmToolDefinition> tools = toolRegistry.definitions(toolContext);
        ContextBreakdownAnalyzer.Breakdown breakdown = ContextBreakdownAnalyzer.analyze(
                messages,
                toolContext,
                params,
                tools,
                loopProperties.getMaxEstimatedChars(),
                loopProperties.getAutoCompactBufferChars(),
                resolveModel(config, null, params));
        events.contextBreakdown(session, ContextBreakdownAnalyzer.toEventMap(breakdown));
        return breakdown;
    }

    /** Used by /context — also records a visible agent step. */
    public void publishContextBreakdown(
            WebSocketSession session,
            List<LlmMessage> messages,
            ToolContext toolContext,
            ChatSendParams params) {
        ContextBreakdownAnalyzer.Breakdown breakdown =
                emitContextBreakdown(session, messages, toolContext, params);
        events.agentStep(session, "context", breakdown.percent() + "% Full",
                null,
                formatBreakdownDetail(breakdown),
                null);
    }

    private static String formatBreakdownDetail(ContextBreakdownAnalyzer.Breakdown breakdown) {
        return breakdown.totalChars() + " / " + breakdown.maxChars() + " 字符 (" + breakdown.percent() + "%)";
    }

    public List<LlmToolDefinition> toolDefinitionsFor(ToolContext toolContext) {
        return toolRegistry.definitions(toolContext);
    }

    public String run(
            WebSocketSession session,
            List<LlmMessage> seedMessages,
            ToolContext toolContext,
            AgentRunContext run) {
        LlmRuntimeConfig config = requireReady(session);
        if (config == null) {
            return "";
        }

        List<LlmMessage> messages = new ArrayList<>(seedMessages);
        List<LlmToolDefinition> tools = toolRegistry.definitions(toolContext);
        int maxTurns = loopProperties.getMaxTurns();
        boolean unlimitedTurns = loopProperties.isUnlimited();

        run.checkNotCancelled();
        events.agentStep(session, "start", "Agent 任务开始", null, null, AgentRunMode.WIRE_AGENT);

        try {
            for (int turn = 0; unlimitedTurns || turn < maxTurns; turn++) {
                run.checkNotCancelled();
                int turnNo = turn + 1;

                ToolResultPairing.repair(messages);
                events.agentStep(session, "stream_request_start", turn == 0 ? "开始请求…" : "继续分析…",
                        turnNo, null, AgentRunMode.WIRE_AGENT);
                events.agentStep(session, "turn", "第 " + turnNo + " 轮", turnNo, null, AgentRunMode.WIRE_AGENT);
                events.agentStep(session, "llm", "调用模型 CPA…", turnNo, resolveModel(config, run, null), AgentRunMode.WIRE_AGENT);

                LlmResponse modelTurn = callModelTurn(session, config, messages, tools, run);
                List<LlmToolCall> toolCalls = toolCallResolver.resolve(modelTurn);
                boolean needsFollowUp = !toolCalls.isEmpty();

                events.agentStep(session, "llm_done", needsFollowUp ? "模型请求工具" : "模型直接回答",
                        turnNo,
                        needsFollowUp ? formatToolNames(toolCalls) : null,
                        AgentRunMode.WIRE_AGENT);

                if (!needsFollowUp && toolCallResolver.hasUnparsedToolMarkup(modelTurn)) {
                    log.error(
                            "LLM returned tool markup in content without tool_calls. "
                                    + "Use CLIProxyAPI-Java at http://127.0.0.1:8317/v1 (native tool_calls).");
                    return failAnswer(session, cpaToolCallsError());
                }

                if (needsFollowUp) {
                    appendAssistantToolCalls(messages, modelTurn.content(), toolCalls);
                    events.agentStep(session, "tools", "执行工具", turnNo, formatToolNames(toolCalls), AgentRunMode.WIRE_AGENT);
                    executeToolCalls(session, messages, toolCalls, toolContext, run);
                    events.agentStep(session, "tools_done", "工具完成", turnNo,
                            toolCalls.size() + " 个工具", AgentRunMode.WIRE_AGENT);
                    continue;
                }

                return deliverCompletedTurn(session, config, messages, modelTurn, run);
            }

            log.warn("Agent loop reached max turns ({})", maxTurns);
            events.agentStep(session, "max_turns", "已达最大轮次（" + maxTurns + "）", maxTurns,
                    "将汇总已完成修改", AgentRunMode.WIRE_AGENT);
            messages.add(LlmMessage.user(
                    "Maximum agent turns (" + maxTurns + ") reached. "
                            + "Summarize what you changed and what remains. Output Markdown only — do not call tools."));
            return deliverFinalAnswerTurn(session, config, messages, run, 0);
        } catch (AgentLoopCancelledException e) {
            ToolResultPairing.insertSyntheticErrors(messages, "Interrupted by user");
            throw e;
        } catch (Exception e) {
            log.error("Agent loop failed", e);
            ToolResultPairing.insertSyntheticErrors(messages, "Agent loop failed");
            String message = e.getMessage() == null ? "Agent 执行失败" : e.getMessage();
            return failAnswer(session, message);
        }
    }

    /**
     * Manual {@code /compact} — Claude Code {@code commands/compact/compact.ts}.
     */
    public String runManualCompact(
            WebSocketSession session,
            List<LlmMessage> seedMessages,
            AgentRunContext run) {
        LlmRuntimeConfig config = requireReady(session);
        if (config == null) {
            return "";
        }
        run.checkNotCancelled();
        List<LlmMessage> messages = new ArrayList<>(seedMessages);
        AgentContextSummarizer.CompactResult result =
                contextSummarizer.apply(messages, config, run, CompactMode.MANUAL);
        int chars = AgentContextCompactor.estimateChars(result.messages());
        int max = loopProperties.getMaxEstimatedChars();
        if (result.changed()) {
            events.agentStep(session, "compact", "手动整理上下文…", null,
                    "压缩后 " + chars + " / " + max + " 字符", AgentRunMode.WIRE_AGENT);
            if (result.summarySnippet() != null) {
                events.compactBoundary(session, "manual", result.summarySnippet(), chars, max);
            }
        } else {
            events.agentStep(session, "context", "上下文占用", null,
                    chars + " / " + max + " 字符", AgentRunMode.WIRE_AGENT);
        }
        String reply = result.changed()
                ? "已整理上下文（" + chars + " / " + max + " 字符）。"
                + (result.summarySnippet() != null ? "\n\n" + result.summarySnippet() : "")
                : "上下文已在预算内（" + chars + " / " + max + " 字符），无需压缩。";
        emitAnswerToUi(session, reply);
        return reply;
    }

    /**
     * Claude Code always runs queryLoop with a project; we only omit tools when there is no cwd.
     */
    public String streamOnce(
            WebSocketSession session,
            List<LlmMessage> seedMessages,
            AgentRunContext run) {
        LlmRuntimeConfig config = requireReady(session);
        if (config == null) {
            return "";
        }
        run.checkNotCancelled();
        events.agentStep(session, "stream_request_start", "开始请求…", 1, null, AgentRunMode.WIRE_STREAM);
        events.agentStep(session, "llm", "调用模型 CPA…", 1, resolveModel(config, run, null), AgentRunMode.WIRE_STREAM);
        return deliverFinalAnswerTurn(session, config, seedMessages, run, 0);
    }

    /**
     * Nested agent loop for {@code Task} tool — streams tool events to parent WebSocket when available.
     */
    public String runSubagent(
            WebSocketSession session,
            List<LlmMessage> seedMessages,
            ToolContext toolContext,
            AgentRunContext run,
            int maxTurns) {
        LlmRuntimeConfig config = llmSettingsService.getRuntimeConfig();
        if (!config.enabled() || !llmProvider.isConfigured()) {
            return "Subagent unavailable: LLM not configured.";
        }
        if (maxTurns <= 0) {
            maxTurns = 8;
        }

        List<LlmMessage> messages = new ArrayList<>(seedMessages);
        List<LlmToolDefinition> tools = toolRegistry.definitions(toolContext);

        run.checkNotCancelled();
        try {
            for (int turn = 0; turn < maxTurns; turn++) {
                run.checkNotCancelled();
                ToolResultPairing.repair(messages);
                LlmResponse modelTurn = callModelTurn(session, config, messages, tools, run);
                List<LlmToolCall> toolCalls = toolCallResolver.resolve(modelTurn);
                if (!toolCalls.isEmpty()) {
                    appendAssistantToolCalls(messages, modelTurn.content(), toolCalls);
                    executeToolCalls(session, messages, toolCalls, toolContext, run);
                    continue;
                }
                String text = AssistantContentFilter.sanitizeForUser(
                        modelTurn.content() == null ? "" : modelTurn.content());
                if (AssistantContentFilter.isCleanUserText(text)) {
                    return text;
                }
                return deliverFinalAnswerTurn(session, config, messages, run, 0);
            }
            messages.add(LlmMessage.user(
                    "Maximum subagent turns reached. Summarize findings in Markdown only — do not call tools."));
            return deliverFinalAnswerTurn(session, config, messages, run, 0);
        } catch (AgentLoopCancelledException e) {
            return "Subagent cancelled.";
        } catch (Exception e) {
            log.warn("Subagent loop failed", e);
            return "Subagent failed: " + (e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private LlmRuntimeConfig requireReady(WebSocketSession session) {
        LlmRuntimeConfig config = llmSettingsService.getRuntimeConfig();
        if (!config.enabled()) {
            return failWithMessage(session, "LLM is disabled. Enable it in Settings → LLM Provider.");
        }
        if (!llmProvider.isConfigured()) {
            return failWithMessage(session,
                    "LLM is not configured. Open Settings, set API Key and Save, then try again.");
        }
        return config;
    }

    private LlmRuntimeConfig failWithMessage(WebSocketSession session, String text) {
        streamStaticMessage(session, text);
        return null;
    }

    /** Model turn with tools — stream policy from Settings (CPA ToolTest defaults to non-stream). */
    private LlmResponse callModelTurn(
            WebSocketSession session,
            LlmRuntimeConfig config,
            List<LlmMessage> messages,
            List<LlmToolDefinition> tools,
            AgentRunContext run) {
        if (config.streamToolTurns()) {
            return callModelTurnStreaming(session, config, messages, tools, run);
        }
        return callModelTurnNonStreaming(session, config, messages, tools, run);
    }

    private LlmResponse callModelTurnNonStreaming(
            WebSocketSession session,
            LlmRuntimeConfig config,
            List<LlmMessage> messages,
            List<LlmToolDefinition> tools,
            AgentRunContext run) {
        for (int attempt = 0; attempt < 2; attempt++) {
            List<LlmMessage> compacted = prepareContext(session, messages, config, run);

            LlmChatRequest request = LlmChatRequest.builder()
                    .messages(compacted)
                    .model(resolveModel(config, run, null))
                    .tools(tools)
                    .toolChoice("auto")
                    .temperature(config.temperature())
                    .maxTokens(config.maxTokens())
                    .stream(false)
                    .build();

            try {
                run.checkNotCancelled();
                return llmProvider.complete(request);
            } catch (Exception e) {
                Throwable cause = e instanceof LlmException ? e : e;
                if (cause instanceof AgentLoopCancelledException cancelled) {
                    throw cancelled;
                }
                if (cause instanceof LlmException llmException) {
                    if (attempt == 0 && isContextLengthError(llmException)
                            && applyReactiveCompact(session, messages, config, run)) {
                        continue;
                    }
                    throw llmException;
                }
                throw new LlmException("LLM 规划失败: " + cause.getMessage(), cause);
            }
        }
        throw new LlmException("LLM 规划失败");
    }

    private LlmResponse callModelTurnStreaming(
            WebSocketSession session,
            LlmRuntimeConfig config,
            List<LlmMessage> messages,
            List<LlmToolDefinition> tools,
            AgentRunContext run) {
        List<LlmMessage> compacted = prepareContext(session, messages, config, run);
        LlmChatRequest request = LlmChatRequest.builder()
                .messages(compacted)
                .model(resolveModel(config, run, null))
                .tools(tools)
                .toolChoice("auto")
                .temperature(config.temperature())
                .maxTokens(config.maxTokens())
                .stream(true)
                .build();

        AtomicReference<LlmResponse> completed = new AtomicReference<>();
        try {
            run.checkNotCancelled();
            CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
                    llmProvider.streamComplete(request, new LlmStreamHandler() {
                        @Override
                        public void onEvent(LlmStreamEvent event) {
                            if (event instanceof LlmStreamEvent.Completed done) {
                                completed.set(done.response());
                            } else if (event instanceof LlmStreamEvent.Error err) {
                                throw new LlmException(err.message());
                            }
                        }
                    }));
            future.orTimeout(config.timeoutSeconds(), TimeUnit.SECONDS).join();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof AgentLoopCancelledException cancelled) {
                throw cancelled;
            }
            if (cause instanceof LlmException llmException) {
                throw llmException;
            }
            if (cause instanceof TimeoutException) {
                throw new LlmException("LLM 规划超时（" + config.timeoutSeconds() + " 秒）。");
            }
            throw new LlmException("LLM 流式规划失败: " + cause.getMessage(), cause);
        }
        LlmResponse response = completed.get();
        if (response == null) {
            throw new LlmException("LLM 流式规划未返回完整响应");
        }
        return response;
    }

    private List<LlmMessage> prepareContext(
            WebSocketSession session,
            List<LlmMessage> messages,
            LlmRuntimeConfig config,
            AgentRunContext run) {
        AgentContextSummarizer.CompactResult result =
                contextSummarizer.apply(messages, config, run, CompactMode.AUTO);
        int chars = AgentContextCompactor.estimateChars(result.messages());
        int max = loopProperties.getMaxEstimatedChars();
        if (result.changed()) {
            events.agentStep(session, "compact", "整理上下文…", null,
                    "压缩后 " + chars + " / " + max + " 字符", AgentRunMode.WIRE_AGENT);
            if (result.summarySnippet() != null) {
                events.compactBoundary(session, "auto", result.summarySnippet(), chars, max);
            }
            messages.clear();
            messages.addAll(result.messages());
        } else {
            events.agentStep(session, "context", "上下文占用", null,
                    chars + " / " + max + " 字符", AgentRunMode.WIRE_AGENT);
        }
        return result.messages();
    }

    /** {@code !needsFollowUp} exit path — user-visible text only (Claude Code queryLoop completed). */
    private String deliverCompletedTurn(
            WebSocketSession session,
            LlmRuntimeConfig config,
            List<LlmMessage> messages,
            LlmResponse modelTurn,
            AgentRunContext run) {
        run.checkNotCancelled();
        String text = AssistantContentFilter.sanitizeForUser(
                modelTurn.content() == null ? "" : modelTurn.content());
        if (AssistantContentFilter.isCleanUserText(text)) {
            events.agentStep(session, "writing", "生成回答…");
            emitAnswerToUi(session, text);
            return text;
        }
        return deliverFinalAnswerTurn(session, config, messages, run, 0);
    }

    /**
     * Final answer ({@code tool_choice=none}).
     * Prefers {@link LlmRuntimeConfig#streamAnswerTurns()}; falls back to non-stream (CPA ToolTest round 2).
     */
    private String deliverFinalAnswerTurn(
            WebSocketSession session,
            LlmRuntimeConfig config,
            List<LlmMessage> messages,
            AgentRunContext run,
            int attempt) {
        if (config.streamAnswerTurns() && attempt == 0) {
            String streamed = streamFinalAnswerTurn(session, config, messages, run);
            if (!streamed.isBlank()) {
                return streamed;
            }
            log.warn("Answer stream empty or invalid (model={}); falling back to non-stream (CPA ToolTest round 2)",
                    resolveModel(config, run, null));
        }
        return completeFinalAnswerTurn(session, config, messages, run, attempt);
    }

    private String completeFinalAnswerTurn(
            WebSocketSession session,
            LlmRuntimeConfig config,
            List<LlmMessage> messages,
            AgentRunContext run,
            int attempt) {
        for (int reactiveAttempt = 0; reactiveAttempt < 2; reactiveAttempt++) {
            ToolResultPairing.repair(messages);
            List<LlmMessage> compacted = prepareContext(session, messages, config, run);

            LlmChatRequest request = LlmChatRequest.builder()
                    .messages(compacted)
                    .model(resolveModel(config, run, null))
                    .tools(List.of())
                    .toolChoice("none")
                    .temperature(config.temperature())
                    .maxTokens(config.maxTokens())
                    .stream(false)
                    .build();

            try {
                run.checkNotCancelled();
                LlmResponse response = llmProvider.complete(request);

                if (run.isCancelled()) {
                    return "";
                }

                String text = AssistantContentFilter.sanitizeForUser(
                        response.content() == null ? "" : response.content());
                if (AssistantContentFilter.isCleanUserText(text)) {
                    events.agentStep(session, "writing", "生成回答…");
                    emitAnswerToUi(session, text);
                    return text;
                }

                if (toolCallResolver.hasUnparsedToolMarkup(response)) {
                    log.error("Final answer contained tool markup without tool_calls (model={})", resolveModel(config, run, null));
                    return failAnswer(session, cpaToolCallsError());
                }

                log.warn("Final answer empty or invalid ({} messages, ~{} chars, attempt={})",
                        compacted.size(), AgentContextCompactor.estimateChars(compacted), attempt);
                return failAnswer(session,
                        "模型未返回有效文字。请检查 CPA 网关与 Model 配置，或缩小问题范围后重试。");
            } catch (Exception e) {
                if (run.isCancelled()) {
                    return "";
                }
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                if (cause instanceof AgentLoopCancelledException) {
                    throw (AgentLoopCancelledException) cause;
                }
                if (cause instanceof TimeoutException) {
                    throw new LlmException("LLM 回答超时（" + config.timeoutSeconds()
                            + " 秒）。请缩小问题或检查 API 连接。");
                }
                if (cause instanceof LlmException llmException) {
                    if (reactiveAttempt == 0 && isContextLengthError(llmException)
                            && applyReactiveCompact(session, messages, config, run)) {
                        continue;
                    }
                    throw llmException;
                }
                throw new LlmException("LLM 回答失败: " + cause.getMessage(), cause);
            }
        }
        throw new LlmException("LLM 回答失败");
    }

    private String streamFinalAnswerTurn(
            WebSocketSession session,
            LlmRuntimeConfig config,
            List<LlmMessage> messages,
            AgentRunContext run) {
        ToolResultPairing.repair(messages);
        List<LlmMessage> compacted = prepareContext(session, messages, config, run);

        LlmChatRequest request = LlmChatRequest.builder()
                .messages(compacted)
                .model(resolveModel(config, run, null))
                .tools(List.of())
                .toolChoice("none")
                .temperature(config.temperature())
                .maxTokens(config.maxTokens())
                .stream(true)
                .build();

        StreamingTextSanitizer sanitizer = new StreamingTextSanitizer();
        try {
            run.checkNotCancelled();
            CompletableFuture<Void> streamFuture = CompletableFuture.runAsync(() ->
                    llmProvider.streamComplete(request, new LlmStreamHandler() {
                        @Override
                        public void onEvent(LlmStreamEvent event) {
                            if (run.isCancelled()) {
                                return;
                            }
                            switch (event) {
                                case LlmStreamEvent.ContentDelta delta -> {
                                    if (!delta.delta().isEmpty()) {
                                        String visible = sanitizer.consumeVisibleDelta(delta.delta());
                                        emitUserTextDelta(session, visible);
                                    }
                                }
                                case LlmStreamEvent.Completed completed -> {
                                    String content = completed.response().content();
                                    if (content != null) {
                                        sanitizer.consumeVisibleDelta(content);
                                    }
                                    emitUserTextDelta(session, sanitizer.flushRemainder());
                                }
                                case LlmStreamEvent.Error err -> events.error(session, err.message());
                                default -> {
                                }
                            }
                        }
                    }));
            streamFuture.orTimeout(config.timeoutSeconds(), TimeUnit.SECONDS).join();
        } catch (Exception e) {
            if (run.isCancelled()) {
                return partialText(sanitizer);
            }
            log.debug("Answer stream failed: {}", e.getMessage());
            return "";
        }
        if (run.isCancelled()) {
            return partialText(sanitizer);
        }
        events.agentStep(session, "writing", "生成回答…");
        String answer = sanitizer.visibleText();
        return AssistantContentFilter.isCleanUserText(answer) ? answer : "";
    }

    private static String partialText(StreamingTextSanitizer sanitizer) {
        String partial = sanitizer.visibleText();
        return partial.isBlank() ? "" : partial;
    }

    /** Chunked text_delta — smaller chunks so the UI can render incrementally (Cursor-style). */
    private void emitAnswerToUi(WebSocketSession session, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        final int chunkSize = 32;
        for (int i = 0; i < text.length(); i += chunkSize) {
            emitUserTextDelta(session, text.substring(i, Math.min(i + chunkSize, text.length())));
        }
    }

    private void emitUserTextDelta(WebSocketSession session, String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        events.textDelta(session, delta);
    }

    private String failAnswer(WebSocketSession session, String message) {
        events.error(session, message);
        events.textDelta(session, message);
        return message;
    }

    private static void appendAssistantToolCalls(
            List<LlmMessage> messages,
            String rawContent,
            List<LlmToolCall> toolCalls) {
        String assistantContent = AssistantContentFilter.sanitizeForUser(rawContent == null ? "" : rawContent);
        messages.add(LlmMessage.assistant(assistantContent, toolCalls));
    }

    private static String formatToolNames(List<LlmToolCall> toolCalls) {
        return toolCalls.stream().map(LlmToolCall::name).distinct().reduce((a, b) -> a + ", " + b).orElse("");
    }

    private void executeToolCalls(
            WebSocketSession session,
            List<LlmMessage> messages,
            List<LlmToolCall> toolCalls,
            ToolContext toolContext,
            AgentRunContext run) {
        List<ToolExecutionResult> results =
                toolExecutor.executeBatch(session, events, toolCalls, toolContext, run);
        for (ToolExecutionResult item : results) {
            messages.add(LlmMessage.tool(
                    item.call().id(),
                    item.call().name(),
                    item.result().forModelContext()));
            if (!item.result().supplementalParts().isEmpty()) {
                messages.add(LlmMessage.userMultimodal(
                        "[read_file supplemental content]",
                        item.result().supplementalParts()));
            }
            String followUp = item.result().followUpUserText();
            if (followUp != null && !followUp.isBlank()) {
                messages.add(LlmMessage.user(followUp));
            }
        }
    }

    private String streamStaticMessage(WebSocketSession session, String text) {
        events.textDelta(session, text);
        return text;
    }

    private static String cpaToolCallsError() {
        return "LLM 未返回标准 tool_calls（网关需使用 CLIProxyAPI-Java）。"
                + "请 Settings 中设置 Base URL 为 http://127.0.0.1:8317/v1，"
                + "API Key 为 CPA 客户端密钥，Model 从 GET /v1/models 选择。";
    }

    private boolean isContextLengthError(LlmException error) {
        if (error.getStatusCode() == 413) {
            return true;
        }
        String msg = error.getMessage();
        if (msg == null) {
            return false;
        }
        String lower = msg.toLowerCase();
        return lower.contains("context length")
                || lower.contains("context_length")
                || lower.contains("prompt is too long")
                || lower.contains("maximum context")
                || lower.contains("token limit")
                || lower.contains("too many tokens")
                || lower.contains("request too large");
    }

    private boolean applyReactiveCompact(
            WebSocketSession session,
            List<LlmMessage> messages,
            LlmRuntimeConfig config,
            AgentRunContext run) {
        events.agentStep(session, "compact", "上下文过长，自动压缩并重试…", null, null, AgentRunMode.WIRE_AGENT);
        AgentContextSummarizer.CompactResult result =
                contextSummarizer.apply(messages, config, run, CompactMode.REACTIVE);
        if (!result.changed()) {
            return false;
        }
        messages.clear();
        messages.addAll(result.messages());
        int chars = AgentContextCompactor.estimateChars(messages);
        int max = loopProperties.getMaxEstimatedChars();
        if (result.summarySnippet() != null) {
            events.compactBoundary(session, "auto", result.summarySnippet(), chars, max);
        }
        return true;
    }

    private static String resolveModel(LlmRuntimeConfig config, AgentRunContext run, ChatSendParams params) {
        if (run != null && run.modelOverride() != null && !run.modelOverride().isBlank()) {
            return run.modelOverride().trim();
        }
        if (params != null && params.getModel() != null && !params.getModel().isBlank()) {
            return params.getModel().trim();
        }
        return config.model();
    }
}
