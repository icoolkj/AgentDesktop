package com.agentdesktop.server.dto;

public class SkillInstallRequestDto {

    /** Absolute path to a skill directory (must contain SKILL.md). */
    private String sourcePath;
    /** {@code user-ad} | {@code user} | {@code workspace} */
    private String scope = "user-ad";
    private String workspaceRoot;

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }
}
