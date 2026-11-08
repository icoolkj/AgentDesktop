package com.agentdesktop.server.agent.loop;

/**
 * Claude Code autocompact (inside queryLoop) vs manual {@code /compact} command.
 */
public enum CompactMode {
    AUTO,
    MANUAL,
    /** Reactive compact after API 413 / context-length errors — Claude {@code reactiveCompact.ts}. */
    REACTIVE
}
