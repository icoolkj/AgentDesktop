package com.agentdesktop.server.agent.loop;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strips model-internal markup from user-visible assistant text when a misconfigured gateway
 * leaks it into {@code content}. Does not parse markup into tools — see {@link ToolCallResolver}.
 */
final class AssistantContentFilter {

    private static final Pattern DSML_SEGMENT = Pattern.compile(
            "(?s)<\\|DSML\\|[^\\n]*(?:\\n.*?)*?(?:</\\|DSML\\|[^>\\n]*>|(?=<\\|DSML\\|)|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LEGACY_DSML_SEGMENT = Pattern.compile(
            "(?s)<[^>\\n]*DSML[^>\\n]*>.*?(?:</[^>\\n]*DSML[^>\\n]*>|(?=<[^>\\n]*DSML))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DSML_PIPE_TAG = Pattern.compile("<\\|DSML\\|[^\\n]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern DSML_TAG = Pattern.compile("<[^>\\n]*DSML[^>\\n]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern INCOMPLETE_MARKUP = Pattern.compile(
            "(?is)(?:<\\s*\\|?\\s*\\|?\\s*DSML|tool_calls?e?\\s*=|\\|DSML\\|).*$");
    private static final Pattern INVOKE_LINE = Pattern.compile("(?m)^\\s*<[^>\\n]*invoke[^>\\n]*>.*$", Pattern.CASE_INSENSITIVE);

    private AssistantContentFilter() {
    }

    static boolean containsInternalMarkup(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String upper = text.toUpperCase();
        return upper.contains("DSML")
                || upper.contains("<INVOKE")
                || upper.contains("TOOL_CALLS")
                || upper.contains("TOOL_CALLSE");
    }

    static String sanitizeForUser(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String result = text;
        for (int i = 0; i < 8; i++) {
            String next = DSML_SEGMENT.matcher(result).replaceAll("");
            next = LEGACY_DSML_SEGMENT.matcher(next).replaceAll("");
            if (next.equals(result)) {
                break;
            }
            result = next;
        }
        result = DSML_PIPE_TAG.matcher(result).replaceAll("");
        result = DSML_TAG.matcher(result).replaceAll("");
        result = INVOKE_LINE.matcher(result).replaceAll("");
        result = stripFromMarkupStart(result);
        result = INCOMPLETE_MARKUP.matcher(result).replaceAll("");
        return result.strip();
    }

    static boolean isUserVisible(String text) {
        return isCleanUserText(sanitizeForUser(text));
    }

    /** Sanitized text safe to show — no internal markup remnants. */
    static boolean isCleanUserText(String sanitized) {
        return sanitized != null
                && !sanitized.isBlank()
                && !containsInternalMarkup(sanitized);
    }

    private static String stripFromMarkupStart(String text) {
        int idx = findInternalMarkupStart(text);
        if (idx >= 0) {
            return text.substring(0, idx).strip();
        }
        return text;
    }

    private static int findInternalMarkupStart(String text) {
        int earliest = -1;
        String[] markers = {"<|DSML|", "<| DSML", "|DSML|", "TOOL_CALLSE", "TOOL_CALLS"};
        String upper = text.toUpperCase();
        for (String marker : markers) {
            int i = upper.indexOf(marker);
            if (i >= 0) {
                earliest = earliest < 0 ? i : Math.min(earliest, i);
            }
        }
        Matcher spaced = Pattern.compile("(?i)<\\s*\\|?\\s*\\|?\\s*DSML").matcher(text);
        if (spaced.find()) {
            earliest = earliest < 0 ? spaced.start() : Math.min(earliest, spaced.start());
        }
        return earliest;
    }
}
