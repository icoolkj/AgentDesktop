package com.agentdesktop.server.agent.attachment;

/** CC {@code pdfUtils.parsePDFPageRange} — 1-indexed inclusive pages. */
public record PdfPageRange(int firstPage, int lastPage) {

    public int pageCount() {
        if (lastPage == Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return lastPage - firstPage + 1;
    }

    public static PdfPageRange parse(String pages) {
        if (pages == null || pages.isBlank()) {
            return null;
        }
        String trimmed = pages.trim();
        if (trimmed.endsWith("-")) {
            int first = Integer.parseInt(trimmed.substring(0, trimmed.length() - 1).trim());
            if (first < 1) {
                throw new IllegalArgumentException("Invalid page range: " + pages);
            }
            return new PdfPageRange(first, Integer.MAX_VALUE);
        }
        int dash = trimmed.indexOf('-');
        if (dash < 0) {
            int page = Integer.parseInt(trimmed);
            if (page < 1) {
                throw new IllegalArgumentException("Invalid page range: " + pages);
            }
            return new PdfPageRange(page, page);
        }
        int first = Integer.parseInt(trimmed.substring(0, dash).trim());
        int last = Integer.parseInt(trimmed.substring(dash + 1).trim());
        if (first < 1 || last < 1 || last < first) {
            throw new IllegalArgumentException("Invalid page range: " + pages);
        }
        return new PdfPageRange(first, last);
    }
}
