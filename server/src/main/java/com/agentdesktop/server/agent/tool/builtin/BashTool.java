package com.agentdesktop.server.agent.tool.builtin;

import com.agentdesktop.server.agent.extension.BashSandboxService;
import com.agentdesktop.server.agent.llm.LlmToolDefinition;
import com.agentdesktop.server.agent.tool.Tool;
import com.agentdesktop.server.agent.tool.ToolContext;
import com.agentdesktop.server.agent.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Run shell commands in the workspace — Claude Code Bash / Cursor run_terminal_cmd parity.
 */
@Component
public class BashTool implements Tool {

    private static final int MAX_OUTPUT_CHARS = 64_000;
    private static final long DEFAULT_TIMEOUT_SECONDS = 120;

    private final ObjectMapper objectMapper;
    private final BashSandboxService bashSandboxService;

    public BashTool(ObjectMapper objectMapper, BashSandboxService bashSandboxService) {
        this.objectMapper = objectMapper;
        this.bashSandboxService = bashSandboxService;
    }

    @Override
    public String name() {
        return "run_terminal_cmd";
    }

    @Override
    public String description() {
        return """
                Execute a shell command in the workspace directory.
                Returns combined stdout/stderr. Use for builds, tests, git, npm, etc.
                """;
    }

    @Override
    public LlmToolDefinition definition() {
        return new LlmToolDefinition(
                name(),
                description(),
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "command", Map.of(
                                        "type", "string",
                                        "description", "Shell command to run"
                                ),
                                "description", Map.of(
                                        "type", "string",
                                        "description", "Short human-readable description of what the command does"
                                ),
                                "timeout_seconds", Map.of(
                                        "type", "integer",
                                        "description", "Max seconds to wait (default 120)"
                                )
                        ),
                        "required", List.of("command")
                )
        );
    }

    @Override
    public ToolResult execute(String argumentsJson, ToolContext context) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
            String command = args.path("command").asText("");
            if (command.isBlank()) {
                return ToolResult.failure("Missing required argument: command");
            }

            BashSandboxService.BashValidation sandbox = bashSandboxService.validate(
                    command,
                    context.featuresOrDefault(),
                    context.workspaceRoot());
            if (!sandbox.allowed()) {
                return ToolResult.failure(sandbox.message());
            }

            long timeoutSec = args.has("timeout_seconds")
                    ? Math.max(1, Math.min(600, args.path("timeout_seconds").asLong()))
                    : DEFAULT_TIMEOUT_SECONDS;

            if (!context.hasWorkspace()) {
                return ToolResult.failure("No workspace root — cannot run terminal commands");
            }

            Path cwd = context.workspacePath();
            if (cwd == null || !Files.isDirectory(cwd)) {
                return ToolResult.failure("Invalid workspace directory");
            }

            ProcessBuilder builder = new ProcessBuilder();
            if (isWindows()) {
                builder.command("cmd.exe", "/c", command);
            } else {
                builder.command("sh", "-lc", command);
            }
            builder.directory(cwd.toFile());
            builder.redirectErrorStream(true);

            Process process = builder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buf = new char[4096];
                int read;
                while ((read = reader.read(buf)) >= 0) {
                    output.append(buf, 0, read);
                    if (output.length() > MAX_OUTPUT_CHARS) {
                        output.append("\n… (output truncated)");
                        break;
                    }
                }
            }

            boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ToolResult.failure("Command timed out after " + timeoutSec + " seconds");
            }

            int exitCode = process.exitValue();
            String text = output.toString().trim();
            if (text.isEmpty()) {
                text = "(no output)";
            }
            String prefix = sandbox.warning() ? "[sandbox warning] " : "";
            String result = prefix + "Exit code: " + exitCode + "\n" + text;
            if (sandbox.warning() && sandbox.message() != null) {
                result = sandbox.message() + "\n" + result;
            }
            if (exitCode != 0) {
                return ToolResult.failure(result);
            }
            return ToolResult.success(result);
        } catch (Exception e) {
            return ToolResult.failure(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    @Override
    public boolean concurrencySafe() {
        return false;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
