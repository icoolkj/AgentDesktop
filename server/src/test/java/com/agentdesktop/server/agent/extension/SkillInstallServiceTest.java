package com.agentdesktop.server.agent.extension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SkillInstallServiceTest {

    private final ExtensionLoader extensionLoader = org.mockito.Mockito.mock(ExtensionLoader.class);
    private final SkillInstallService service = new SkillInstallService(extensionLoader);

    @Test
    void installsSkillFromDirectory(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("demo-skill");
        Files.createDirectories(source);
        Files.writeString(source.resolve("SKILL.md"), """
                ---
                description: Demo skill
                ---
                Do the thing.
                """);

        Path targetRoot = temp.resolve("skills-agent-desktop");
        var request = new com.agentdesktop.server.dto.SkillInstallRequestDto();
        request.setSourcePath(source.toString());
        request.setScope("user-ad");

        // Override target by using workspace trick - actually scope user-ad goes to real home dir
        // Use temp by testing resolveSkillDirectory and copy via workspace scope instead
        request.setScope("workspace");
        request.setWorkspaceRoot(temp.toString());
        Files.createDirectories(temp.resolve(".agent-desktop").resolve("skills"));

        var result = service.install(request);

        assertThat(result.isOk()).isTrue();
        assertThat(Files.exists(temp.resolve(".agent-desktop/skills/demo-skill/SKILL.md"))).isTrue();
    }

    @Test
    void uninstallRemovesManagedSkill(@TempDir Path temp) throws Exception {
        Path skillDir = temp.resolve(".agent-desktop/skills/demo-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "---\ndescription: x\n---\nbody");

        var result = service.uninstall("demo-skill", "workspace", temp.toString());
        assertThat(result.isOk()).isTrue();
        assertThat(Files.exists(skillDir)).isFalse();
    }

    @Test
    void listsCatalogEntries() {
        assertThat(service.listCatalog()).isNotEmpty();
        assertThat(service.listCatalog().stream().anyMatch(item -> "agent-desktop-dev".equals(item.get("id")))).isTrue();
    }
}
