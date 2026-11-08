package com.agentdesktop.server.agent.permission;

import java.util.regex.Pattern;

/**
 * Parsed permission rule — Claude Code Tool(pattern) syntax subset.
 * Examples: write_file, write_file with path glob, glob.
 */
public record PermissionRule(String toolName, String pathGlob, PermissionBehavior behavior) {

    private static final Pattern RULE_PATTERN = Pattern.compile(
            "^([A-Za-z_][A-Za-z0-9_]*)(?:\\((.+)\\))?$");

    public static PermissionRule parse(String raw, PermissionBehavior behavior) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("empty permission rule");
        }
        String trimmed = raw.trim();
        var matcher = RULE_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("invalid permission rule: " + raw);
        }
        String tool = matcher.group(1);
        String glob = matcher.group(2);
        return new PermissionRule(tool, glob == null ? null : glob.trim(), behavior);
    }

    public boolean matches(String toolName, String relativePath) {
        if (toolName == null || !this.toolName.equalsIgnoreCase(toolName)) {
            return false;
        }
        if (pathGlob == null || pathGlob.isBlank()) {
            return true;
        }
        if (relativePath == null || relativePath.isBlank()) {
            return false;
        }
        return globMatches(pathGlob, normalizePath(relativePath));
    }

    private static String normalizePath(String path) {
        return path.replace('\\', '/').replaceAll("^\\./+", "");
    }

    /** Simple glob: {@code **}, {@code *}, path segments — sufficient for project rules. */
    static boolean globMatches(String glob, String path) {
        String g = normalizePath(glob);
        String p = normalizePath(path);
        if ("**".equals(g) || "*".equals(g)) {
            return true;
        }
        if (g.endsWith("/**")) {
            String prefix = g.substring(0, g.length() - 3);
            return p.equals(prefix) || p.startsWith(prefix + "/");
        }
        if (g.contains("*")) {
            String regex = g.replace(".", "\\.")
                    .replace("**", "§§")
                    .replace("*", "[^/]*")
                    .replace("§§", ".*");
            return p.matches(regex);
        }
        return p.equals(g) || p.startsWith(g + "/");
    }
}
