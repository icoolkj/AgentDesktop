package com.agentdesktop.server.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * chat.send 参数（与 shared/ ChatSendParams 对齐）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatSendParams {

    private String message;
    private String sessionId;
    private String workspaceRoot;
    private String openFilePath;
    private java.util.List<EditorDocumentSnapshotDto> editorDocuments;
    private java.util.List<LlmHistoryMessageDto> history;
    private java.util.List<ChatAttachmentDto> attachments;
    private String projectRules;
    private String permissionMode;
    /** Transcript user message UUID — Claude fileHistory snapshot key. */
    private String userMessageId;
    /** Git status summary for system prompt. */
    private String gitStatusSummary;
    /** Per-request model override from composer (null = server default). */
    private String model;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getGitStatusSummary() {
        return gitStatusSummary;
    }

    public void setGitStatusSummary(String gitStatusSummary) {
        this.gitStatusSummary = gitStatusSummary;
    }

    public String getUserMessageId() {
        return userMessageId;
    }

    public void setUserMessageId(String userMessageId) {
        this.userMessageId = userMessageId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public String getOpenFilePath() {
        return openFilePath;
    }

    public void setOpenFilePath(String openFilePath) {
        this.openFilePath = openFilePath;
    }

    public java.util.List<EditorDocumentSnapshotDto> getEditorDocuments() {
        return editorDocuments;
    }

    public void setEditorDocuments(java.util.List<EditorDocumentSnapshotDto> editorDocuments) {
        this.editorDocuments = editorDocuments;
    }

    public java.util.List<LlmHistoryMessageDto> getHistory() {
        return history;
    }

    public void setHistory(java.util.List<LlmHistoryMessageDto> history) {
        this.history = history;
    }

    public java.util.List<ChatAttachmentDto> getAttachments() {
        return attachments;
    }

    public void setAttachments(java.util.List<ChatAttachmentDto> attachments) {
        this.attachments = attachments;
    }

    public String getProjectRules() {
        return projectRules;
    }

    public void setProjectRules(String projectRules) {
        this.projectRules = projectRules;
    }

    public String getPermissionMode() {
        return permissionMode;
    }

    public void setPermissionMode(String permissionMode) {
        this.permissionMode = permissionMode;
    }
}
