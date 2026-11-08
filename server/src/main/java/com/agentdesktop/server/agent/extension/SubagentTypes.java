package com.agentdesktop.server.agent.extension;

/**
 * Claude Code Task subagent types.
 */
public final class SubagentTypes {

    public static final String GENERAL = "generalPurpose";
    public static final String EXPLORE = "explore";
    public static final String SHELL = "shell";

    private SubagentTypes() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return GENERAL;
        }
        return switch (raw.trim().toLowerCase()) {
            case "explore", "readonly", "read-only" -> EXPLORE;
            case "shell", "bash", "terminal" -> SHELL;
            case "generalpurpose", "general", "default" -> GENERAL;
            default -> raw.trim();
        };
    }

    public static String permissionModeForType(String type) {
        if (EXPLORE.equals(type)) {
            return "plan";
        }
        return null;
    }
}
