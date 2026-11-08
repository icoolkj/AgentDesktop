package com.agentdesktop.server.dto;

public class PluginInstallRequestDto {

    /** Absolute path to a plugin directory (must contain plugin.json). */
    private String sourcePath;
    /** {@code user} = ~/.agent-desktop/plugins · {@code workspace} = .agent-desktop/plugins */
    private String scope = "user";
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
