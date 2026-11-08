package com.agentdesktop.server.agent.extension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Parses skill SKILL.md frontmatter fields (CC-compatible subset). */
public final class SkillFrontmatterParser {

    private static final Pattern LIST_ITEM = Pattern.compile("^-\\s+(.+)$");

    private SkillFrontmatterParser() {
    }

    public static Map<String, String> parseFrontmatterMap(String frontmatter) {
        Map<String, String> map = new LinkedHashMap<>();
        if (frontmatter == null || frontmatter.isBlank()) {
            return map;
        }
        String currentKey = null;
        StringBuilder currentValue = new StringBuilder();
        for (String rawLine : frontmatter.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon > 0 && !line.startsWith("-")) {
                flush(map, currentKey, currentValue);
                currentKey = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                currentValue = new StringBuilder(line.substring(colon + 1).trim());
            } else if (currentKey != null) {
                if (!currentValue.isEmpty()) {
                    currentValue.append('\n');
                }
                currentValue.append(line);
            }
        }
        flush(map, currentKey, currentValue);
        return map;
    }

    public static List<String> parsePathPatterns(Map<String, String> frontmatter) {
        String raw = frontmatter.get("paths");
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> patterns = new ArrayList<>();
        for (String line : raw.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            var matcher = LIST_ITEM.matcher(trimmed);
            if (matcher.matches()) {
                trimmed = matcher.group(1).trim();
            }
            for (String part : trimmed.split(",")) {
                String pattern = stripQuotes(part.trim());
                if (!pattern.isBlank() && !"**".equals(pattern)) {
                    patterns.add(pattern);
                }
            }
        }
        return List.copyOf(patterns);
    }

    public static List<String> parseAllowedTools(Map<String, String> frontmatter) {
        String raw = frontmatter.get("allowed-tools");
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> tools = new ArrayList<>();
        for (String line : raw.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            var matcher = LIST_ITEM.matcher(trimmed);
            if (matcher.matches()) {
                trimmed = matcher.group(1).trim();
            }
            for (String part : trimmed.split(",")) {
                String tool = stripQuotes(part.trim());
                if (!tool.isBlank()) {
                    tools.add(tool);
                }
            }
        }
        return List.copyOf(tools);
    }

    public static boolean isForkContext(Map<String, String> frontmatter) {
        return "fork".equalsIgnoreCase(frontmatter.get("context"));
    }

    public static String parseAgentType(Map<String, String> frontmatter) {
        String agent = frontmatter.get("agent");
        return agent == null || agent.isBlank() ? null : agent.trim();
    }

    private static void flush(Map<String, String> map, String key, StringBuilder value) {
        if (key == null) {
            return;
        }
        map.put(key, value.toString().trim());
    }

    private static String stripQuotes(String value) {
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            return value.substring(1, value.length() - 1);
        }
        if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
