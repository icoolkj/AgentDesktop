package com.agentdesktop.server.service;

import com.agentdesktop.server.agent.llm.LlmChatRequest;
import com.agentdesktop.server.agent.llm.LlmMessage;
import com.agentdesktop.server.agent.llm.LlmProvider;
import com.agentdesktop.server.agent.llm.LlmResponse;
import com.agentdesktop.server.agent.llm.LlmStreamEvent;
import com.agentdesktop.server.agent.llm.LlmStreamHandler;
import com.agentdesktop.server.agent.settings.LlmRuntimeConfig;
import com.agentdesktop.server.agent.settings.LlmSettingsService;
import com.agentdesktop.server.agent.loop.AgentLoopCancelledException;
import com.agentdesktop.server.agent.loop.AgentRunContext;
import com.agentdesktop.server.agent.loop.AgentRunRegistry;
import com.agentdesktop.server.config.AgentExecutorConfig;
import com.agentdesktop.server.dto.InlineEditParams;
import com.agentdesktop.server.ws.NdjsonEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cursor-style Ctrl+K inline edit — single LLM turn, code-only replacement (no tools).
 */
@Service
public class InlineEditService {

    private static final Logger log = LoggerFactory.getLogger(InlineEditService.class);
    private static final int RUN_TIMEOUT_SECONDS = 120;
    private static final Pattern FENCE = Pattern.compile("^```[a-zA-Z0-9_-]*\\n([\\s\\S]*?)```\\s*$");

    private final LlmProvider llmProvider;
    private final LlmSettingsService llmSettingsService;
    private final NdjsonEventPublisher events;
    private final AgentRunRegistry runRegistry;
    private final ExecutorService agentExecutor;

    public InlineEditService(
            LlmProvider llmProvider,
            LlmSettingsService llmSettingsService,
            NdjsonEventPublisher events,
            AgentRunRegistry runRegistry,
            @Qualifier(AgentExecutorConfig.AGENT_EXECUTOR_BEAN) ExecutorService agentExecutor) {
        this.llmProvider = llmProvider;
        this.llmSettingsService = llmSettingsService;
        this.events = events;
        this.runRegistry = runRegistry;
        this.agentExecutor = agentExecutor;
    }

    public CompletableFuture<Void> handleInlineEdit(WebSocketSession wsSession, InlineEditParams params) {
        if (runRegistry.hasActiveRun(wsSession.getId())) {
            runRegistry.forceEnd(wsSession.getId());
        }
        return CompletableFuture
                .runAsync(() -> executeInlineEdit(wsSession, params), agentExecutor)
                .orTimeout(RUN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    Throwable cause = ex instanceof CompletionException && ex.getCause() != null
                            ? ex.getCause()
                            : ex;
                    String requestId = params.getRequestId() == null ? "" : params.getRequestId();
                    if (cause instanceof TimeoutException) {
                        events.inlineEditError(wsSession, requestId, "Inline edit timed out");
                    } else if (!(cause instanceof AgentLoopCancelledException)) {
                        log.warn("inline.edit failed", cause);
                        events.inlineEditError(
                                wsSession,
                                requestId,
                                cause.getMessage() == null ? "Inline edit failed" : cause.getMessage());
                    }
                    runRegistry.end(wsSession.getId());
                    return null;
                });
    }

    private void executeInlineEdit(WebSocketSession wsSession, InlineEditParams params) {
        String requestId = params.getRequestId() == null ? "" : params.getRequestId();
        AgentRunContext run = runRegistry.begin(wsSession.getId());
        if (params.getModel() != null && !params.getModel().isBlank()) {
            run.setModelOverride(params.getModel().trim());
        }
        try {
            String instruction = params.getInstruction() == null ? "" : params.getInstruction().trim();
            if (instruction.isEmpty()) {
                events.inlineEditError(wsSession, requestId, "Instruction is required");
                return;
            }

            LlmRuntimeConfig config = llmSettingsService.getRuntimeConfig();
            if (!config.enabled() || !llmProvider.isConfigured()) {
                events.inlineEditError(wsSession, requestId, "LLM is not configured");
                return;
            }

            List<LlmMessage> messages = buildMessages(params, instruction);
            String model = params.getModel() != null && !params.getModel().isBlank()
                    ? params.getModel().trim()
                    : config.model();

            LlmChatRequest request = LlmChatRequest.builder()
                    .messages(messages)
                    .model(model)
                    .tools(List.of())
                    .toolChoice("none")
                    .temperature(Math.min(config.temperature(), 0.4))
                    .maxTokens(Math.min(config.maxTokens(), 4096))
                    .stream(true)
                    .build();

            StringBuilder raw = new StringBuilder();
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
                                        raw.append(delta.delta());
                                        events.inlineEditDelta(wsSession, requestId, delta.delta());
                                    }
                                }
                                case LlmStreamEvent.Completed completed -> {
                                    LlmResponse response = completed.response();
                                    if (response != null && response.content() != null) {
                                        raw.append(response.content());
                                    }
                                }
                                case LlmStreamEvent.Error err ->
                                        events.inlineEditError(wsSession, requestId, err.message());
                                default -> {
                                }
                            }
                        }
                    }));
            streamFuture.orTimeout(config.timeoutSeconds(), TimeUnit.SECONDS).join();

            if (run.isCancelled()) {
                return;
            }

            String code = normalizeReplacement(raw.toString());
            if (code.isBlank()) {
                events.inlineEditError(wsSession, requestId, "Model returned empty replacement");
                return;
            }
            events.inlineEditDone(wsSession, requestId, code);
        } catch (AgentLoopCancelledException ignored) {
            // user cancelled
        } catch (Exception e) {
            log.warn("inline.edit error", e);
            events.inlineEditError(wsSession, requestId, e.getMessage() == null ? "Inline edit failed" : e.getMessage());
        } finally {
            runRegistry.end(wsSession.getId());
        }
    }

    static List<LlmMessage> buildMessages(InlineEditParams params, String instruction) {
        String rel = params.getRelativePath() != null && !params.getRelativePath().isBlank()
                ? params.getRelativePath()
                : params.getFilePath();
        String lang = params.getLanguage() == null ? "" : params.getLanguage();
        String range = params.getStartLine() == params.getEndLine()
                ? params.getStartLine() + ":" + params.getStartColumn() + "-" + params.getEndColumn()
                : params.getStartLine() + ":" + params.getStartColumn() + "-"
                        + params.getEndLine() + ":" + params.getEndColumn();

        String system = """
                You are an inline code editor (Cursor Ctrl+K). The user selected a region in a file and gave an instruction.
                Respond with ONLY the replacement text for that exact selection — no markdown fences, no explanation, no comments outside the code unless the user asked for comments inside the code.
                Preserve indentation style of the selection. Do not wrap the answer in ``` blocks.""";

        String user = "File: " + rel + " (" + lang + ", lines " + range + ")\n\n"
                + "Selected code:\n"
                + params.getSelectionText()
                + "\n\nInstruction: "
                + instruction;

        List<LlmMessage> messages = new ArrayList<>();
        messages.add(LlmMessage.system(system));
        messages.add(LlmMessage.user(user));
        return messages;
    }

    static String normalizeReplacement(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        Matcher fence = FENCE.matcher(text);
        if (fence.matches()) {
            text = fence.group(1).trim();
        }
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline > 0) {
                text = text.substring(firstNewline + 1);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3).trim();
            }
        }
        return text;
    }
}
