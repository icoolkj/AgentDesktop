package com.agentdesktop.server.agent.tool;

import com.agentdesktop.server.agent.llm.LlmContentPart;

import java.util.List;

public record ToolResult(
        boolean ok,
        String output,
        List<LlmContentPart> supplementalParts,
        String followUpUserText
) {
    /** Max chars sent back to the LLM in tool role messages. */
    public static final int MODEL_CONTEXT_LIMIT = 8_000;
    /** Max chars shown in the chat UI tool card. */
    public static final int UI_PREVIEW_LIMIT = 4_000;

    public ToolResult {
        supplementalParts = supplementalParts == null ? List.of() : List.copyOf(supplementalParts);
        followUpUserText = followUpUserText == null ? "" : followUpUserText;
    }

    public static ToolResult success(String output) {
        return new ToolResult(true, output, List.of(), "");
    }

    public static ToolResult successWithMedia(String output, List<LlmContentPart> supplementalParts) {
        return new ToolResult(true, output, supplementalParts, "");
    }

    public static ToolResult successWithFollowUp(String output, String followUpUserText) {
        return new ToolResult(true, output, List.of(), followUpUserText);
    }

    public static ToolResult failure(String message) {
        return new ToolResult(false, message, List.of(), "");
    }

    public String forModelContext() {
        return truncateForLimit(ok ? output : "ERROR: " + safeOutput(), MODEL_CONTEXT_LIMIT);
    }

    public String forUiPreview() {
        return truncateForLimit(safeOutput(), UI_PREVIEW_LIMIT);
    }

    private String safeOutput() {
        return output == null ? "" : output;
    }

    private static String truncateForLimit(String text, int max) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "\n… (" + (text.length() - max) + " chars truncated)";
    }
}
