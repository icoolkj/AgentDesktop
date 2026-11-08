package com.agentdesktop.server.agent.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Spawns ripgrep with Claude Code defaults ({@code ripgrep.ts}):
 * 20s timeout on native Windows/macOS/Linux, SIGKILL escalation on Unix.
 */
public final class RipgrepRunner {

    private static final Logger log = LoggerFactory.getLogger(RipgrepRunner.class);

    /** Claude Code {@code defaultTimeout} for non-WSL platforms. */
    public static final long DEFAULT_TIMEOUT_MS = 20_000;
    private static final int MAX_OUTPUT_CHARS = 500_000;

    private RipgrepRunner() {
    }

    public record RipgrepResult(boolean ok, List<String> lines, String error) {
        public static RipgrepResult success(List<String> lines) {
            return new RipgrepResult(true, lines, null);
        }

        public static RipgrepResult failure(String error) {
            return new RipgrepResult(false, List.of(), error);
        }

        public static RipgrepResult unavailable() {
            return failure("ripgrep unavailable");
        }
    }

    public static boolean isAvailable() {
        return resolveRgCommand() != null;
    }

    public static RipgrepResult run(List<String> args, Path workingDir) {
        String rg = resolveRgCommand();
        if (rg == null) {
            return RipgrepResult.unavailable();
        }

        List<String> command = new ArrayList<>();
        command.add(rg);
        command.addAll(args);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir.toFile());
        builder.redirectErrorStream(true);

        try {
            Process process = builder.start();
            List<String> lines = new ArrayList<>();
            StringBuilder overflow = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (lines.size() < 10_000) {
                        lines.add(line);
                    } else {
                        overflow.append(line).append('\n');
                    }
                }
            }

            boolean finished = process.waitFor(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!finished) {
                destroyProcess(process);
                return RipgrepResult.failure(
                        "ripgrep timed out after " + (DEFAULT_TIMEOUT_MS / 1000) + "s — narrow path or use glob");
            }

            int exitCode = process.exitValue();
            // rg: 0 = matches, 1 = no matches, 2+ = error
            if (exitCode >= 2) {
                String detail = lines.isEmpty() ? "exit code " + exitCode : String.join("\n", lines);
                return RipgrepResult.failure("ripgrep failed: " + detail);
            }

            if (overflow.length() > 0) {
                log.debug("ripgrep output truncated at 10000 lines");
            }
            return RipgrepResult.success(lines);
        } catch (Exception e) {
            return RipgrepResult.failure(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private static void destroyProcess(Process process) {
        try {
            process.destroy();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    /** Prefer {@code rg} on PATH; on Windows also try common install locations. */
    static String resolveRgCommand() {
        String fromPath = findOnPath("rg");
        if (fromPath != null) {
            return fromPath;
        }
        if (isWindows()) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                Path candidate = Path.of(localAppData, "Programs", "ripgrep", "rg.exe");
                if (Files.isRegularFile(candidate)) {
                    return candidate.toString();
                }
            }
        }
        return null;
    }

    private static String findOnPath(String name) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return null;
        }
        String executable = isWindows() ? name + ".exe" : name;
        for (String dir : pathEnv.split(isWindows() ? ";" : ":")) {
            if (dir.isBlank()) {
                continue;
            }
            String trimmed = dir.trim();
            // Skip non-directory PATH entries (e.g. tcp://… from Docker tooling)
            if (trimmed.contains("://")) {
                continue;
            }
            try {
                Path candidate = Path.of(trimmed, executable);
                if (Files.isRegularFile(candidate)) {
                    return candidate.toString();
                }
            } catch (Exception ignored) {
                // malformed PATH segment
            }
        }
        return null;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
