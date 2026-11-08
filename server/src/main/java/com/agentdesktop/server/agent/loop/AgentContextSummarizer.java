package com.agentdesktop.server.agent.loop;

import com.agentdesktop.server.agent.extension.InvokedSkillRecord;
import com.agentdesktop.server.agent.extension.SessionSkillState;
import com.agentdesktop.server.agent.extension.SkillMessages;
import com.agentdesktop.server.agent.llm.LlmMessage;
import com.agentdesktop.server.agent.llm.LlmRole;
import com.agentdesktop.server.config.AgentLoopProperties;
import com.agentdesktop.server.agent.llm.LlmChatRequest;
import com.agentdesktop.server.agent.llm.LlmException;
import com.agentdesktop.server.agent.llm.LlmProvider;
import com.agentdesktop.server.agent.settings.LlmRuntimeConfig;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Context compaction — Claude Code {@code autoCompact.ts} + {@code compact.ts} parity.
 */
@Component
public class AgentContextSummarizer {

    private static final int SUMMARY_INPUT_MAX_CHARS = 24_000;
    private static final int POST_COMPACT_SKILL_CHARS = 4_000;

    private final LlmProvider llmProvider;
    private final AgentLoopProperties loopProperties;
    private final SessionSkillState sessionSkillState;

    public AgentContextSummarizer(
            LlmProvider llmProvider,
            AgentLoopProperties loopProperties,
            SessionSkillState sessionSkillState) {
        this.llmProvider = llmProvider;
        this.loopProperties = loopProperties;
        this.sessionSkillState = sessionSkillState;
    }

    public record CompactResult(List<LlmMessage> messages, boolean changed, String summarySnippet) {
    }

    public CompactResult apply(List<LlmMessage> messages, LlmRuntimeConfig config, AgentRunContext run, CompactMode mode) {
        int maxChars = loopProperties.getMaxEstimatedChars();
        int threshold = mode == CompactMode.MANUAL || mode == CompactMode.REACTIVE
                ? maxChars
                : loopProperties.autoCompactThresholdChars();

        List<LlmMessage> working = AgentContextCompactor.compact(new ArrayList<>(messages), threshold);
        int chars = AgentContextCompactor.estimateChars(working);
        if (mode == CompactMode.AUTO && !loopProperties.isAutoCompactEnabled()) {
            return new CompactResult(working, !working.equals(messages), null);
        }
        if (chars <= threshold && mode == CompactMode.AUTO) {
            return new CompactResult(working, !working.equals(messages), null);
        }
        if (chars <= maxChars && mode == CompactMode.AUTO) {
            return new CompactResult(working, !working.equals(messages), null);
        }
        if (mode == CompactMode.REACTIVE && chars <= maxChars && working.equals(messages)) {
            // Still over API limit — force summarize even if under local estimate
        } else if (mode == CompactMode.REACTIVE && chars <= maxChars) {
            return new CompactResult(working, !working.equals(messages), null);
        }

        run.checkNotCancelled();
        SummarizeResult summarized = summarizeMiddle(working, config, run, mode);
        List<LlmMessage> rebuilt = appendInvokedSkillReminders(summarized.messages(), run.transcriptSessionId());
        return new CompactResult(rebuilt, true, summarized.summarySnippet());
    }

    private record SummarizeResult(List<LlmMessage> messages, String summarySnippet) {
    }

    private List<LlmMessage> appendInvokedSkillReminders(List<LlmMessage> messages, String sessionId) {
        List<InvokedSkillRecord> invoked = sessionSkillState.invokedSkills(sessionId);
        if (invoked.isEmpty()) {
            return messages;
        }
        List<LlmMessage> rebuilt = new ArrayList<>(messages);
        invoked.stream()
                .sorted(Comparator.comparingLong(InvokedSkillRecord::invokedAtEpochMs).reversed())
                .forEach(skill -> {
                    String body = skill.content();
                    if (body.length() > POST_COMPACT_SKILL_CHARS) {
                        body = body.substring(0, POST_COMPACT_SKILL_CHARS) + "\n…";
                    }
                    rebuilt.add(LlmMessage.user(SkillMessages.wrapSystemReminder(
                            "Previously invoked skill '" + skill.name() + "' (retained after compact):\n\n" + body)));
                });
        return rebuilt;
    }

    private SummarizeResult summarizeMiddle(
            List<LlmMessage> messages,
            LlmRuntimeConfig config,
            AgentRunContext run,
            CompactMode mode) {
        int headerEnd = findHeaderEnd(messages);
        int tailStart = findTailStart(messages);
        if (tailStart <= headerEnd) {
            return new SummarizeResult(messages, null);
        }

        String digest = buildDigest(messages, headerEnd, tailStart);
        if (digest.isBlank()) {
            return new SummarizeResult(messages, null);
        }

        run.checkNotCancelled();
        String summary = callSummaryLlm(digest, config, run, mode);

        List<LlmMessage> rebuilt = new ArrayList<>(messages.subList(0, headerEnd));
        rebuilt.add(LlmMessage.user("[Earlier conversation summarized for context limits]\n" + summary));
        rebuilt.addAll(messages.subList(tailStart, messages.size()));
        return new SummarizeResult(rebuilt, summary);
    }

    private static int findHeaderEnd(List<LlmMessage> messages) {
        int lastSystem = -1;
        int firstUser = -1;
        for (int i = 0; i < messages.size(); i++) {
            LlmRole role = messages.get(i).role();
            if (role == LlmRole.SYSTEM) {
                lastSystem = i;
            } else if (role == LlmRole.USER && firstUser < 0) {
                firstUser = i;
            }
        }
        if (firstUser >= 0) {
            return firstUser + 1;
        }
        return lastSystem + 1;
    }

    private static int findTailStart(List<LlmMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            LlmMessage message = messages.get(i);
            if (message.role() == LlmRole.ASSISTANT && !message.toolCalls().isEmpty()) {
                return i;
            }
        }
        return messages.size();
    }

    private static String buildDigest(List<LlmMessage> messages, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            LlmMessage message = messages.get(i);
            if (message.content() == null || message.content().isBlank()) {
                continue;
            }
            if (AgentContextCompactor.isProtectedSkillMessage(message)) {
                continue;
            }
            sb.append('[').append(message.role()).append("] ");
            if (message.toolName() != null) {
                sb.append(message.toolName()).append(": ");
            }
            sb.append(message.content()).append("\n\n");
            if (sb.length() > SUMMARY_INPUT_MAX_CHARS) {
                sb.setLength(SUMMARY_INPUT_MAX_CHARS);
                sb.append("\n…(truncated)");
                break;
            }
        }
        return sb.toString().trim();
    }

    private String callSummaryLlm(
            String digest,
            LlmRuntimeConfig config,
            AgentRunContext run,
            CompactMode mode) {
        String instruction = mode == CompactMode.MANUAL
                ? "Summarize the conversation for a coding agent. Keep file paths, decisions, and open tasks."
                : "Summarize tool outputs for a coding agent. Keep file paths, key findings, and errors.";
        List<LlmMessage> summaryMessages = List.of(
                LlmMessage.system(instruction + " Be concise (bullet points). Do not invent information."),
                LlmMessage.user(digest));

        LlmChatRequest request = LlmChatRequest.builder()
                .messages(summaryMessages)
                .model(config.model())
                .tools(List.of())
                .toolChoice("none")
                .temperature(Math.min(config.temperature(), 0.3))
                .maxTokens(Math.min(config.maxTokens(), 2048))
                .stream(false)
                .build();

        try {
            run.checkNotCancelled();
            var response = CompletableFuture
                    .supplyAsync(() -> llmProvider.complete(request))
                    .orTimeout(config.timeoutSeconds(), TimeUnit.SECONDS)
                    .join();
            String content = response.content();
            return content == null || content.isBlank()
                    ? "(summary unavailable — middle context dropped)"
                    : content.trim();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof TimeoutException) {
                throw new LlmException("Context summarization timed out");
            }
            if (cause instanceof LlmException llmException) {
                throw llmException;
            }
            throw new LlmException("Context summarization failed: " + cause.getMessage(), cause);
        }
    }
}
