package com.agentdesktop.server.agent.extension;

import java.util.List;

public record ExtensionBundle(
        List<SlashCommandDefinition> slashCommands,
        List<SkillEntry> skills,
        List<HookDefinition> hooks,
        List<PluginRegistry.PluginManifest> plugins) {

    public static ExtensionBundle empty() {
        return new ExtensionBundle(List.of(), List.of(), List.of(), List.of());
    }
}
