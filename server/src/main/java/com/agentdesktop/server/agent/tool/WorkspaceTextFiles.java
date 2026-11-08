package com.agentdesktop.server.agent.tool;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** Read/write workspace text with Monaco buffer overlay support. */
public final class WorkspaceTextFiles {

    public static final int MAX_BYTES = 512 * 1024;

    private static final Set<String> IGNORED_DIR_NAMES = Set.of(
            ".git",
            "node_modules",
            "target",
            "dist",
            "build",
            "out",
            ".idea",
            ".agent-desktop",
            "__pycache__");

    private WorkspaceTextFiles() {
    }

    public static boolean isIgnoredDir(Path dir) {
        Path name = dir.getFileName();
        return name != null && IGNORED_DIR_NAMES.contains(name.toString());
    }

    public static String readString(Path file, ToolContext context) throws Exception {
        String overlay = context.openDocumentContent(file);
        if (overlay != null) {
            return overlay;
        }
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("File not found: " + file);
        }
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Not a regular file: " + file);
        }
        long size = Files.size(file);
        if (size > MAX_BYTES) {
            throw new IllegalArgumentException("File too large (" + size + " bytes)");
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    public static void writeString(Path file, String content) throws Exception {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Content too large. Max " + MAX_BYTES + " bytes.");
        }
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
