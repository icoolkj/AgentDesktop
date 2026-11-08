package com.agentdesktop.server.agent.extension;

/**
 * Outcome of resolving a user slash command before the agent loop.
 */
public sealed interface SlashCommandResult permits
        SlashCommandResult.Compact,
        SlashCommandResult.Context,
        SlashCommandResult.PromptExpansion,
        SlashCommandResult.None {

    record Compact(String instructions) implements SlashCommandResult {
    }

    record Context() implements SlashCommandResult {
    }

    /** Custom command from {@code commands/*.md} — expands to a user prompt. */
    record PromptExpansion(String expandedPrompt, String commandName) implements SlashCommandResult {
    }

    record None() implements SlashCommandResult {
        public static final None INSTANCE = new None();
    }
}
