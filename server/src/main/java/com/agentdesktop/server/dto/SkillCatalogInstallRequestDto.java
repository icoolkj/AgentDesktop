package com.agentdesktop.server.dto;

public class SkillCatalogInstallRequestDto {

    private String catalogId;
    /** {@code user-ad} | {@code user} | {@code workspace} */
    private String scope = "user-ad";
    private String workspaceRoot;

    public String getCatalogId() {
        return catalogId;
    }

    public void setCatalogId(String catalogId) {
        this.catalogId = catalogId;
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
