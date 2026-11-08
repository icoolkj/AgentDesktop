package com.agentdesktop.server.agent.extension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillCatalogTest {

    private final SkillCatalog catalog = new SkillCatalog();

    @Test
    void loadsSkillMetadataWithoutBody(@TempDir Path skillsRoot) throws Exception {
        Path skillDir = skillsRoot.resolve("my-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                description: Test skill
                user-invocable: true
                disable-model-invocation: false
                ---
                # Body should not appear in catalog
                Secret instructions here.
                """);

        List<SkillEntry> skills = catalog.loadFromDirs(List.of(skillsRoot));

        assertThat(skills).hasSize(1);
        SkillEntry entry = skills.get(0);
        assertThat(entry.name()).isEqualTo("my-skill");
        assertThat(entry.description()).isEqualTo("Test skill");
        assertThat(entry.modelInvocable()).isTrue();
        assertThat(entry.userInvocable()).isTrue();
    }

    @Test
    void findByNameIsCaseInsensitive() {
        SkillEntry entry = new SkillEntry(
                "demo", "d", null, Path.of("x"), Path.of("x/SKILL.md"), "user", true, false,
                List.of(), List.of(), false, null);
        assertThat(SkillCatalog.findByName(List.of(entry), "DEMO")).isEqualTo(entry);
    }
}
