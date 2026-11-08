package com.agentdesktop.server.agent.extension;

import org.springframework.stereotype.Component;

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

/**
 * Resolves slash commands from built-ins, skills (skills/.../SKILL.md), and commands/*.md.
 */
@Component
public class SlashCommandRegistry {

    private static final Pattern FRONTMATTER = Pattern.compile(
            "^---\\s*\\n(.*?)\\n---\\s*\\n(.*)",
            Pattern.DOTALL);

    private final SkillContentLoader skillContentLoader;
    private final SessionSkillState sessionSkillState;

    public SlashCommandRegistry(SkillContentLoader skillContentLoader, SessionSkillState sessionSkillState) {
        this.skillContentLoader = skillContentLoader;
        this.sessionSkillState = sessionSkillState;
    }

    public SlashCommandResult resolve(String message, ExtensionBundle bundle) {
        return resolve(message, bundle, null);
    }

    public SlashCommandResult resolve(String message, ExtensionBundle bundle, String sessionId) {
        if (message == null || message.isBlank()) {
            return SlashCommandResult.None.INSTANCE;
        }
        String trimmed = message.trim();
        if (!trimmed.startsWith("/")) {
            return SlashCommandResult.None.INSTANCE;
        }

        int space = trimmed.indexOf(' ');
        String commandToken = space < 0 ? trimmed.substring(1) : trimmed.substring(1, space);
        String args = space < 0 ? "" : trimmed.substring(space + 1).trim();
        String commandName = commandToken.toLowerCase(Locale.ROOT);

        if ("context".equals(commandName)) {
            return new SlashCommandResult.Context();
        }
        if ("compact".equals(commandName)) {
            return new SlashCommandResult.Compact(args);
        }

        SkillEntry skill = findSkill(bundle, commandName);
        if (skill != null && skill.userInvocable()) {
            try {
                String body = skillContentLoader.loadForInvocation(skill, args);
                String wrapped = SkillMessages.wrapSystemReminder(body);
                if (sessionId != null && sessionSkillState != null) {
                    String skillPath = resolveSkillPath(skill);
                    sessionSkillState.recordInvoked(sessionId, skill.name(), skillPath, body);
                    sessionSkillState.setActiveAllowedTools(sessionId, skill.allowedTools());
                }
                return new SlashCommandResult.PromptExpansion(wrapped, commandName);
            } catch (Exception e) {
                return new SlashCommandResult.PromptExpansion(
                        "Failed to load skill /" + commandName + ": " + e.getMessage(),
                        commandName);
            }
        }

        SlashCommandDefinition custom = findCustom(bundle, commandName);
        if (custom != null) {
            String expanded = expandTemplate(custom.promptTemplate(), args);
            return new SlashCommandResult.PromptExpansion(expanded, commandName);
        }
        return SlashCommandResult.None.INSTANCE;
    }

    public List<SlashCommandDefinition> loadFromDirs(List<Path> dirs) {
        Map<String, SlashCommandDefinition> byName = new LinkedHashMap<>();
        for (Path dir : dirs) {
            try (var stream = Files.list(dir)) {
                stream.filter(path -> Files.isRegularFile(path)
                                && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                        .forEach(path -> {
                            try {
                                SlashCommandDefinition def = parseCommandFile(path);
                                if (def != null && !"compact".equals(def.name()) && !"context".equals(def.name())) {
                                    byName.putIfAbsent(def.name(), def);
                                }
                            } catch (Exception ignored) {
                                // skip malformed command files
                            }
                        });
            } catch (Exception ignored) {
                // skip unreadable dirs
            }
        }
        return List.copyOf(byName.values());
    }

    private static SlashCommandDefinition findCustom(ExtensionBundle bundle, String name) {
        if (bundle == null || bundle.slashCommands() == null) {
            return null;
        }
        return bundle.slashCommands().stream()
                .filter(cmd -> name.equals(cmd.name()))
                .findFirst()
                .orElse(null);
    }

    private static SkillEntry findSkill(ExtensionBundle bundle, String name) {
        return SkillCatalog.findByName(bundle == null ? List.of() : bundle.skills(), name);
    }

    static SlashCommandDefinition parseCommandFile(Path path) throws Exception {
        String raw = Files.readString(path, StandardCharsets.UTF_8);
        String description = "";
        String body = raw.trim();
        Matcher matcher = FRONTMATTER.matcher(raw);
        if (matcher.matches()) {
            description = parseDescription(matcher.group(1));
            body = matcher.group(2).trim();
        }
        String fileName = path.getFileName().toString();
        String name = fileName.substring(0, fileName.length() - 3).toLowerCase(Locale.ROOT);
        if (name.isBlank() || body.isBlank()) {
            return null;
        }
        return new SlashCommandDefinition(name, description, body, path.toString());
    }

    private static String parseDescription(String frontmatter) {
        for (String line : frontmatter.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("description:")) {
                return trimmed.substring("description:".length()).trim();
            }
        }
        return "";
    }

    static String expandTemplate(String template, String args) {
        String result = template;
        result = result.replace("$ARGUMENTS", args);
        result = result.replace("{{args}}", args);
        result = result.replace("{{arguments}}", args);
        if (!args.isBlank() && !result.contains(args)) {
            result = result + "\n\nUser arguments: " + args;
        }
        return result.trim();
    }

    private static String resolveSkillPath(SkillEntry skill) {
        if (skill.skillRoot() != null) {
            return skill.skillRoot().toString();
        }
        if (skill.skillFile() != null) {
            return skill.skillFile().toString();
        }
        if (skill.isBundledInBinary()) {
            return "bundled:" + skill.bundledCatalogId();
        }
        if (skill.isMcpSkill()) {
            return "mcp:" + skill.name();
        }
        return skill.name();
    }
}
