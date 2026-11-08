package com.agentdesktop.server.agent.llm;

public record LlmContentPart(
        String type,
        String text,
        String imageUrl,
        String documentMime,
        String documentBase64
) {

    public static LlmContentPart text(String text) {
        return new LlmContentPart("text", text, null, null, null);
    }

    /** OpenAI-style data URL (legacy). Prefer {@link #image} for CPA / Claude. */
    public static LlmContentPart imageUrl(String url) {
        return new LlmContentPart("image_url", null, url, null, null);
    }

    /** Anthropic / Claude Code image block source fields. */
    public static LlmContentPart image(String mimeType, String base64) {
        return new LlmContentPart("image", null, null, mimeType, base64);
    }

    /** Anthropic / Claude Code document block (e.g. application/pdf). */
    public static LlmContentPart document(String mimeType, String base64) {
        return new LlmContentPart("document", null, null, mimeType, base64);
    }
}
