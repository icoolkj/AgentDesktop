package com.agentdesktop.server.agent.extension;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;

/** Matches workspace-relative paths against skill {@code paths:} frontmatter globs. */
public final class SkillPathMatcher {

    private SkillPathMatcher() {
    }

    public static boolean matchesAny(List<String> patterns, Path workspaceRoot, Path absolutePath) {
        if (patterns == null || patterns.isEmpty() || workspaceRoot == null || absolutePath == null) {
            return false;
        }
        Path normalizedRoot = workspaceRoot.toAbsolutePath().normalize();
        Path normalizedFile = absolutePath.toAbsolutePath().normalize();
        if (!normalizedFile.startsWith(normalizedRoot)) {
            return false;
        }
        Path relative = normalizedRoot.relativize(normalizedFile);
        String relativePosix = relative.toString().replace('\\', '/');
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            String glob = pattern.replace('\\', '/');
            if (!glob.startsWith("glob:")) {
                glob = "glob:" + glob;
            }
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher(glob);
            if (matcher.matches(Path.of(relativePosix)) || matcher.matches(Path.of("/" + relativePosix))) {
                return true;
            }
        }
        return false;
    }
}
