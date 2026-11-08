package com.agentdesktop.server.agent.attachment;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfSupportTest {

    @Test
    void readSmallPdfProducesDocumentBlock() throws Exception {
        Path pdf = Files.createTempFile("ad-test", ".pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("hello pdf");
                cs.endText();
            }
            doc.save(pdf.toFile());
        }

        PdfSupport support = new PdfSupport();
        PdfSupport.PdfReadResult result = support.readPdf(pdf, null);
        assertThat(result.summary()).contains("PDF file read");
        assertThat(result.supplementalParts()).hasSize(1);
        assertThat(result.supplementalParts().get(0).type()).isEqualTo("document");
    }

    @Test
    void rejectsLargePdfWithoutPages() throws Exception {
        Path pdf = Files.createTempFile("ad-test", ".pdf");
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < 12; i++) {
                doc.addPage(new PDPage());
            }
            doc.save(pdf.toFile());
        }

        PdfSupport support = new PdfSupport();
        assertThatThrownBy(() -> support.readPdf(pdf, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too many to read at once");
    }
}
