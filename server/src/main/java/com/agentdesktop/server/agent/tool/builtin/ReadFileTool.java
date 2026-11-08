package com.agentdesktop.server.agent.tool.builtin;

import com.agentdesktop.server.agent.attachment.PdfSupport;
import com.agentdesktop.server.agent.checkpoint.ReadFileStateService;
import com.agentdesktop.server.agent.llm.LlmContentPart;
import com.agentdesktop.server.agent.llm.LlmToolDefinition;
import com.agentdesktop.server.agent.settings.LlmRuntimeConfig;
import com.agentdesktop.server.agent.settings.LlmSettingsService;
import com.agentdesktop.server.agent.tool.Tool;
import com.agentdesktop.server.agent.tool.ToolContext;
import com.agentdesktop.server.agent.tool.ToolResult;
import com.agentdesktop.server.agent.tool.WorkspacePaths;
import com.agentdesktop.server.agent.tool.WorkspaceTextFiles;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class ReadFileTool implements Tool {

    private static final int MAX_BYTES = 512 * 1024;
    /** Default max lines per read (Cursor-style Read tool). */
    private static final int DEFAULT_LINE_LIMIT = 200;

    private final ObjectMapper objectMapper;
    private final ReadFileStateService readFileStateService;
    private final PdfSupport pdfSupport;
    private final LlmSettingsService llmSettingsService;

    public ReadFileTool(
            ObjectMapper objectMapper,
            ReadFileStateService readFileStateService,
            PdfSupport pdfSupport,
            LlmSettingsService llmSettingsService) {
        this.objectMapper = objectMapper;
        this.readFileStateService = readFileStateService;
        this.pdfSupport = pdfSupport;
        this.llmSettingsService = llmSettingsService;
    }

    @Override
    public String name() {
        return "read_file";
    }

    @Override
    public String description() {
        return """
                Read a UTF-8 text file, PDF, or image path in the workspace (or persisted attachment store).
                Use offset/limit for large text files. For PDFs use pages (e.g. "1-5", max 20 pages per call).
                """;
    }

    @Override
    public boolean concurrencySafe() {
        return true;
    }

    @Override
    public LlmToolDefinition definition() {
        return new LlmToolDefinition(
                name(),
                description(),
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "path", Map.of(
                                        "type", "string",
                                        "description", "File path relative to workspace root or absolute attachment path"
                                ),
                                "offset", Map.of(
                                        "type", "integer",
                                        "description", "1-based line number to start reading text files (default 1)"
                                ),
                                "limit", Map.of(
                                        "type", "integer",
                                        "description", "Max lines to read for text files (default " + DEFAULT_LINE_LIMIT + ")"
                                ),
                                "pages", Map.of(
                                        "type", "string",
                                        "description", "Page range for PDF files (e.g. \"1-5\", \"3\", \"10-20\"). Max 20 pages per request."
                                )
                        ),
                        "required", List.of("path")
                )
        );
    }

    @Override
    public ToolResult execute(String argumentsJson, ToolContext context) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
            String pathArg = args.path("path").asText("");
            if (pathArg.isBlank()) {
                return ToolResult.failure("Missing required argument: path");
            }

            Path file = WorkspacePaths.resolve(context, pathArg);
            if (!Files.exists(file)) {
                return ToolResult.failure("File not found: " + pathArg);
            }
            if (!Files.isRegularFile(file)) {
                return ToolResult.failure("Not a regular file: " + pathArg);
            }

            if (isPdfPath(file)) {
                return readPdfFile(file, pathArg, args.path("pages").asText(null), context);
            }

            if (isImagePath(file)) {
                return readImageFile(file, pathArg, context);
            }

            int offset = Math.max(1, args.path("offset").asInt(1));
            int limit = args.has("limit") ? Math.max(1, args.path("limit").asInt()) : DEFAULT_LINE_LIMIT;

            String content;
            try {
                content = WorkspaceTextFiles.readString(file, context);
            } catch (IllegalArgumentException notFound) {
                content = null;
            }

            List<String> allLines;
            if (content != null) {
                allLines = content.isEmpty() ? List.of() : content.replace("\r\n", "\n").lines().toList();
            } else {
                long size = Files.size(file);
                if (size > MAX_BYTES) {
                    return ToolResult.failure("File too large (" + size + " bytes). Use offset/limit to read a portion.");
                }
                allLines = Files.readAllLines(file, StandardCharsets.UTF_8);
            }

            int totalLines = allLines.size();
            boolean fromBuffer = content != null && context.openDocumentContent(file) != null;
            if (totalLines == 0) {
                readFileStateService.recordRead(
                        context.sessionId(),
                        file,
                        "",
                        offset,
                        limit,
                        0,
                        fromBuffer);
                return ToolResult.success(fromBuffer ? "(empty file, from unsaved editor buffer)" : "(empty file)");
            }

            int startIndex = Math.min(offset - 1, totalLines);
            int endIndex = Math.min(startIndex + limit, totalLines);

            StringBuilder sb = new StringBuilder();
            if (fromBuffer) {
                sb.append("(reading unsaved editor buffer)\n");
            }
            sb.append("File: ").append(pathArg).append(" (lines ").append(startIndex + 1)
                    .append('-').append(endIndex).append(" of ").append(totalLines).append(")\n");

            for (int i = startIndex; i < endIndex; i++) {
                sb.append(String.format("%6d| %s%n", i + 1, allLines.get(i)));
            }

            if (endIndex < totalLines) {
                sb.append("\n… ").append(totalLines - endIndex)
                        .append(" more lines; call read_file with offset=").append(endIndex + 1);
            }

            readFileStateService.recordRead(
                    context.sessionId(),
                    file,
                    content != null ? content : String.join("\n", allLines),
                    offset,
                    limit,
                    totalLines,
                    fromBuffer);

            return ToolResult.success(sb.toString());
        } catch (Exception e) {
            return ToolResult.failure(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private ToolResult readPdfFile(Path file, String pathArg, String pagesParam, ToolContext context) {
        try {
            PdfSupport.PdfReadResult result = pdfSupport.readPdf(file, pagesParam);
            readFileStateService.recordRead(
                    context.sessionId(),
                    file,
                    result.summary(),
                    1,
                    1,
                    1,
                    false);
            if (result.supplementalParts().isEmpty()) {
                return ToolResult.success(result.summary());
            }
            return ToolResult.successWithMedia(result.summary(), result.supplementalParts());
        } catch (IllegalArgumentException e) {
            return ToolResult.failure(e.getMessage());
        } catch (Exception e) {
            return ToolResult.failure(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private ToolResult readImageFile(Path file, String pathArg, ToolContext context) {
        try {
            long size = Files.size(file);
            if (size > MAX_BYTES) {
                return ToolResult.failure("Image too large (" + size + " bytes).");
            }
            byte[] bytes = Files.readAllBytes(file);
            String mime = guessImageMime(file);
            String base64 = Base64.getEncoder().encodeToString(bytes);
            String summary = "Image file read: " + file.getFileName() + " (" + size + " bytes)";
            readFileStateService.recordRead(
                    context.sessionId(),
                    file,
                    summary,
                    1,
                    1,
                    1,
                    false);
            return ToolResult.successWithMedia(summary, List.of(LlmContentPart.image(mime, base64)));
        } catch (Exception e) {
            return ToolResult.failure(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private static String guessImageMime(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/png";
    }

    private static boolean isImagePath(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".gif") || name.endsWith(".webp");
    }

    private static boolean isPdfPath(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".pdf");
    }
}
