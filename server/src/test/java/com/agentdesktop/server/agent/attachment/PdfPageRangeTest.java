package com.agentdesktop.server.agent.attachment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PdfPageRangeTest {

    @Test
    void parseSinglePage() {
        PdfPageRange range = PdfPageRange.parse("5");
        assertThat(range.firstPage()).isEqualTo(5);
        assertThat(range.lastPage()).isEqualTo(5);
    }

    @Test
    void parseClosedRange() {
        PdfPageRange range = PdfPageRange.parse("1-10");
        assertThat(range.pageCount()).isEqualTo(10);
    }
}
