package com.agentdesktop.server.agent.extension;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BundledSkillRegistryTest {

    private final BundledSkillRegistry bundledSkillRegistry = new BundledSkillRegistry();

    @Test
    void loadsAgentDesktopDevBundledSkill() {
        var skills = bundledSkillRegistry.bundledSkills();
        assertThat(skills).isNotEmpty();
        SkillEntry entry = skills.stream()
                .filter(s -> "agent-desktop-dev".equals(s.name()))
                .findFirst()
                .orElseThrow();
        assertThat(entry.isBundledInBinary()).isTrue();
        assertThat(entry.source()).isEqualTo("bundled");
    }
}
