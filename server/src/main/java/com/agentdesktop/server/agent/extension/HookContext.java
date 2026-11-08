package com.agentdesktop.server.agent.extension;

/**
 * Context passed to hook commands via environment variables.
 */
public record HookContext(
        String event,
        String toolName,
        String toolInputJson,
        String toolOutput,
        Boolean toolOk,
        String userPrompt,
        String workspaceRoot,
        String sessionId,
        String subagentType,
        int subagentDepth) {

    public static HookContext forTool(
            String event,
            String toolName,
            String toolInputJson,
            String toolOutput,
            boolean toolOk,
            String workspaceRoot,
            String sessionId) {
        return new HookContext(
                event,
                toolName,
                toolInputJson,
                toolOutput,
                toolOk,
                null,
                workspaceRoot,
                sessionId,
                null,
                0);
    }

    public static HookContext forUserPrompt(String userPrompt, String workspaceRoot, String sessionId) {
        return new HookContext(
                HookEvents.USER_PROMPT_SUBMIT,
                null,
                null,
                null,
                null,
                userPrompt,
                workspaceRoot,
                sessionId,
                null,
                0);
    }

    public static HookContext forSession(String event, String workspaceRoot, String sessionId) {
        return new HookContext(event, null, null, null, null, null, workspaceRoot, sessionId, null, 0);
    }

    public static HookContext forSubagentStop(
            String subagentType,
            int depth,
            String summary,
            String workspaceRoot,
            String sessionId) {
        return new HookContext(
                HookEvents.SUBAGENT_STOP,
                null,
                null,
                summary,
                true,
                null,
                workspaceRoot,
                sessionId,
                subagentType,
                depth);
    }
}
