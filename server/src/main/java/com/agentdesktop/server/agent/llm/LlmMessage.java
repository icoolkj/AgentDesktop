package com.agentdesktop.server.agent.llm;

import java.util.List;

public record LlmMessage(
        LlmRole role,
        String content,
        List<LlmToolCall> toolCalls,
        String toolCallId,
        String toolName,
        List<LlmContentPart> contentParts
) {
    public LlmMessage {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        contentParts = contentParts == null ? List.of() : List.copyOf(contentParts);
    }

    public static LlmMessage system(String content) {
        return new LlmMessage(LlmRole.SYSTEM, content, List.of(), null, null, List.of());
    }

    public static LlmMessage user(String content) {
        return new LlmMessage(LlmRole.USER, content, List.of(), null, null, List.of());
    }

    public static LlmMessage userMultimodal(String content, List<LlmContentPart> contentParts) {
        return new LlmMessage(LlmRole.USER, content, List.of(), null, null, contentParts);
    }

    public static LlmMessage assistant(String content) {
        return new LlmMessage(LlmRole.ASSISTANT, content, List.of(), null, null, List.of());
    }

    public static LlmMessage assistant(String content, List<LlmToolCall> toolCalls) {
        return new LlmMessage(LlmRole.ASSISTANT, content, toolCalls, null, null, List.of());
    }

    public static LlmMessage tool(String toolCallId, String toolName, String content) {
        return new LlmMessage(LlmRole.TOOL, content, List.of(), toolCallId, toolName, List.of());
    }
}
