package com.agentdesktop.server.agent.extension;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SkillCatalog {

    private static final Pattern FRONTMATTER = Pattern.compile(
            "^---\\s*\\n(.*?)\\n---\\s*\\n(.*)",
            Pattern.DOTALL);

    public List<SkillEntry> loadFromDirs(List<Path> skillDirs) {
        Map<String, SkillEntry> byName = new LinkedHashMap<>();
        for (Path dir : skillDirs) {
            if (dir == null || !Files.isDirectory(dir)) {
                continue;
            }
            String source = inferSource(dir);
            ExtensionPaths.walkSkillFiles(dir).forEach(skillFile -> {
                try {
                    SkillEntry entry = parseSkillFile(skillFile, source);
                    if (entry != null) {
                        byName.putIfAbsent(entry.name(), entry);
                    }
                } catch (Exception ignored) {
                    // skip unreadable skill
                }
            });
        }
        return List.copyOf(byName.values());
    }

    public static List<SkillEntry> modelInvocableSkills(List<SkillEntry> skills) {
        if (skills == null || skills.isEmpty()) {
            return List.of();
        }
        return skills.stream().filter(SkillEntry::modelInvocable).toList();
    }

    public static List<SkillEntry> unconditionalModelInvocable(List<SkillEntry> skills) {
        if (skills == null || skills.isEmpty()) {
            return List.of();
        }
        return skills.stream().filter(skill -> skill.modelInvocable() && !skill.conditional()).toList();
    }

    public static SkillEntry findByName(List<SkillEntry> skills, String name) {
        if (skills == null || name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        for (SkillEntry skill : skills) {
            if (normalized.equals(skill.name())) {
                return skill;
            }
        }
        return null;
    }

    static SkillEntry parseSkillFile(Path skillFile, String source) throws IOException {
        String raw = Files.readString(skillFile, StandardCharsets.UTF_8);
        Path skillRoot = skillFile.getParent();
        String name = skillRoot == null
                ? stripExtension(skillFile.getFileName().toString())
                : skillRoot.getFileName().toString().toLowerCase(Locale.ROOT);
        return parseSkillFileContent(name, raw, source, skillRoot, skillFile, null);
    }

    public static SkillEntry parseSkillFileContent(
            String name,
            String raw,
            String source,
            Path skillRoot,
            Path skillFile,
            String bundledCatalogId) {
        if (raw.isBlank() || name.isBlank()) {
            return null;
        }

        String frontmatterText = "";
        String body = raw.trim();
        Matcher matcher = FRONTMATTER.matcher(raw);
        if (matcher.matches()) {
            frontmatterText = matcher.group(1);
            body = matcher.group(2);
        }

        Map<String, String> frontmatter = SkillFrontmatterParser.parseFrontmatterMap(frontmatterText);
        String description = frontmatter.getOrDefault("description", "").trim();
        if (description.isBlank()) {
            description = extractFirstParagraph(body);
        }
        if (description.isBlank()) {
            description = "Skill " + name;
        }

        boolean userInvocable = parseBooleanFrontmatter(frontmatter.get("user-invocable"), true);
        boolean disableModelInvocation = parseBooleanFrontmatter(frontmatter.get("disable-model-invocation"), false);
        List<String> pathPatterns = SkillFrontmatterParser.parsePathPatterns(frontmatter);
        List<String> allowedTools = SkillFrontmatterParser.parseAllowedTools(frontmatter);
        boolean forkContext = SkillFrontmatterParser.isForkContext(frontmatter);
        String agentType = SkillFrontmatterParser.parseAgentType(frontmatter);

        return new SkillEntry(
                name,
                description,
                frontmatter.get("when_to_use"),
                skillRoot,
                skillFile,
                source,
                userInvocable,
                disableModelInvocation,
                pathPatterns,
                allowedTools,
                forkContext,
                agentType,
                null,
                null,
                null,
                bundledCatalogId);
    }

    private static String inferSource(Path dir) {
        String normalized = dir.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.contains("/plugins/")) {
            return "plugin";
        }
        if (normalized.contains("/.claude/skills")) {
            return "project-claude";
        }
        if (normalized.contains("/.agent-desktop/skills")) {
            return "project";
        }
        if (normalized.contains("skills-agent-desktop")) {
            return "user-agent-desktop";
        }
        if (normalized.endsWith("/skills") || normalized.contains("/.agent-desktop/skills")) {
            return "user";
        }
        return "skills";
    }

    private static Map<String, String> parseFrontmatterMap(String frontmatter) {
        return SkillFrontmatterParser.parseFrontmatterMap(frontmatter);
    }

    private static boolean parseBooleanFrontmatter(String raw, boolean defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized) || "yes".equals(normalized) || "1".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "no".equals(normalized) || "0".equals(normalized)) {
            return false;
        }
        return defaultValue;
    }

    private static String extractFirstParagraph(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String[] parts = body.trim().split("\n\n");
        return parts[0].replace('\n', ' ').trim();
    }

    private static String stripExtension(String fileName) {
        if (fileName.toLowerCase(Locale.ROOT).endsWith(".md")) {
            return fileName.substring(0, fileName.length() - 3).toLowerCase(Locale.ROOT);
        }
        return fileName.toLowerCase(Locale.ROOT);
    }
}
