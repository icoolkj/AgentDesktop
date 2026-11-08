package com.agentdesktop.server.agent.attachment;

import com.agentdesktop.server.agent.llm.LlmContentPart;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * CC parity: {@code src/utils/pdf.ts} + {@code FileReadTool} PDF branch.
 * Primary: poppler (pdfinfo/pdftoppm). Fallback: PDFBox page count + render.
 */
@Component
public class PdfSupport {

    private static final Logger log = LoggerFactory.getLogger(PdfSupport.class);

    private volatile Boolean pdftoppmAvailable;

    public record PdfInfo(long sizeBytes, Integer pageCount, Integer effectivePageCount) {
    }

    public record PdfReadResult(String summary, List<LlmContentPart> supplementalParts) {
    }

    public void validatePdfHeader(Path file) {
        try {
            byte[] header = Files.readAllBytes(file);
            if (header.length < 5) {
                throw new IllegalArgumentException("PDF file is empty: " + file);
            }
            String magic = new String(header, 0, 5, StandardCharsets.US_ASCII);
            if (!magic.startsWith("%PDF-")) {
                throw new IllegalArgumentException(
                        "File is not a valid PDF (missing %PDF- header): " + file.getFileName());
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot read PDF: " + e.getMessage(), e);
        }
    }

    public PdfInfo inspect(Path file) {
        try {
            long size = Files.size(file);
            if (size == 0) {
                throw new IllegalArgumentException("PDF file is empty: " + file);
            }
            validatePdfHeader(file);
            Integer pageCount = getPageCount(file);
            int effective = pageCount != null
                    ? pageCount
                    : (int) Math.ceil(size / (100.0 * 1024));
            return new PdfInfo(size, pageCount, effective);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage() == null ? e.toString() : e.getMessage(), e);
        }
    }

    public Integer getPageCount(Path file) {
        Integer fromPdfInfo = pageCountViaPdfInfo(file);
        if (fromPdfInfo != null) {
            return fromPdfInfo;
        }
        return pageCountViaPdfBox(file);
    }

    /**
     * CC FileReadTool PDF branch — structural limits only; CPA Payload 规则 handle per-model wire format.
     */
    public PdfReadResult readPdf(Path file, String pagesParam) throws Exception {
        validatePdfHeader(file);
        long size = Files.size(file);
        if (size > PdfLimits.PDF_TARGET_RAW_SIZE && pagesParam == null) {
            throw new IllegalArgumentException(
                    "PDF file exceeds maximum allowed size of "
                            + (PdfLimits.PDF_TARGET_RAW_SIZE / 1024 / 1024) + " MB.");
        }

        if (pagesParam != null && !pagesParam.isBlank()) {
            PdfPageRange range = PdfPageRange.parse(pagesParam);
            int span = range.lastPage() == Integer.MAX_VALUE
                    ? PdfLimits.PDF_MAX_PAGES_PER_READ + 1
                    : range.pageCount();
            if (span > PdfLimits.PDF_MAX_PAGES_PER_READ) {
                throw new IllegalArgumentException(
                        "Page range \"" + pagesParam + "\" exceeds maximum of "
                                + PdfLimits.PDF_MAX_PAGES_PER_READ + " pages per request.");
            }
            List<LlmContentPart> images = extractPageImages(file, range);
            String summary = "PDF pages extracted: " + images.size() + " page(s) from "
                    + file.getFileName() + " (" + formatSize(size) + "), pages=" + pagesParam;
            return new PdfReadResult(summary, images);
        }

        Integer pageCount = getPageCount(file);
        if (pageCount != null && pageCount > PdfLimits.PDF_AT_MENTION_INLINE_THRESHOLD) {
            throw new IllegalArgumentException(
                    "This PDF has " + pageCount + " pages, which is too many to read at once. "
                            + "Use the pages parameter to read specific page ranges (e.g., pages: \"1-5\"). "
                            + "Maximum " + PdfLimits.PDF_MAX_PAGES_PER_READ + " pages per request.");
        }

        if (size > PdfLimits.PDF_EXTRACT_SIZE_THRESHOLD) {
            log.debug("Large PDF {} ({} bytes) — sending full document block (CC path for native PDF models)", file, size);
        }

        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length > PdfLimits.PDF_TARGET_RAW_SIZE) {
            throw new IllegalArgumentException(
                    "PDF file exceeds maximum allowed size of "
                            + (PdfLimits.PDF_TARGET_RAW_SIZE / 1024 / 1024) + " MB.");
        }
        String base64 = Base64.getEncoder().encodeToString(bytes);
        String summary = "PDF file read: " + file.getFileName() + " (" + formatSize(size) + ")";
        List<LlmContentPart> parts = List.of(LlmContentPart.document("application/pdf", base64));
        return new PdfReadResult(summary, parts);
    }

    public List<LlmContentPart> extractPageImages(Path file, PdfPageRange range) throws Exception {
        if (Files.size(file) > PdfLimits.PDF_MAX_EXTRACT_SIZE) {
            throw new IllegalArgumentException(
                    "PDF file exceeds maximum allowed size for page extraction ("
                            + (PdfLimits.PDF_MAX_EXTRACT_SIZE / 1024 / 1024) + " MB).");
        }
        if (isPdftoppmAvailable()) {
            try {
                return extractViaPdftoppm(file, range);
            } catch (Exception e) {
                log.warn("pdftoppm failed, falling back to PDFBox: {}", e.getMessage());
            }
        }
        return extractViaPdfBox(file, range);
    }

    private List<LlmContentPart> extractViaPdftoppm(Path file, PdfPageRange range) throws Exception {
        Path outputDir = Files.createTempDirectory("ad-pdf-");
        try {
            String prefix = outputDir.resolve("page").toString();
            List<String> cmd = new ArrayList<>();
            cmd.add("pdftoppm");
            cmd.add("-jpeg");
            cmd.add("-r");
            cmd.add("100");
            if (range.firstPage() > 0) {
                cmd.add("-f");
                cmd.add(String.valueOf(range.firstPage()));
            }
            if (range.lastPage() != Integer.MAX_VALUE) {
                cmd.add("-l");
                cmd.add(String.valueOf(range.lastPage()));
            }
            cmd.add(file.toAbsolutePath().toString());
            cmd.add(prefix);

            Process process = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();
            String errOut = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("pdftoppm timed out");
            }
            if (process.exitValue() != 0) {
                if (errOut.toLowerCase(Locale.ROOT).contains("password")) {
                    throw new IllegalArgumentException(
                            "PDF is password-protected. Please provide an unprotected version.");
                }
                throw new IllegalStateException("pdftoppm failed: " + errOut);
            }

            try (Stream<Path> stream = Files.list(outputDir)) {
                List<Path> images = stream
                        .filter(p -> p.getFileName().toString().endsWith(".jpg"))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .toList();
                if (images.isEmpty()) {
                    throw new IllegalStateException("pdftoppm produced no output pages.");
                }
                List<LlmContentPart> parts = new ArrayList<>();
                for (Path img : images) {
                    byte[] data = Files.readAllBytes(img);
                    parts.add(LlmContentPart.image("image/jpeg", Base64.getEncoder().encodeToString(data)));
                }
                return parts;
            }
        } finally {
            deleteRecursively(outputDir);
        }
    }

    private List<LlmContentPart> extractViaPdfBox(Path file, PdfPageRange range) throws Exception {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            int total = document.getNumberOfPages();
            int first = Math.max(1, range.firstPage());
            int last = range.lastPage() == Integer.MAX_VALUE ? total : Math.min(range.lastPage(), total);
            if (first > total) {
                throw new IllegalArgumentException("Page range starts beyond document length (" + total + " pages)");
            }
            PDFRenderer renderer = new PDFRenderer(document);
            List<LlmContentPart> parts = new ArrayList<>();
            for (int page = first; page <= last; page++) {
                BufferedImage image = renderer.renderImageWithDPI(page - 1, 100, ImageType.RGB);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "jpg", baos);
                parts.add(LlmContentPart.image("image/jpeg", Base64.getEncoder().encodeToString(baos.toByteArray())));
            }
            if (parts.isEmpty()) {
                throw new IllegalStateException("PDF page extraction produced no pages.");
            }
            return parts;
        }
    }

    private Integer pageCountViaPdfInfo(Path file) {
        try {
            Process process = new ProcessBuilder("pdfinfo", file.toAbsolutePath().toString())
                    .redirectErrorStream(true)
                    .start();
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) {
                return null;
            }
            for (String line : out.split("\n")) {
                if (line.startsWith("Pages:")) {
                    String num = line.substring("Pages:".length()).trim();
                    return Integer.parseInt(num.split("\\s+")[0]);
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }

    private Integer pageCountViaPdfBox(Path file) {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            return document.getNumberOfPages();
        } catch (Exception e) {
            log.debug("PDFBox page count failed for {}: {}", file, e.getMessage());
            return null;
        }
    }

    private boolean isPdftoppmAvailable() {
        if (pdftoppmAvailable != null) {
            return pdftoppmAvailable;
        }
        synchronized (this) {
            if (pdftoppmAvailable != null) {
                return pdftoppmAvailable;
            }
            try {
                Process process = new ProcessBuilder("pdftoppm", "-v")
                        .redirectErrorStream(true)
                        .start();
                process.waitFor(5, TimeUnit.SECONDS);
                pdftoppmAvailable = true;
            } catch (Exception e) {
                pdftoppmAvailable = false;
            }
            return pdftoppmAvailable;
        }
    }

    private static void deleteRecursively(Path dir) {
        try {
            if (!Files.exists(dir)) {
                return;
            }
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                        // best effort
                    }
                });
            }
        } catch (Exception ignored) {
            // best effort
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
    }
}
