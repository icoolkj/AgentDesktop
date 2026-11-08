package com.agentdesktop.server.agent.extension;

import com.agentdesktop.server.agent.tool.Tool;

import java.util.List;
import java.util.Locale;

/** Filters tools by skill {@code allowed-tools} frontmatter patterns (CC subset). */
public final class SkillAllowedToolFilter {

    private SkillAllowedToolFilter() {
    }

    public static boolean isAllowed(Tool tool, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return true;
        }
        if (tool == null) {
            return false;
        }
        String name = tool.name();
        for (String pattern : patterns) {
            if (matches(name, pattern)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAllowedMcpTool(String toolName, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return true;
        }
        for (String pattern : patterns) {
            if (matches(toolName, pattern)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(String toolName, String pattern) {
        if (pattern == null || pattern.isBlank() || toolName == null) {
            return false;
        }
        String normalizedPattern = pattern.trim();
        String normalizedName = toolName.trim();
        if (normalizedPattern.equals(normalizedName)) {
            return true;
        }
        if (normalizedPattern.endsWith("(*)")) {
            String prefix = normalizedPattern.substring(0, normalizedPattern.length() - 3);
            return normalizedName.startsWith(prefix);
        }
        if (normalizedPattern.contains("*")) {
            String regex = normalizedPattern
                    .replace(".", "\\.")
                    .replace("(", "\\(")
                    .replace(")", "\\)")
                    .replace("*", ".*");
            return normalizedName.matches(regex);
        }
        return normalizedName.toLowerCase(Locale.ROOT).equals(normalizedPattern.toLowerCase(Locale.ROOT));
    }
}
