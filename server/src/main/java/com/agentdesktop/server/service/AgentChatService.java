package com.agentdesktop.server.service;

import com.agentdesktop.server.agent.attachment.PdfLimits;
import com.agentdesktop.server.agent.llm.LlmContentPart;
import com.agentdesktop.server.agent.llm.LlmMessage;
import com.agentdesktop.server.agent.llm.LlmRole;
import com.agentdesktop.server.agent.llm.LlmToolCall;
import com.agentdesktop.server.agent.settings.LlmRuntimeConfig;
import com.agentdesktop.server.agent.settings.LlmSettingsService;
import com.agentdesktop.server.agent.chat.AgentRunMode;
import com.agentdesktop.server.agent.extension.ExtensionLoader;
import com.agentdesktop.server.agent.extension.HookContext;
import com.agentdesktop.server.agent.extension.HookEvents;
import com.agentdesktop.server.agent.extension.HookOutcome;
import com.agentdesktop.server.agent.extension.HookRunner;
import com.agentdesktop.server.agent.extension.SessionSkillState;
import com.agentdesktop.server.agent.extension.SkillCatalog;
import com.agentdesktop.server.agent.extension.SkillListingFormatter;
import com.agentdesktop.server.agent.extension.SkillMessages;
import com.agentdesktop.server.agent.extension.SlashCommandRegistry;
import com.agentdesktop.server.agent.extension.SlashCommandResult;
import com.agentdesktop.server.agent.checkpoint.FileHistoryService;
import com.agentdesktop.server.agent.permission.PermissionRuleLoader;
import com.agentdesktop.server.agent.permission.PermissionRuleSet;
import com.agentdesktop.server.agent.context.ContextBreakdownAnalyzer;
import com.agentdesktop.server.agent.loop.AgentContextCompactor;
import com.agentdesktop.server.agent.loop.AgentLoopCancelledException;
import com.agentdesktop.server.agent.loop.AgentLoopService;
import com.agentdesktop.server.agent.loop.AgentRunContext;
import com.agentdesktop.server.agent.loop.AgentRunRegistry;
import com.agentdesktop.server.agent.tool.ToolContext;
import com.agentdesktop.server.config.AgentExecutorConfig;
import com.agentdesktop.server.config.AgentLoopProperties;
import com.agentdesktop.server.dto.ChatAttachmentDto;
import com.agentdesktop.server.dto.ChatSendParams;
import com.agentdesktop.server.dto.EditorDocumentSnapshotDto;
import com.agentdesktop.server.dto.LlmHistoryMessageDto;
import com.agentdesktop.server.dto.LlmHistoryToolCallDto;
import com.agentdesktop.server.transcript.ServerTranscriptLoader;
import com.agentdesktop.server.ws.NdjsonEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.file.Path;

/**
 * Chat orchestration. Conversation transcript is persisted client-side as JSONL
 * (Claude Code parity). History is read from the same JSONL path as Electron — never over WebSocket.
 */
@Service
public class AgentChatService {

    private static final Logger log = LoggerFactory.getLogger(AgentChatService.class);
    private static final int MAX_HISTORY_MESSAGES = 48;
    /** Hard cap so UI always receives `done` even if tools/LLM hang. */
    private static final int CHAT_RUN_TIMEOUT_SECONDS = 480;

    private final AgentLoopService agentLoopService;
    private final AgentRunRegistry runRegistry;
    private final NdjsonEventPublisher events;
    private final ServerTranscriptLoader transcriptLoader;
    private final ExecutorService agentExecutor;
    private final PermissionRuleLoader permissionRuleLoader;
    private final FileHistoryService fileHistoryService;
    private final AgentLoopProperties loopProperties;
    private final ExtensionLoader extensionLoader;
    private final SlashCommandRegistry slashCommandRegistry;
    private final HookRunner hookRunner;
    private final LlmSettingsService llmSettingsService;
    private final SessionSkillState sessionSkillState;

    public AgentChatService(
            AgentLoopService agentLoopService,
            AgentRunRegistry runRegistry,
            NdjsonEventPublisher events,
            ServerTranscriptLoader transcriptLoader,
            PermissionRuleLoader permissionRuleLoader,
            FileHistoryService fileHistoryService,
            AgentLoopProperties loopProperties,
            ExtensionLoader extensionLoader,
            SlashCommandRegistry slashCommandRegistry,
            HookRunner hookRunner,
            LlmSettingsService llmSettingsService,
            SessionSkillState sessionSkillState,
            @Qualifier(AgentExecutorConfig.AGENT_EXECUTOR_BEAN) ExecutorService agentExecutor) {
        this.agentLoopService = agentLoopService;
        this.runRegistry = runRegistry;
        this.events = events;
        this.transcriptLoader = transcriptLoader;
        this.permissionRuleLoader = permissionRuleLoader;
        this.fileHistoryService = fileHistoryService;
        this.loopProperties = loopProperties;
        this.extensionLoader = extensionLoader;
        this.slashCommandRegistry = slashCommandRegistry;
        this.hookRunner = hookRunner;
        this.llmSettingsService = llmSettingsService;
        this.sessionSkillState = sessionSkillState;
        this.agentExecutor = agentExecutor;
    }

    public CompletableFuture<Void> handleChatSend(WebSocketSession wsSession, ChatSendParams params) {
        if (runRegistry.hasActiveRun(wsSession.getId())) {
            log.warn("chat.send rejected — run already active for session {}, forcing stale cleanup", wsSession.getId());
            runRegistry.forceEnd(wsSession.getId());
        }

        AtomicBoolean doneSent = new AtomicBoolean(false);
        return CompletableFuture
                .runAsync(() -> executeChatSend(wsSession, params, doneSent), agentExecutor)
                .orTimeout(CHAT_RUN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    Throwable cause = ex instanceof CompletionException && ex.getCause() != null
                            ? ex.getCause()
                            : ex;
                    if (cause instanceof TimeoutException) {
                        log.warn("chat.send timed out after {}s for session {}", CHAT_RUN_TIMEOUT_SECONDS, wsSession.getId());
                        runRegistry.cancel(wsSession.getId());
                        if (doneSent.compareAndSet(false, true)) {
                            events.error(wsSession,
                                    "Agent 执行超时（" + (CHAT_RUN_TIMEOUT_SECONDS / 60)
                                            + " 分钟）。请缩小问题范围，或指定具体文件路径后重试。");
                            events.done(wsSession, params.getSessionId());
                        }
                        runRegistry.end(wsSession.getId());
                    }
                    return null;
                });
    }

    private void executeChatSend(WebSocketSession wsSession, ChatSendParams params, AtomicBoolean doneSent) {
        AgentRunContext run = runRegistry.begin(wsSession.getId());
        if (params.getSessionId() != null && !params.getSessionId().isBlank()) {
            run.setTranscriptSessionId(params.getSessionId());
        }
        if (params.getModel() != null && !params.getModel().isBlank()) {
            run.setModelOverride(params.getModel().trim());
        }
        String sessionId = params.getSessionId();
        ToolContext toolContext = null;
        try {
            String userText = params.getMessage() == null ? "" : params.getMessage().trim();
            if (userText.isEmpty() && !hasAttachments(params)) {
                events.textDelta(wsSession, "Send a message to start chatting with the agent.");
                return;
            }

            toolContext = buildToolContext(params);
            runSessionHook(toolContext, HookEvents.SESSION_START);

            AgentRunMode.Mode runMode = AgentRunMode.resolve(toolContext);

            List<LlmHistoryMessageDto> history = resolveHistory(params);
            events.agentStep(wsSession, "history_loaded",
                    "已加载会话历史",
                    null,
                    history.size() + " 条消息",
                    null);

            emitAttachmentStep(wsSession, params);

            var extensionBundle = toolContext.extensionBundleOrEmpty();
            SlashCommandResult slash = slashCommandRegistry.resolve(userText, extensionBundle, params.getSessionId());
            if (slash instanceof SlashCommandResult.Compact) {
                handleManualCompact(wsSession, params, toolContext, run, history);
                return;
            }
            if (slash instanceof SlashCommandResult.Context) {
                handleContextCommand(wsSession, params, runMode, history);
                return;
            }
            if (slash instanceof SlashCommandResult.PromptExpansion expansion) {
                userText = expansion.expandedPrompt();
                params.setMessage(userText);
                events.agentStep(wsSession, "slash_command", "/" + expansion.commandName(), null, null, null);
            }

            HookOutcome promptHook = hookRunner.runEvent(
                    toolContext.extensionBundleOrEmpty().hooks(),
                    HookEvents.USER_PROMPT_SUBMIT,
                    HookContext.forUserPrompt(userText, toolContext.workspaceRoot(), toolContext.sessionId()));
            if (!promptHook.allowed()) {
                events.error(wsSession, promptHook.reason());
                return;
            }
            if (promptHook.updatedUserPrompt() != null && !promptHook.updatedUserPrompt().isBlank()) {
                userText = promptHook.updatedUserPrompt().trim();
                params.setMessage(userText);
            }

            events.agentStep(wsSession, "thinking", "思考中…");
            List<LlmMessage> messages = buildPromptMessages(toolContext, params, runMode, history);
            agentLoopService.emitContextBreakdown(wsSession, messages, toolContext, params);
            String modeWire = runMode == AgentRunMode.Mode.AGENT_LOOP
                    ? AgentRunMode.WIRE_AGENT
                    : AgentRunMode.WIRE_STREAM;
            String modeLabel = runMode == AgentRunMode.Mode.AGENT_LOOP
                    ? permissionModeLabel(params.getPermissionMode())
                    : "无工作区 · 纯对话";
            events.agentStep(wsSession, "mode", modeLabel, null, null, modeWire);

            if (runMode == AgentRunMode.Mode.AGENT_LOOP) {
                agentLoopService.run(wsSession, messages, toolContext, run);
            } else {
                agentLoopService.streamOnce(wsSession, messages, run);
            }
        } catch (AgentLoopCancelledException e) {
            log.info("chat.send cancelled for session {}", wsSession.getId());
        } catch (Exception e) {
            log.error("chat.send failed", e);
            events.error(wsSession, e.getMessage() == null ? "Chat failed" : e.getMessage());
        } finally {
            if (toolContext != null) {
                if (run.isCancelled()) {
                    runSessionHook(toolContext, HookEvents.STOP);
                    events.agentStep(wsSession, "cancelled", "已停止生成");
                } else {
                    runSessionHook(toolContext, HookEvents.SESSION_END);
                }
            }
            if (doneSent.compareAndSet(false, true)) {
                events.done(wsSession, sessionId);
            }
            runRegistry.end(wsSession.getId());
        }
    }

    private void handleManualCompact(
            WebSocketSession wsSession,
            ChatSendParams params,
            ToolContext toolContext,
            AgentRunContext run,
            List<LlmHistoryMessageDto> history) {
        events.agentStep(wsSession, "compact", "手动 /compact", null, null, AgentRunMode.WIRE_AGENT);
        List<LlmMessage> messages = buildPromptMessages(toolContext, params, AgentRunMode.Mode.AGENT_LOOP, history);
        agentLoopService.runManualCompact(wsSession, messages, run);
    }

    private void handleContextCommand(
            WebSocketSession wsSession,
            ChatSendParams params,
            AgentRunMode.Mode runMode,
            List<LlmHistoryMessageDto> history) {
        ToolContext toolContext = buildToolContext(params);
        List<LlmMessage> messages = buildPromptMessages(toolContext, params, runMode, history);
        agentLoopService.publishContextBreakdown(wsSession, messages, toolContext, params);
        ContextBreakdownAnalyzer.Breakdown breakdown = buildBreakdown(messages, toolContext, params);
        StringBuilder reply = new StringBuilder();
        reply.append("Context · ").append(breakdown.percent()).append("% Full\n");
        reply.append(breakdown.totalChars()).append(" / ").append(breakdown.maxChars()).append(" 字符\n\n");
        for (ContextBreakdownAnalyzer.Category cat : breakdown.categories()) {
            if ("free".equals(cat.id()) || cat.chars() <= 0) {
                continue;
            }
            int pct = breakdown.maxChars() > 0
                    ? (int) Math.round(cat.chars() * 100.0 / breakdown.maxChars())
                    : 0;
            reply.append("• ").append(cat.label()).append(": ")
                    .append(formatChars(cat.chars())).append(" (").append(pct).append("%)\n");
        }
        events.textDelta(wsSession, reply.toString().trim());
    }

    private ContextBreakdownAnalyzer.Breakdown buildBreakdown(
            List<LlmMessage> messages,
            ToolContext toolContext,
            ChatSendParams params) {
        return ContextBreakdownAnalyzer.analyze(
                messages,
                toolContext,
                params,
                agentLoopService.toolDefinitionsFor(toolContext),
                loopProperties.getMaxEstimatedChars(),
                loopProperties.getAutoCompactBufferChars(),
                "");
    }

    private static String formatChars(int chars) {
        if (chars >= 1_000_000) {
            return String.format("%.1fM", chars / 1_000_000.0);
        }
        if (chars >= 1000) {
            return String.format("%.1fK", chars / 1000.0);
        }
        return String.valueOf(chars);
    }

    public void handleChatCancel(WebSocketSession wsSession) {
        runRegistry.cancel(wsSession.getId());
    }

    private List<LlmHistoryMessageDto> resolveHistory(ChatSendParams params) {
        List<LlmHistoryMessageDto> wire = params.getHistory();
        if (wire != null && !wire.isEmpty()) {
            log.debug("chat.send using legacy wire history ({} messages)", wire.size());
            return wire;
        }
        return transcriptLoader.loadHistory(params.getSessionId(), params.getWorkspaceRoot());
    }

    private List<LlmMessage> buildPromptMessages(
            ToolContext toolContext,
            ChatSendParams params,
            AgentRunMode.Mode runMode,
            List<LlmHistoryMessageDto> history) {
        List<LlmMessage> messages = new ArrayList<>();
        messages.add(LlmMessage.system(buildSystemPrompt(toolContext, params, runMode)));

        if (history != null && !history.isEmpty()) {
            List<LlmHistoryMessageDto> usable = runMode == AgentRunMode.Mode.STREAM_ONLY
                    ? streamOnlyHistory(history)
                    : sanitizeHistory(history);
            int start = Math.max(0, usable.size() - MAX_HISTORY_MESSAGES);
            for (LlmHistoryMessageDto item : usable.subList(start, usable.size())) {
                LlmMessage mapped = toLlmMessage(item);
                if (mapped != null) {
                    messages.add(mapped);
                }
            }
        }

        String userText = params.getMessage() == null ? "" : params.getMessage().trim();
        userText = appendSkillListingForTurn(toolContext, runMode, userText, params.getSessionId());

        if (messages.isEmpty() || !isLatestUserMessage(messages, buildUserMessageText(params, userText))) {
            messages.add(buildUserMessage(params, userText));
        }

        return messages;
    }

    /** CC parity: skill catalog metadata on each agent turn (user role, not system prompt). */
    private String appendSkillListingForTurn(
            ToolContext toolContext,
            AgentRunMode.Mode runMode,
            String userText,
            String sessionId) {
        if (runMode == AgentRunMode.Mode.STREAM_ONLY) {
            return userText;
        }
        var skills = sessionSkillState.listingSkills(
                sessionId,
                toolContext.extensionBundleOrEmpty().skills());
        String listing = SkillListingFormatter.format(skills, SkillListingFormatter.DEFAULT_CHAR_BUDGET);
        if (listing.isBlank()) {
            return userText;
        }
        String listingBlock = SkillMessages.skillListingMessage(listing);
        if (userText.isBlank()) {
            return listingBlock;
        }
        return userText + "\n\n" + listingBlock;
    }

    private LlmMessage buildUserMessage(ChatSendParams params, String userText) {
        String textBody = buildUserMessageText(params, userText);
        List<LlmContentPart> parts = new ArrayList<>();
        parts.add(LlmContentPart.text(textBody));

        List<ChatAttachmentDto> attachments = params.getAttachments();
        if (attachments != null) {
            for (ChatAttachmentDto attachment : attachments) {
                if (attachment == null) {
                    continue;
                }
                String kind = attachment.getKind();
                if ("image".equals(kind)) {
                    String base64 = attachment.getBase64();
                    if (base64 == null || base64.isBlank()) {
                        continue;
                    }
                    String mime = attachment.getMimeType() == null || attachment.getMimeType().isBlank()
                            ? "image/png"
                            : attachment.getMimeType();
                    parts.add(LlmContentPart.image(mime, base64));
                } else if ("pdf".equals(kind)) {
                    String base64 = attachment.getBase64();
                    if (base64 == null || base64.isBlank()) {
                        continue;
                    }
                    String mime = attachment.getMimeType() == null || attachment.getMimeType().isBlank()
                            ? "application/pdf"
                            : attachment.getMimeType();
                    parts.add(LlmContentPart.document(mime, base64));
                }
            }
        }

        if (parts.size() == 1) {
            return LlmMessage.user(textBody);
        }
        return LlmMessage.userMultimodal(textBody, parts);
    }

    private String buildUserMessageText(ChatSendParams params, String userText) {
        StringBuilder sb = new StringBuilder();
        sb.append(userText);
        List<ChatAttachmentDto> attachments = params.getAttachments();
        if (attachments != null && !attachments.isEmpty()) {
            sb.append("\n\n--- Attached context ---\n");
            for (ChatAttachmentDto attachment : attachments) {
                if (attachment == null || attachment.getPath() == null) {
                    continue;
                }
                String kind = attachment.getKind() == null ? "file" : attachment.getKind();
                if ("image".equals(kind)) {
                    String label = attachment.getLabel() != null ? attachment.getLabel() : attachment.getPath();
                    sb.append("\n[Image] ").append(label).append(" (").append(attachment.getPath()).append(")");
                    continue;
                }
                if ("pdf_reference".equals(kind)) {
                    appendPdfReferenceHint(sb, attachment);
                    continue;
                }
                if ("pdf".equals(kind)) {
                    String label = attachment.getLabel() != null ? attachment.getLabel() : attachment.getPath();
                    sb.append("\n[PDF] ").append(label).append(" (").append(attachment.getPath()).append(")");
                    continue;
                }
                sb.append("\n[").append(kind).append("] ").append(attachment.getPath());
                if (attachment.getContent() != null && !attachment.getContent().isBlank()) {
                    sb.append("\n```\n").append(attachment.getContent()).append("\n```\n");
                }
            }
        }
        return sb.toString();
    }

    private static boolean hasAttachments(ChatSendParams params) {
        List<ChatAttachmentDto> attachments = params.getAttachments();
        return attachments != null && !attachments.isEmpty();
    }

    private void emitAttachmentStep(WebSocketSession wsSession, ChatSendParams params) {
        List<ChatAttachmentDto> attachments = params.getAttachments();
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        int images = 0;
        int pdfs = 0;
        int files = 0;
        for (ChatAttachmentDto att : attachments) {
            if (att == null || att.getKind() == null) {
                continue;
            }
            switch (att.getKind()) {
                case "image" -> images++;
                case "pdf" -> pdfs++;
                case "pdf_reference" -> pdfs++;
                default -> files++;
            }
        }
        StringBuilder detail = new StringBuilder();
        if (images > 0) {
            detail.append(images).append(" 张图片");
        }
        if (pdfs > 0) {
            if (!detail.isEmpty()) {
                detail.append(" · ");
            }
            detail.append(pdfs).append(" 个 PDF");
        }
        if (files > 0) {
            if (!detail.isEmpty()) {
                detail.append(" · ");
            }
            detail.append(files).append(" 个文件");
        }
        events.agentStep(wsSession, "attachments", "读取附件…", null, detail.toString(), null);
    }

    private void emitContextStats(WebSocketSession wsSession, List<LlmMessage> messages) {
        int chars = AgentContextCompactor.estimateChars(messages);
        int max = loopProperties.getMaxEstimatedChars();
        int pct = max > 0 ? Math.min(100, (int) Math.round(chars * 100.0 / max)) : 0;
        events.agentStep(wsSession, "context", "上下文占用",
                null,
                chars + " / " + max + " 字符 (" + pct + "%)",
                null);
    }

    private static boolean isLatestUserMessage(List<LlmMessage> messages, String userText) {
        if (messages.isEmpty()) {
            return false;
        }
        LlmMessage last = messages.get(messages.size() - 1);
        return last.role() == LlmRole.USER && userText.equals(last.content());
    }

    private static final String SYNTHETIC_TOOL_RESULT_PLACEHOLDER =
            "[Tool result missing due to internal error]";

    /** Stream-only path: text turns without tool_use blocks in history (no tool registry). */
    static List<LlmHistoryMessageDto> streamOnlyHistory(List<LlmHistoryMessageDto> history) {
        List<LlmHistoryMessageDto> out = new ArrayList<>();
        for (LlmHistoryMessageDto item : history) {
            if (item.getRole() == null) {
                continue;
            }
            String role = item.getRole().toLowerCase();
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }
            if (item.getToolCalls() != null && !item.getToolCalls().isEmpty()) {
                continue;
            }
            String content = item.getContent();
            if (content == null || content.isBlank()) {
                continue;
            }
            out.add(item);
        }
        int start = Math.max(0, out.size() - 20);
        return out.subList(start, out.size());
    }

    /**
     * Claude ensureToolResultPairing parity for OpenAI wire format: insert synthetic
     * tool messages for missing ids instead of dropping the assistant turn.
     */
    static List<LlmHistoryMessageDto> sanitizeHistory(List<LlmHistoryMessageDto> history) {
        List<LlmHistoryMessageDto> out = new ArrayList<>();
        int i = 0;
        while (i < history.size()) {
            LlmHistoryMessageDto m = history.get(i);
            if ("assistant".equalsIgnoreCase(m.getRole())
                    && m.getToolCalls() != null
                    && !m.getToolCalls().isEmpty()) {
                java.util.LinkedHashSet<String> required = new java.util.LinkedHashSet<>();
                for (LlmHistoryToolCallDto call : m.getToolCalls()) {
                    if (call.getId() != null) {
                        required.add(call.getId());
                    }
                }
                out.add(m);

                List<LlmHistoryMessageDto> tools = new ArrayList<>();
                int j = i + 1;
                while (j < history.size() && "tool".equalsIgnoreCase(history.get(j).getRole())) {
                    tools.add(history.get(j));
                    j++;
                }

                java.util.Set<String> responded = new java.util.LinkedHashSet<>();
                for (LlmHistoryMessageDto tool : tools) {
                    if (tool.getToolCallId() != null) {
                        responded.add(tool.getToolCallId());
                    }
                }

                List<LlmHistoryMessageDto> repaired = new ArrayList<>();
                for (String id : required) {
                    if (!responded.contains(id)) {
                        LlmHistoryMessageDto synthetic = new LlmHistoryMessageDto();
                        synthetic.setRole("tool");
                        synthetic.setToolCallId(id);
                        synthetic.setToolName("tool");
                        synthetic.setContent(SYNTHETIC_TOOL_RESULT_PLACEHOLDER);
                        repaired.add(synthetic);
                    }
                }
                for (LlmHistoryMessageDto tool : tools) {
                    if (tool.getToolCallId() != null && required.contains(tool.getToolCallId())) {
                        repaired.add(tool);
                    }
                }
                out.addAll(repaired);
                i = j;
            } else if ("tool".equalsIgnoreCase(m.getRole())) {
                i++;
            } else {
                out.add(m);
                i++;
            }
        }
        return out;
    }

    private static LlmMessage toLlmMessage(LlmHistoryMessageDto item) {
        if (item.getRole() == null) {
            return null;
        }
        return switch (item.getRole().toLowerCase()) {
            case "user" -> LlmMessage.user(item.getContent() == null ? "" : item.getContent());
            case "assistant" -> {
                if (item.getToolCalls() != null && !item.getToolCalls().isEmpty()) {
                    yield LlmMessage.assistant(
                            item.getContent() == null ? "" : item.getContent(),
                            mapToolCalls(item.getToolCalls()));
                }
                yield LlmMessage.assistant(item.getContent() == null ? "" : item.getContent());
            }
            case "tool" -> LlmMessage.tool(
                    item.getToolCallId(),
                    item.getToolName(),
                    item.getContent() == null ? "" : item.getContent());
            case "system" -> LlmMessage.system(item.getContent() == null ? "" : item.getContent());
            default -> null;
        };
    }

    private static List<LlmToolCall> mapToolCalls(List<LlmHistoryToolCallDto> toolCalls) {
        List<LlmToolCall> mapped = new ArrayList<>();
        for (LlmHistoryToolCallDto call : toolCalls) {
            if (call.getId() == null || call.getName() == null) {
                continue;
            }
            mapped.add(new LlmToolCall(
                    call.getId(),
                    call.getName(),
                    call.getArgumentsJson() == null ? "{}" : call.getArgumentsJson()));
        }
        return mapped;
    }

    private ToolContext buildToolContext(ChatSendParams params) {
        Map<String, String> openDocuments = new LinkedHashMap<>();
        List<EditorDocumentSnapshotDto> snapshots = params.getEditorDocuments();
        if (snapshots != null) {
            for (EditorDocumentSnapshotDto snapshot : snapshots) {
                if (snapshot.getPath() == null || snapshot.getPath().isBlank()) {
                    continue;
                }
                if (snapshot.getContent() == null) {
                    continue;
                }
                openDocuments.put(snapshot.getPath(), snapshot.getContent());
            }
        }
        PermissionRuleSet rules = permissionRuleLoader.load(params.getWorkspaceRoot());
        String sessionId = params.getSessionId();
        String turnId = params.getUserMessageId();
        if (sessionId != null && turnId != null) {
            fileHistoryService.beginTurn(sessionId, turnId);
        }
        var extensionBundle = extensionLoader.load(params.getWorkspaceRoot());
        var features = extensionLoader.features();
        return new ToolContext(
                params.getWorkspaceRoot(),
                params.getOpenFilePath(),
                Map.copyOf(openDocuments),
                params.getPermissionMode(),
                params.getProjectRules(),
                rules,
                sessionId,
                turnId,
                features,
                extensionBundle,
                0,
                null,
                collectAttachmentReadRoots(params),
                params.getModel(),
                sessionSkillState.activeAllowedTools(sessionId));
    }

    /** CC additionalWorkingDirectories parity for ~/.agent-desktop/projects/.../attachments/ */
    private static List<String> collectAttachmentReadRoots(ChatSendParams params) {
        List<String> roots = new ArrayList<>();
        List<ChatAttachmentDto> attachments = params.getAttachments();
        if (attachments == null) {
            return roots;
        }
        for (ChatAttachmentDto attachment : attachments) {
            if (attachment == null || attachment.getPath() == null || attachment.getPath().isBlank()) {
                continue;
            }
            try {
                Path file = Path.of(attachment.getPath()).toAbsolutePath().normalize();
                roots.add(file.toString());
                Path parent = file.getParent();
                if (parent != null) {
                    roots.add(parent.toString());
                }
            } catch (Exception ignored) {
                // skip malformed attachment paths
            }
        }
        return roots;
    }

    private void runSessionHook(ToolContext toolContext, String event) {
        hookRunner.runEvent(
                toolContext.extensionBundleOrEmpty().hooks(),
                event,
                HookContext.forSession(event, toolContext.workspaceRoot(), toolContext.sessionId()));
    }

    private static String buildSystemPrompt(
            ToolContext toolContext,
            ChatSendParams params,
            AgentRunMode.Mode runMode) {
        if (runMode == AgentRunMode.Mode.STREAM_ONLY) {
            return """
                    You are Agent Desktop, a friendly coding assistant in an IDE.
                    Reply in the user's language. Keep casual greetings short (2-4 sentences).
                    For coding questions, be concise. Suggest opening a workspace folder for file analysis.
                    """;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("You are Agent Desktop, a coding assistant embedded in an IDE.\n");
        sb.append("Use the provided tools to read and modify the workspace. ");
        sb.append("To read files use read_file — not shell commands in Markdown. ");
        sb.append("To edit files use search_replace or write_file — do not paste full file contents for the user to copy.\n");
        sb.append("When the user asks you to implement or fix something, keep calling tools until the work is done on disk, ");
        sb.append("then summarize what changed.\n");
        sb.append("When the user asks for analysis only, read what you need, answer in Markdown, then stop.\n");
        sb.append("Prefer read_file offset/limit for large files. Use Markdown in final answers.\n");
        appendEnvInfo(sb, toolContext, params);
        appendGitStatus(sb, params.getGitStatusSummary());
        appendAttachmentHint(sb, params);
        if (toolContext.openFilePath() != null && !toolContext.openFilePath().isBlank()) {
            sb.append("Open file: ").append(toolContext.openFilePath()).append('\n');
        }
        if (toolContext.openDocuments() != null && !toolContext.openDocuments().isEmpty()) {
            sb.append("Open editor tabs with in-memory content are synced to read_file (unsaved buffers).\n");
        }
        sb.append("Tools: read_file, list_dir, write_file, search_replace, glob, grep");
        sb.append(" (read_file supports PDF with pages parameter, max ")
                .append(PdfLimits.PDF_MAX_PAGES_PER_READ).append(" pages per call)");
        sb.append(", Task (sub-agents), WebSearch, WebFetch when enabled in features.json");
        if (!SkillCatalog.modelInvocableSkills(toolContext.extensionBundleOrEmpty().skills()).isEmpty()) {
            sb.append(", Skill (invoke BEFORE other work when a listed skill matches; catalog is in the user message)");
        }
        sb.append(", plus MCP tools from ~/.agent-desktop/mcp.json.\n");
        sb.append("Prefer search_replace for small edits; grep/glob to locate files before editing.\n");
        sb.append("For architecture diagrams use Mermaid code blocks (```mermaid).\n");
        appendProjectRules(sb, params.getProjectRules());
        appendPermissionHint(sb, toolContext.permissionModeOrDefault());
        if (toolContext.permissionRulesOrEmpty() != null && !toolContext.permissionRulesOrEmpty().isEmpty()) {
            sb.append("Project permission rules loaded from .agent-desktop/permissions.json or .claude/settings.json.\n");
        }
        return sb.toString();
    }

    private static String permissionModeLabel(String permissionMode) {
        if ("plan".equals(permissionMode)) {
            return "Plan 只读模式";
        }
        if ("auto".equals(permissionMode)) {
            return "Agent 自动执行";
        }
        if ("ask".equals(permissionMode)) {
            return "Ask 写前确认";
        }
        return "Agent 任务模式";
    }

    private static void appendPdfReferenceHint(StringBuilder sb, ChatAttachmentDto attachment) {
        String path = attachment.getPath();
        String label = attachment.getLabel() != null ? attachment.getLabel() : path;
        int pages = attachment.getPageCount() != null ? attachment.getPageCount() : 0;
        String size = attachment.getFileSize() != null
                ? String.valueOf(attachment.getFileSize())
                : "unknown";
        sb.append("\n[PDF reference] ").append(label).append(" (").append(path).append(")");
        sb.append("\nPDF file (").append(pages > 0 ? pages + " pages" : "pages unknown")
                .append(", ").append(size).append(" bytes). ");
        sb.append("This PDF is too large or the model does not support inline PDF document blocks. ");
        sb.append("You MUST use read_file with the pages parameter to read specific page ranges ")
                .append("(e.g., pages: \"1-5\"). Do NOT call read_file without pages or it will fail. ")
                .append("Maximum ").append(PdfLimits.PDF_MAX_PAGES_PER_READ).append(" pages per request.");
        sb.append('\n');
    }

    private static void appendGitStatus(StringBuilder sb, String gitStatusSummary) {
        if (gitStatusSummary == null || gitStatusSummary.isBlank()) {
            return;
        }
        sb.append("\n--- Git status ---\n");
        sb.append(gitStatusSummary.trim()).append('\n');
    }

    private static void appendAttachmentHint(StringBuilder sb, ChatSendParams params) {
        List<ChatAttachmentDto> attachments = params.getAttachments();
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        sb.append("\n--- User attachments (this message) ---\n");
        sb.append("""
                The user attached files below. Their content is already inline in the user message \
                (PDF text, image data, or file snippets). Do NOT glob or search the workspace to locate \
                attached files. Persisted copies are under ~/.agent-desktop/projects/{cwd}/attachments/{session}/{message}/ \
                (CC image-cache parity; AD per-message store). read_file may use those absolute paths.
                """);
        for (ChatAttachmentDto attachment : attachments) {
            if (attachment == null || attachment.getPath() == null) {
                continue;
            }
            String kind = attachment.getKind() == null ? "file" : attachment.getKind();
            sb.append("- ").append(kind).append(": ").append(attachment.getPath()).append('\n');
        }
    }

    /** Claude Code computeEnvInfo — cwd + git + platform in <env> block. */
    private static void appendEnvInfo(StringBuilder sb, ToolContext toolContext, ChatSendParams params) {
        sb.append("Here is useful information about the environment you are running in:\n");
        sb.append("<env>\n");
        sb.append("Working directory: ").append(toolContext.workspaceRoot()).append('\n');
        boolean isGit = params.getGitStatusSummary() != null && !params.getGitStatusSummary().isBlank();
        sb.append("Is directory a git repo: ").append(isGit ? "Yes" : "No").append('\n');
        sb.append("Platform: ").append(System.getProperty("os.name")).append('\n');
        sb.append("OS Version: ").append(System.getProperty("os.version")).append('\n');
        sb.append("</env>\n");
    }

    private static void appendProjectRules(StringBuilder sb, String projectRules) {
        if (projectRules == null || projectRules.isBlank()) {
            return;
        }
        sb.append("\n--- Project rules (AGENTS.md / .cursorrules) ---\n");
        sb.append(projectRules.trim()).append('\n');
    }

    private static void appendPermissionHint(StringBuilder sb, String permissionMode) {
        // AD ask/auto/plan ↔ CC default/acceptEdits/plan (filesystem.ts checkWritePermissionForTool)
        sb.append("Permission mode: ").append(permissionMode).append(". ");
        if ("plan".equals(permissionMode)) {
            sb.append("Plan mode (CC plan) — read-only tools only; do not attempt write_file or search_replace.\n");
        } else if ("auto".equals(permissionMode)) {
            sb.append("Agent mode (CC acceptEdits) — file writes in workspace proceed without per-action confirmation.\n");
        } else {
            sb.append("Ask mode (CC default) — the user confirms each write_file/search_replace before execution.\n");
        }
    }
}
