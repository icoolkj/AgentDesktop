package com.agentdesktop.server.agent.loop;

/**
 * Incrementally strips internal markup from streamed tokens before sending to the UI.
 */
final class StreamingTextSanitizer {

    private final StringBuilder raw = new StringBuilder();
    private int emittedLength;

    void appendRaw(String delta) {
        if (delta != null && !delta.isEmpty()) {
            raw.append(delta);
        }
    }

    /** New user-visible text produced by this delta (may be empty while buffering a DSML tag). */
    String consumeVisibleDelta(String delta) {
        appendRaw(delta);
        return emitNewSuffix();
    }

    String flushRemainder() {
        return emitNewSuffix();
    }

    String visibleText() {
        return AssistantContentFilter.sanitizeForUser(raw.toString());
    }

    private String emitNewSuffix() {
        String clean = AssistantContentFilter.sanitizeForUser(raw.toString());
        if (clean.length() <= emittedLength) {
            return "";
        }
        String chunk = clean.substring(emittedLength);
        emittedLength = clean.length();
        return chunk;
    }
}
