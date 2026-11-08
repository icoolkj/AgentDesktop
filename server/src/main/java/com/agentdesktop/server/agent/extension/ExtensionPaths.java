package com.agentdesktop.server.agent.extension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Resolves extension directories with Claude Code compatibility.
 * Search order: workspace first, then user home.
 */
public final class ExtensionPaths {

    private static final Path USER_ROOT = Path.of(System.getProperty("user.home"), ".agent-desktop");

    private ExtensionPaths() {
    }

    public static Path userRoot() {
        return USER_ROOT;
    }

    public static List<Path> commandDirs(String workspaceRoot) {
        return mergeDirs(workspaceRoot, "commands");
    }

    public static Path userSkillsDir() {
        return USER_ROOT.resolve("skills");
    }

    public static Path userAgentDesktopSkillsDir() {
        return USER_ROOT.resolve("skills-agent-desktop");
    }

    public static Path workspaceSkillsDir(String workspaceRoot) {
        return Path.of(workspaceRoot).resolve(".agent-desktop").resolve("skills");
    }

    public static List<Path> skillDirs(String workspaceRoot) {
        List<Path> dirs = new ArrayList<>();
        dirs.addAll(ProjectDirsUpToHome.agentDesktopSubdirs("skills", workspaceRoot));
        dirs.addAll(ProjectDirsUpToHome.claudeSubdirs("skills", workspaceRoot));
        dirs.add(userSkillsDir());
        dirs.add(userAgentDesktopSkillsDir());
        return dirs.stream().filter(Files::isDirectory).distinct().toList();
    }

    public static List<Path> hookConfigFiles(String workspaceRoot) {
        List<Path> files = new ArrayList<>();
        for (Path projectRoot : projectConfigRoots(workspaceRoot)) {
            files.add(projectRoot.resolve(".agent-desktop").resolve("hooks.json"));
            files.add(projectRoot.resolve(".claude").resolve("settings.json"));
        }
        files.add(USER_ROOT.resolve("hooks.json"));
        return files.stream().filter(Files::isRegularFile).distinct().toList();
    }

    /** Project roots from {@code cwd} up to git boundary (inclusive), most specific first. */
    static List<Path> projectConfigRoots(String workspaceRoot) {
        if (workspaceRoot == null || workspaceRoot.isBlank()) {
            return List.of();
        }
        Path start = Path.of(workspaceRoot.trim()).toAbsolutePath().normalize();
        Path gitRoot = ProjectDirsUpToHome.findGitRoot(start);
        List<Path> roots = new ArrayList<>();
        Path current = start;
        while (true) {
            if (normalizeForCompare(current).equals(normalizeForCompare(Path.of(System.getProperty("user.home")).toAbsolutePath().normalize()))) {
                break;
            }
            roots.add(current);
            if (gitRoot != null && normalizeForCompare(current).equals(normalizeForCompare(gitRoot))) {
                break;
            }
            Path parent = current.getParent();
            if (parent == null || parent.equals(current)) {
                break;
            }
            current = parent;
        }
        return List.copyOf(roots);
    }

    private static String normalizeForCompare(Path path) {
        return path.toAbsolutePath().normalize().toString().replace('\\', '/')
                .toLowerCase(Locale.ROOT);
    }

    public static Path featureSettingsFile() {
        return USER_ROOT.resolve("features.json");
    }

    public static Path userPluginsDir() {
        return USER_ROOT.resolve("plugins");
    }

    public static Path workspacePluginsDir(String workspaceRoot) {
        return Path.of(workspaceRoot).resolve(".agent-desktop").resolve("plugins");
    }

    public static List<Path> pluginRoots(String workspaceRoot) {
        List<Path> roots = new ArrayList<>();
        roots.addAll(ProjectDirsUpToHome.agentDesktopSubdirs("plugins", workspaceRoot));
        roots.addAll(ProjectDirsUpToHome.claudeSubdirs("plugins", workspaceRoot));
        roots.add(USER_ROOT.resolve("plugins"));
        return roots.stream().filter(Files::isDirectory).distinct().toList();
    }

    private static List<Path> mergeDirs(String workspaceRoot, String subdir) {
        List<Path> dirs = new ArrayList<>();
        dirs.addAll(ProjectDirsUpToHome.agentDesktopSubdirs(subdir, workspaceRoot));
        dirs.addAll(ProjectDirsUpToHome.claudeSubdirs(subdir, workspaceRoot));
        dirs.add(USER_ROOT.resolve(subdir));
        return dirs.stream().filter(Files::isDirectory).distinct().toList();
    }

    /** Recursively find files named {@code SKILL.md}. */
    public static Stream<Path> walkSkillFiles(Path dir) {
        try {
            return Files.walk(dir)
                    .filter(path -> Files.isRegularFile(path)
                            && "SKILL.md".equalsIgnoreCase(path.getFileName().toString()));
        } catch (Exception e) {
            return Stream.empty();
        }
    }
}
