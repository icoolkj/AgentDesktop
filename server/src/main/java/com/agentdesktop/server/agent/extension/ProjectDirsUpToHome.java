package com.agentdesktop.server.agent.extension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Walk from {@code cwd} up toward home, collecting existing config subdirectories.
 * Stops at the nearest git root (inclusive) so parent-repo config does not leak in.
 * Mirrors Claude Code {@code getProjectDirsUpToHome}.
 */
public final class ProjectDirsUpToHome {

    private static final Path HOME = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();

    private ProjectDirsUpToHome() {
    }

    /** Existing {@code .claude/{subdir}} directories, most specific first. */
    public static List<Path> claudeSubdirs(String subdir, String cwd) {
        return collectSubdirs(cwd, ".claude", subdir);
    }

    /** Existing {@code .agent-desktop/{subdir}} directories, most specific first. */
    public static List<Path> agentDesktopSubdirs(String subdir, String cwd) {
        return collectSubdirs(cwd, ".agent-desktop", subdir);
    }

    private static List<Path> collectSubdirs(String cwd, String configDirName, String subdir) {
        if (cwd == null || cwd.isBlank()) {
            return List.of();
        }
        Path start = Path.of(cwd.trim()).toAbsolutePath().normalize();
        Path gitRoot = findGitRoot(start);
        List<Path> dirs = new ArrayList<>();
        Path current = start;

        while (true) {
            if (normalizePath(current).equals(normalizePath(HOME))) {
                break;
            }

            Path candidate = current.resolve(configDirName).resolve(subdir);
            if (Files.isDirectory(candidate)) {
                dirs.add(candidate);
            }

            if (gitRoot != null && normalizePath(current).equals(normalizePath(gitRoot))) {
                break;
            }

            Path parent = current.getParent();
            if (parent == null || parent.equals(current)) {
                break;
            }
            current = parent;
        }
        return List.copyOf(dirs);
    }

    static Path findGitRoot(Path start) {
        Path current = start.toAbsolutePath().normalize();
        while (true) {
            if (isGitRoot(current)) {
                return current;
            }
            Path parent = current.getParent();
            if (parent == null || parent.equals(current)) {
                return null;
            }
            current = parent;
        }
    }

    private static boolean isGitRoot(Path dir) {
        if (Files.isDirectory(dir.resolve(".git"))) {
            return true;
        }
        Path gitFile = dir.resolve(".git");
        if (!Files.isRegularFile(gitFile)) {
            return false;
        }
        try {
            String content = Files.readString(gitFile).trim();
            return content.startsWith("gitdir:");
        } catch (IOException e) {
            return false;
        }
    }

    private static String normalizePath(Path path) {
        return path.toAbsolutePath().normalize().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
    }
}
