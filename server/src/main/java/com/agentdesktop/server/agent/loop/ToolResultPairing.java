package com.agentdesktop.server.agent.loop;

import com.agentdesktop.server.agent.llm.LlmMessage;
import com.agentdesktop.server.agent.llm.LlmRole;
import com.agentdesktop.server.agent.llm.LlmToolCall;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * OpenAI-gateway equivalent of Claude Code {@code ensureToolResultPairing} (messages.ts).
 * Repairs tool_use / tool_result mismatches before each model call so the API never sees
 * dangling tool blocks (which deadlocks the session on resume).
 */
final class ToolResultPairing {

    private static final String MISSING_RESULT = "[Tool result unavailable — interrupted or not executed]";

    private ToolResultPairing() {
    }

    static void repair(List<LlmMessage> messages) {
        dedupeAssistantToolCalls(messages);
        insertMissingToolResults(messages);
        removeOrphanToolResults(messages);
    }

    /** Synthetic tool_result blocks for cancelled / failed tool rounds (query.ts yieldMissingToolResultBlocks). */
    static void insertSyntheticErrors(List<LlmMessage> messages, String errorMessage) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            LlmMessage message = messages.get(i);
            if (message.role() != LlmRole.ASSISTANT || message.toolCalls().isEmpty()) {
                continue;
            }
            Set<String> existing = toolResultIdsAfter(messages, i);
            int insertAt = i + 1;
            while (insertAt < messages.size() && messages.get(insertAt).role() == LlmRole.TOOL) {
                insertAt++;
            }
            for (LlmToolCall call : message.toolCalls()) {
                if (!existing.contains(call.id())) {
                    messages.add(insertAt++, LlmMessage.tool(call.id(), call.name(), errorMessage));
                }
            }
        }
    }

    private static void dedupeAssistantToolCalls(List<LlmMessage> messages) {
        Set<String> seenIds = new HashSet<>();
        for (int i = 0; i < messages.size(); i++) {
            LlmMessage message = messages.get(i);
            if (message.role() != LlmRole.ASSISTANT || message.toolCalls().isEmpty()) {
                continue;
            }
            List<LlmToolCall> unique = new ArrayList<>();
            for (LlmToolCall call : message.toolCalls()) {
                if (seenIds.add(call.id())) {
                    unique.add(call);
                }
            }
            if (unique.size() != message.toolCalls().size()) {
                messages.set(i, LlmMessage.assistant(message.content(), unique));
            }
        }
    }

    private static void insertMissingToolResults(List<LlmMessage> messages) {
        for (int i = 0; i < messages.size(); i++) {
            LlmMessage message = messages.get(i);
            if (message.role() != LlmRole.ASSISTANT || message.toolCalls().isEmpty()) {
                continue;
            }
            Set<String> found = toolResultIdsAfter(messages, i);
            int insertAt = i + 1;
            while (insertAt < messages.size() && messages.get(insertAt).role() == LlmRole.TOOL) {
                insertAt++;
            }
            for (LlmToolCall call : message.toolCalls()) {
                if (!found.contains(call.id())) {
                    messages.add(insertAt++, LlmMessage.tool(call.id(), call.name(), MISSING_RESULT));
                }
            }
        }
    }

    private static void removeOrphanToolResults(List<LlmMessage> messages) {
        Set<String> expectedIds = new HashSet<>();
        for (LlmMessage message : messages) {
            if (message.role() == LlmRole.ASSISTANT) {
                for (LlmToolCall call : message.toolCalls()) {
                    expectedIds.add(call.id());
                }
            }
        }
        messages.removeIf(message ->
                message.role() == LlmRole.TOOL
                        && message.toolCallId() != null
                        && !expectedIds.contains(message.toolCallId()));
    }

    private static Set<String> toolResultIdsAfter(List<LlmMessage> messages, int assistantIndex) {
        Set<String> found = new HashSet<>();
        for (int j = assistantIndex + 1; j < messages.size(); j++) {
            LlmMessage next = messages.get(j);
            if (next.role() != LlmRole.TOOL) {
                break;
            }
            if (next.toolCallId() != null) {
                found.add(next.toolCallId());
            }
        }
        return found;
    }
}
