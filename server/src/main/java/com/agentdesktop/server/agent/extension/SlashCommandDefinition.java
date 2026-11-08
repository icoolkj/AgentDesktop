package com.agentdesktop.server.agent.extension;

public record SlashCommandDefinition(
        String name,
        String description,
        String promptTemplate,
        String sourcePath) {
}
