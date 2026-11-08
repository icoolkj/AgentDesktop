package com.agentdesktop.server.agent.tool.builtin;

import com.agentdesktop.server.agent.llm.LlmToolDefinition;
import com.agentdesktop.server.agent.tool.RipgrepRunner;
import com.agentdesktop.server.agent.tool.Tool;
import com.agentdesktop.server.agent.tool.ToolContext;
import com.agentdesktop.server.agent.tool.ToolResult;
import com.agentdesktop.server.agent.tool.WorkspacePaths;
import com.agentdesktop.server.agent.tool.WorkspaceScan;
import com.agentdesktop.server.agent.tool.WorkspaceTextFiles;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Workspace grep — prefers ripgrep (Claude Code {@code GrepTool.ts} / {@code ripgrep.ts}),
 * falls back to Java scan for environments without {@code rg}.
 */
@Component
public class GrepTool implements Tool {

    /** Claude Code {@code DEFAULT_HEAD_LIMIT}. */
    private static final int DEFAULT_HEAD_LIMIT = 250;
    private static final int MAX_FILE_BYTES = 512 * 1024;
    /** Java fallback only — ripgrep has its own 20s process timeout. */
    private static final int MAX_FILES_SCANNED = 400;

    private static final String[] VCS_EXCLUDES = {
            ".git", ".svn", ".hg", ".bzr", ".jj", ".sl"
    };

    private final ObjectMapper objectMapper;

    public GrepTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "grep";
    }

    @Override
    public String description() {
        return """
                Search file contents in the workspace (ripgrep when available).
                Returns file:line:content matches. Skips node_modules, .git, target, etc.
                head_limit defaults to 250; pass 0 for unlimited (use sparingly).
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
                                "pattern", Map.of(
                                        "type", "string",
                                        "description", "Regex pattern to search for"
                                ),
                                "path", Map.of(
                                        "type", "string",
                                        "description", "File or directory (default: workspace root)"
                                ),
                                "glob", Map.of(
                                        "type", "string",
                                        "description", "Optional glob to filter files (e.g. **/*.java)"
                                ),
                                "case_insensitive", Map.of(
                                        "type", "boolean",
                                        "description", "Case insensitive search (default false)"
                                ),
                                "head_limit", Map.of(
                                        "type", "integer",
                                        "description", "Max matching lines (default 250; 0 = unlimited)"
                                )
                        ),
                        "required", List.of("pattern")
                )
        );
    }

    @Override
    public ToolResult execute(String argumentsJson, ToolContext context) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
            String patternArg = args.path("pattern").asText("");
            if (patternArg.isBlank()) {
                return ToolResult.failure("Missing required argument: pattern");
            }

            String pathArg = args.path("path").asText(".");
            String glob = args.has("glob") ? args.path("glob").asText(null) : null;
            boolean caseInsensitive = args.path("case_insensitive").asBoolean(false);
            int headLimitRaw = args.has("head_limit") ? args.path("head_limit").asInt(DEFAULT_HEAD_LIMIT) : DEFAULT_HEAD_LIMIT;
            boolean unlimited = headLimitRaw == 0;
            int headLimit = unlimited ? Integer.MAX_VALUE : Math.min(Math.max(headLimitRaw, 1), 500);

            Path target = WorkspacePaths.resolve(context, pathArg);
            if (!Files.exists(target)) {
                return ToolResult.failure("Path not found: " + pathArg);
            }

            Path workspace = context.workspacePath().toAbsolutePath().normalize();

            if (RipgrepRunner.isAvailable()) {
                ToolResult rgResult = runRipgrep(workspace, target, patternArg, glob, caseInsensitive, headLimit, unlimited);
                if (rgResult != null) {
                    return rgResult;
                }
            }

            return runJavaFallback(workspace, target, patternArg, glob, caseInsensitive, headLimit, context);
        } catch (Exception e) {
            return ToolResult.failure(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private ToolResult runRipgrep(
            Path workspace,
            Path target,
            String patternArg,
            String glob,
            boolean caseInsensitive,
            int headLimit,
            boolean unlimited) {
        List<String> rgArgs = new ArrayList<>();
        rgArgs.add("--hidden");
        rgArgs.add("--max-columns");
        rgArgs.add("500");
        for (String dir : VCS_EXCLUDES) {
            rgArgs.add("--glob");
            rgArgs.add("!" + dir);
        }
        rgArgs.add("--glob");
        rgArgs.add("!node_modules");
        rgArgs.add("--glob");
        rgArgs.add("!target");
        if (caseInsensitive) {
            rgArgs.add("-i");
        }
        rgArgs.add("-n");
        if (patternArg.startsWith("-")) {
            rgArgs.add("-e");
            rgArgs.add(patternArg);
        } else {
            rgArgs.add(patternArg);
        }
        if (glob != null && !glob.isBlank()) {
            for (String part : glob.split("[,\\s]+")) {
                if (!part.isBlank()) {
                    rgArgs.add("--glob");
                    rgArgs.add(part.trim());
                }
            }
        }
        rgArgs.add(target.toString());

        RipgrepRunner.RipgrepResult result = RipgrepRunner.run(rgArgs, workspace.toFile().exists() ? workspace : target.getParent());
        if (!result.ok()) {
            if ("ripgrep unavailable".equals(result.error())) {
                return null;
            }
            return ToolResult.failure(result.error());
        }

        List<String> lines = new ArrayList<>();
        for (String line : result.lines()) {
            if (lines.size() >= headLimit) {
                break;
            }
            String relative = relativizeLine(workspace, line);
            lines.add(relative);
        }

        if (lines.isEmpty()) {
            return ToolResult.success("No matches for pattern: " + patternArg);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(lines.size()).append(" match(es):\n");
        for (String line : lines) {
            sb.append(line).append('\n');
        }
        if (!unlimited && lines.size() >= headLimit) {
            sb.append("\n(head_limit reached)");
        }
        return ToolResult.success(sb.toString());
    }

    private static String relativizeLine(Path workspace, String line) {
        int colon = line.indexOf(':');
        if (colon <= 0) {
            return line;
        }
        Path file = Path.of(line.substring(0, colon));
        try {
            Path normalized = file.toAbsolutePath().normalize();
            String rest = line.substring(colon);
            return workspace.relativize(normalized).toString().replace('\\', '/') + rest;
        } catch (Exception e) {
            return line;
        }
    }

    private ToolResult runJavaFallback(
            Path workspace,
            Path target,
            String patternArg,
            String glob,
            boolean caseInsensitive,
            int headLimit,
            ToolContext context) throws Exception {
        int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0;
        Pattern pattern = Pattern.compile(patternArg, flags);

        List<String> lines = new ArrayList<>();
        final int[] filesScanned = {0};

        if (Files.isRegularFile(target)) {
            grepFile(workspace, target, pattern, lines, headLimit, context);
        } else if (Files.isDirectory(target)) {
            WorkspaceScan.walkFiles(target, file -> {
                if (lines.size() >= headLimit) {
                    return FileVisitResult.TERMINATE;
                }
                if (filesScanned[0] >= MAX_FILES_SCANNED) {
                    return FileVisitResult.TERMINATE;
                }
                if (!WorkspaceScan.matchesGlob(workspace, file, glob)) {
                    return FileVisitResult.CONTINUE;
                }
                filesScanned[0]++;
                grepFile(workspace, file, pattern, lines, headLimit, context);
                return lines.size() >= headLimit ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
            });
        } else {
            return ToolResult.failure("Not a file or directory");
        }

        if (lines.isEmpty()) {
            return ToolResult.success("No matches for pattern: " + patternArg);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(lines.size()).append(" match(es) [Java fallback — install ripgrep for speed]:\n");
        for (String line : lines) {
            sb.append(line).append('\n');
        }
        if (lines.size() >= headLimit && headLimit != Integer.MAX_VALUE) {
            sb.append("\n(head_limit reached)");
        }
        if (filesScanned[0] >= MAX_FILES_SCANNED) {
            sb.append("\n(file scan limit reached — narrow path or use glob)");
        }
        return ToolResult.success(sb.toString());
    }

    private void grepFile(
            Path workspace,
            Path file,
            Pattern pattern,
            List<String> out,
            int headLimit,
            ToolContext context) {
        if (out.size() >= headLimit) {
            return;
        }
        try {
            if (!Files.isRegularFile(file)) {
                return;
            }
            String content;
            try {
                content = WorkspaceTextFiles.readString(file, context);
            } catch (Exception e) {
                return;
            }
            if (content.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) {
                return;
            }

            String relative = workspace.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
            String[] fileLines = content.split("\n", -1);
            for (int i = 0; i < fileLines.length && out.size() < headLimit; i++) {
                Matcher matcher = pattern.matcher(fileLines[i]);
                if (matcher.find()) {
                    out.add(relative + ":" + (i + 1) + ":" + fileLines[i]);
                }
            }
        } catch (Exception ignored) {
            // skip unreadable files
        }
    }
}
