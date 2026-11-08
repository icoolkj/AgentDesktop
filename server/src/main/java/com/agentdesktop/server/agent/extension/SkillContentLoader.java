package com.agentdesktop.server.agent.extension;

import com.agentdesktop.server.agent.mcp.McpSkillBridge;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Loads full SKILL.md body on Skill tool or /skill invocation. */
@Component
public class SkillContentLoader {

    private static final Pattern FRONTMATTER = Pattern.compile(
            "^---\\s*\\n(.*?)\\n---\\s*\\n(.*)",
            Pattern.DOTALL);

    private final McpSkillBridge mcpSkillBridge;

    public SkillContentLoader(McpSkillBridge mcpSkillBridge) {
        this.mcpSkillBridge = mcpSkillBridge;
    }

    public String loadForInvocation(SkillEntry skill, String args) throws Exception {
        if (skill.isBundledInBinary()) {
            return loadBundled(skill, args);
        }
        if (skill.isMcpSkill()) {
            if (mcpSkillBridge == null) {
                throw new IOException("MCP skills unavailable in this context");
            }
            return mcpSkillBridge.loadSkillContent(skill, args);
        }
        if (skill.skillFile() == null) {
            throw new IOException("Skill has no content source: " + skill.name());
        }
        String raw = Files.readString(skill.skillFile(), StandardCharsets.UTF_8);
        String body = stripFrontmatter(raw).trim();
        if (body.isBlank()) {
            throw new IOException("Skill body is empty: " + skill.skillFile());
        }

        String skillDir = skill.skillRoot() == null
                ? ""
                : skill.skillRoot().toString().replace('\\', '/');
        StringBuilder out = new StringBuilder();
        if (!skillDir.isBlank()) {
            out.append("Base directory for this skill: ").append(skillDir).append("\n\n");
        }
        out.append(body);

        String result = out.toString();
        result = substituteArguments(result, args == null ? "" : args);
        result = result.replace("${AD_SKILL_DIR}", skillDir);
        result = result.replace("${CLAUDE_SKILL_DIR}", skillDir);
        return result.trim();
    }

    private String loadBundled(SkillEntry skill, String args) throws IOException {
        String catalogId = skill.bundledCatalogId();
        org.springframework.core.io.Resource resource = new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
                .getResource("classpath:skill-catalog/" + catalogId + "/SKILL.md");
        if (!resource.exists()) {
            throw new IOException("Bundled skill not found: " + catalogId);
        }
        String raw;
        try (var in = resource.getInputStream()) {
            raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String body = stripFrontmatter(raw).trim();
        if (body.isBlank()) {
            throw new IOException("Bundled skill body is empty: " + catalogId);
        }
        String result = substituteArguments(body, args == null ? "" : args);
        result = result.replace("${AD_SKILL_DIR}", "");
        result = result.replace("${CLAUDE_SKILL_DIR}", "");
        return result.trim();
    }

    public static String stripFrontmatter(String raw) {
        Matcher matcher = FRONTMATTER.matcher(raw);
        if (matcher.matches()) {
            return matcher.group(2);
        }
        return raw;
    }

    public static String substituteArguments(String template, String args) {
        String result = template;
        result = result.replace("$ARGUMENTS", args);
        result = result.replace("{{args}}", args);
        result = result.replace("{{arguments}}", args);
        if (!args.isBlank() && !result.contains(args)) {
            result = result + "\n\nUser arguments: " + args;
        }
        return result.trim();
    }
}
