package com.agentdesktop.server.agent.tool;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/** Workspace file walk with ignore rules and glob matching. */
public final class WorkspaceScan {

    private static final int DEFAULT_LIMIT = 200;

    private WorkspaceScan() {
    }

    public static List<Path> glob(Path workspaceRoot, Path searchRoot, String globPattern, int limit) throws IOException {
        int capped = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 500);
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + normalizeGlob(globPattern));
        List<Path> matches = new ArrayList<>();

        Files.walkFileTree(searchRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(searchRoot) && WorkspaceTextFiles.isIgnoredDir(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (matches.size() >= capped) {
                    return FileVisitResult.TERMINATE;
                }
                Path relative = workspaceRoot.relativize(file.toAbsolutePath().normalize());
                String posix = relative.toString().replace('\\', '/');
                if (matcher.matches(relative) || matcher.matches(Path.of(posix))) {
                    matches.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return matches;
    }

    public static void walkFiles(Path searchRoot, FileVisitor visitor) throws IOException {
        Files.walkFileTree(searchRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(searchRoot) && WorkspaceTextFiles.isIgnoredDir(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                visitor.onDirectory(dir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                return visitor.onFile(file);
            }
        });
    }

    public static boolean matchesGlob(Path workspaceRoot, Path file, String globPattern) {
        if (globPattern == null || globPattern.isBlank()) {
            return true;
        }
        Path relative = workspaceRoot.relativize(file.toAbsolutePath().normalize());
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + normalizeGlob(globPattern));
        String posix = relative.toString().replace('\\', '/');
        return matcher.matches(relative) || matcher.matches(Path.of(posix));
    }

    private static String normalizeGlob(String pattern) {
        String trimmed = pattern.trim().replace('\\', '/');
        if (!trimmed.startsWith("**/") && !trimmed.startsWith("*") && !trimmed.contains("/")) {
            return "**/" + trimmed;
        }
        return trimmed;
    }

    @FunctionalInterface
    public interface FileVisitor {
        FileVisitResult onFile(Path file) throws IOException;

        default void onDirectory(Path dir) {
        }
    }
}
