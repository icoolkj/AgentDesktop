package com.agentdesktop.server.agent.extension;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionSkillStateTest {

    @Test
    void listingIncludesActivatedConditionalSkills() {
        SessionSkillState state = new SessionSkillState();
        SkillEntry conditional = new SkillEntry(
                "java-skill", "Java", null, Path.of("x"), Path.of("x/SKILL.md"), "project", true, false,
                List.of("**/*.java"), List.of(), false, null);
        SkillEntry unconditional = new SkillEntry(
                "general", "General", null, Path.of("y"), Path.of("y/SKILL.md"), "user", true, false,
                List.of(), List.of(), false, null);

        state.activateConditional("sess", "java-skill");
        List<SkillEntry> listing = state.listingSkills("sess", List.of(conditional, unconditional));

        assertThat(listing).extracting(SkillEntry::name).containsExactlyInAnyOrder("java-skill", "general");
    }
}
