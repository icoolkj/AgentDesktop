package com.agentdesktop.server.transcript;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Claude Code session paths — mirrors desktop {@code claudeSessionPortable.ts}.
 * {@code ~/.agent-desktop/projects/<sanitize(cwd)>/<sessionId>.jsonl}
 */
public final class AgentDesktopPaths {

    private static final int MAX_SANITIZED_LENGTH = 200;

    private AgentDesktopPaths() {
    }

    public static Path configHome() {
        return Paths.get(System.getProperty("user.home"), ".agent-desktop");
    }

    public static Path projectsDir() {
        return configHome().resolve("projects");
    }

    public static String sanitizePath(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            return "default";
        }
        String sanitized = projectPath.replaceAll("[^a-zA-Z0-9]", "-");
        if (sanitized.length() <= MAX_SANITIZED_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, MAX_SANITIZED_LENGTH) + "-" + simpleHash(projectPath);
    }

    public static Path transcriptFile(String sessionId, String workspaceRoot) {
        String cwd = workspaceRoot == null || workspaceRoot.isBlank() ? "default" : workspaceRoot;
        return projectsDir().resolve(sanitizePath(cwd)).resolve(sessionId + ".jsonl");
    }

    /** djb2 hash — same as claudeSessionPortable.ts simpleHash (UTF-16 code units). */
    static String simpleHash(String str) {
        int hash = 5381;
        for (int i = 0; i < str.length(); i++) {
            hash = ((hash << 5) + hash) ^ str.charAt(i);
        }
        return Integer.toString(Math.abs(hash), 36);
    }
}
