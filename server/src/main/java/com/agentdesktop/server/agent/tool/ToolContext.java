package com.agentdesktop.server.agent.tool;

import com.agentdesktop.server.agent.extension.AgentFeatureSettings;
import com.agentdesktop.server.agent.extension.ExtensionBundle;
import com.agentdesktop.server.agent.extension.SubagentTypes;
import com.agentdesktop.server.agent.permission.PermissionRuleSet;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public record ToolContext(
        String workspaceRoot,
        String openFilePath,
        Map<String, String> openDocuments,
        String permissionMode,
        String projectRules,
        PermissionRuleSet permissionRules,
        String sessionId,
        String turnId,
        AgentFeatureSettings features,
        ExtensionBundle extensionBundle,
        int subagentDepth,
        String subagentType,
        List<String> attachmentReadRoots,
        String requestModel,
        List<String> activeSkillAllowedTools) {

    public ToolContext(String workspaceRoot, String openFilePath) {
        this(workspaceRoot, openFilePath, Map.of(), "ask", null, PermissionRuleSet.EMPTY, null, null,
                AgentFeatureSettings.defaults(), ExtensionBundle.empty(), 0, null, List.of(), null, List.of());
    }

    public ToolContext(String workspaceRoot, String openFilePath, Map<String, String> openDocuments) {
        this(workspaceRoot, openFilePath, openDocuments, "ask", null, PermissionRuleSet.EMPTY, null, null,
                AgentFeatureSettings.defaults(), ExtensionBundle.empty(), 0, null, List.of(), null, List.of());
    }

    public ToolContext(
            String workspaceRoot,
            String openFilePath,
            Map<String, String> openDocuments,
            String permissionMode,
            String projectRules) {
        this(workspaceRoot, openFilePath, openDocuments, permissionMode, projectRules,
                PermissionRuleSet.EMPTY, null, null,
                AgentFeatureSettings.defaults(), ExtensionBundle.empty(), 0, null, List.of(), null, List.of());
    }

    public ToolContext {
        activeSkillAllowedTools = activeSkillAllowedTools == null ? List.of() : List.copyOf(activeSkillAllowedTools);
    }

    public boolean hasWorkspace() {
        return workspaceRoot != null && !workspaceRoot.isBlank();
    }

    public Path workspacePath() {
        return workspaceRoot == null ? null : Path.of(workspaceRoot);
    }

    public String permissionModeOrDefault() {
        return permissionMode == null || permissionMode.isBlank() ? "ask" : permissionMode.trim().toLowerCase();
    }

    public PermissionRuleSet permissionRulesOrEmpty() {
        return permissionRules == null ? PermissionRuleSet.EMPTY : permissionRules;
    }

    public AgentFeatureSettings featuresOrDefault() {
        return features == null ? AgentFeatureSettings.defaults() : features;
    }

    public ExtensionBundle extensionBundleOrEmpty() {
        return extensionBundle == null ? ExtensionBundle.empty() : extensionBundle;
    }

    public String subagentTypeOrDefault() {
        return subagentType == null || subagentType.isBlank()
                ? SubagentTypes.GENERAL
                : SubagentTypes.normalize(subagentType);
    }

    public List<String> attachmentReadRootsOrEmpty() {
        return attachmentReadRoots == null ? List.of() : attachmentReadRoots;
    }

    public List<String> activeSkillAllowedToolsOrEmpty() {
        return activeSkillAllowedTools;
    }

    public ToolContext withActiveSkillAllowedTools(List<String> allowedTools) {
        return new ToolContext(
                workspaceRoot,
                openFilePath,
                openDocuments,
                permissionMode,
                projectRules,
                permissionRules,
                sessionId,
                turnId,
                features,
                extensionBundle,
                subagentDepth,
                subagentType,
                attachmentReadRoots,
                requestModel,
                allowedTools);
    }

    public ToolContext withSubagentDepth(int depth) {
        return new ToolContext(
                workspaceRoot,
                openFilePath,
                openDocuments,
                permissionMode,
                projectRules,
                permissionRules,
                sessionId,
                turnId,
                features,
                extensionBundle,
                depth,
                subagentType,
                attachmentReadRoots,
                requestModel,
                activeSkillAllowedTools);
    }

    public ToolContext withSubagentProfile(String type, int depth) {
        String normalized = SubagentTypes.normalize(type);
        String mode = permissionMode;
        String modeOverride = SubagentTypes.permissionModeForType(normalized);
        if (modeOverride != null) {
            mode = modeOverride;
        }
        return new ToolContext(
                workspaceRoot,
                openFilePath,
                openDocuments,
                mode,
                projectRules,
                permissionRules,
                sessionId,
                turnId,
                features,
                extensionBundle,
                depth,
                normalized,
                attachmentReadRoots,
                requestModel,
                activeSkillAllowedTools);
    }

    /** In-memory editor buffer for absolute path, if the client sent a snapshot. */
    public String openDocumentContent(Path absoluteFile) {
        if (openDocuments == null || openDocuments.isEmpty() || absoluteFile == null) {
            return null;
        }
        Path normalized = absoluteFile.toAbsolutePath().normalize();
        for (Map.Entry<String, String> entry : openDocuments.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            try {
                if (Paths.get(entry.getKey()).toAbsolutePath().normalize().equals(normalized)) {
                    return entry.getValue();
                }
            } catch (Exception ignored) {
                // skip malformed path keys
            }
        }
        return null;
    }
}
