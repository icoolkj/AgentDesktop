package com.agentdesktop.server.agent.extension;

/**
 * Result of a hook chain — may block or supply a modified user prompt.
 */
public record HookOutcome(boolean allowed, String reason, String updatedUserPrompt) {

    public static HookOutcome allow() {
        return new HookOutcome(true, null, null);
    }

    public static HookOutcome allowWithPrompt(String updatedPrompt) {
        return new HookOutcome(true, null, updatedPrompt);
    }

    public static HookOutcome deny(String reason) {
        return new HookOutcome(false, reason == null ? "Blocked by hook" : reason, null);
    }
}
