package com.agentdesktop.server.agent.extension;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillListingFormatterTest {

    @Test
    void formatsModelInvocableSkillsOnly() {
        SkillEntry hidden = new SkillEntry(
                "hidden", "Hidden", null, Path.of("a"), Path.of("a/SKILL.md"), "user", true, true,
                List.of(), List.of(), false, null);
        SkillEntry visible = new SkillEntry(
                "demo", "Demo skill", "when busy", Path.of("b"), Path.of("b/SKILL.md"), "project", true, false,
                List.of(), List.of(), false, null);

        String listing = SkillListingFormatter.format(List.of(hidden, visible), 8000);

        assertThat(listing).contains("- demo:");
        assertThat(listing).doesNotContain("hidden");
    }

    @Test
    void truncatesLongDescriptions() {
        String longDesc = "x".repeat(300);
        SkillEntry skill = new SkillEntry(
                "long", longDesc, null, Path.of("c"), Path.of("c/SKILL.md"), "user", true, false,
                List.of(), List.of(), false, null);

        String listing = SkillListingFormatter.format(List.of(skill), 8000);

        assertThat(listing.length()).isLessThan(longDesc.length() + 20);
        assertThat(listing).endsWith("\u2026");
    }
}
