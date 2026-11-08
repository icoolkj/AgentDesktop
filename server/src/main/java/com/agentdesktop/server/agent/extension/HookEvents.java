package com.agentdesktop.server.agent.extension;

/**
 * Claude Code hook events — {@code settings.json} / {@code hooks.json} compatible.
 */
public final class HookEvents {

    public static final String PRE_TOOL_USE = "PreToolUse";
    public static final String POST_TOOL_USE = "PostToolUse";
    public static final String USER_PROMPT_SUBMIT = "UserPromptSubmit";
    public static final String STOP = "Stop";
    public static final String SESSION_START = "SessionStart";
    public static final String SESSION_END = "SessionEnd";
    public static final String SUBAGENT_STOP = "SubagentStop";

    private HookEvents() {
    }
}
