package com.agentdesktop.server.agent.attachment;

/** Claude Code {@code src/constants/apiLimits.ts} PDF limits. */
public final class PdfLimits {

    public static final int PDF_TARGET_RAW_SIZE = 20 * 1024 * 1024;
    public static final int API_PDF_MAX_PAGES = 100;
    public static final int PDF_EXTRACT_SIZE_THRESHOLD = 3 * 1024 * 1024;
    public static final int PDF_MAX_EXTRACT_SIZE = 100 * 1024 * 1024;
    public static final int PDF_MAX_PAGES_PER_READ = 20;
    public static final int PDF_AT_MENTION_INLINE_THRESHOLD = 10;

    private PdfLimits() {
    }
}
