package com.agentdesktop.server.agent.extension;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlashCommandRegistryTest {

    private final SlashCommandRegistry registry = new SlashCommandRegistry(new SkillContentLoader(null), new SessionSkillState());

    @Test
    void resolvesBuiltInCommands() {
        assertThat(registry.resolve("/compact", ExtensionBundle.empty()))
                .isInstanceOf(SlashCommandResult.Compact.class);
        assertThat(registry.resolve("/context", ExtensionBundle.empty()))
                .isInstanceOf(SlashCommandResult.Context.class);
        assertThat(registry.resolve("hello", ExtensionBundle.empty()))
                .isEqualTo(SlashCommandResult.None.INSTANCE);
    }

    @Test
    void expandsCustomCommandFromBundle() {
        ExtensionBundle bundle = new ExtensionBundle(
                java.util.List.of(new SlashCommandDefinition(
                        "review",
                        "Review changes",
                        "Review the staged git diff and suggest improvements.",
                        "commands/review.md")),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of());
        SlashCommandResult result = registry.resolve("/review focus tests", bundle);
        assertThat(result).isInstanceOf(SlashCommandResult.PromptExpansion.class);
        SlashCommandResult.PromptExpansion expansion = (SlashCommandResult.PromptExpansion) result;
        assertThat(expansion.commandName()).isEqualTo("review");
        assertThat(expansion.expandedPrompt()).contains("Review the staged git diff");
        assertThat(expansion.expandedPrompt()).contains("focus tests");
    }

    @Test
    void expandsTemplatePlaceholders() {
        String expanded = SlashCommandRegistry.expandTemplate("Do: $ARGUMENTS", "fix login");
        assertThat(expanded).contains("fix login");
    }
}
