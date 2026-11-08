package com.agentdesktop.server.agent.extension;

import java.nio.file.Path;
import java.util.List;

/** Metadata for a discovered skill (SKILL.md frontmatter + paths). Body loaded on invoke only. */
public record SkillEntry(
        String name,
        String description,
        String whenToUse,
        Path skillRoot,
        Path skillFile,
        String source,
        boolean userInvocable,
        boolean disableModelInvocation,
        List<String> pathPatterns,
        List<String> allowedTools,
        boolean forkContext,
        String agentType,
        String mcpServer,
        String mcpResourceUri,
        String mcpPromptName,
        String bundledCatalogId) {

    public SkillEntry {
        pathPatterns = pathPatterns == null ? List.of() : List.copyOf(pathPatterns);
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
    }

    /** File-based or bundled-on-disk skill. */
    public SkillEntry(
            String name,
            String description,
            String whenToUse,
            Path skillRoot,
            Path skillFile,
            String source,
            boolean userInvocable,
            boolean disableModelInvocation,
            List<String> pathPatterns,
            List<String> allowedTools,
            boolean forkContext,
            String agentType) {
        this(name, description, whenToUse, skillRoot, skillFile, source, userInvocable,
                disableModelInvocation, pathPatterns, allowedTools, forkContext, agentType,
                null, null, null, null);
    }

    public boolean isMcpSkill() {
        return "mcp".equals(source) && (mcpResourceUri != null || mcpPromptName != null);
    }

    public boolean isBundledInBinary() {
        return "bundled".equals(source) && bundledCatalogId != null;
    }

    public boolean modelInvocable() {
        return !disableModelInvocation;
    }

    public boolean conditional() {
        return !pathPatterns.isEmpty();
    }

    public String listingDescription() {
        if (whenToUse != null && !whenToUse.isBlank()) {
            return description + " - " + whenToUse;
        }
        return description;
    }
}
