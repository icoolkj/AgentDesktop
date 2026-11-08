package com.agentdesktop.server.agent.extension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectDirsUpToHomeTest {

    @Test
    void collectsClaudeSkillDirsUpToGitRoot(@TempDir Path temp) throws Exception {
        Path repo = temp.resolve("repo");
        Path pkg = repo.resolve("packages").resolve("app");
        Files.createDirectories(pkg);
        Files.createDirectories(repo.resolve(".git"));
        Path skillsDir = pkg.resolve(".claude").resolve("skills");
        Files.createDirectories(skillsDir);

        List<Path> dirs = ProjectDirsUpToHome.claudeSubdirs("skills", pkg.toString());

        assertThat(dirs).containsExactly(skillsDir);
    }

    @Test
    void stopsAtGitRootAndDoesNotIncludeParent(@TempDir Path temp) throws Exception {
        Path parent = temp.resolve("parent");
        Path repo = parent.resolve("repo");
        Files.createDirectories(repo.resolve(".git"));
        Path parentSkills = parent.resolve(".claude").resolve("skills");
        Files.createDirectories(parentSkills);
        Path repoSkills = repo.resolve(".claude").resolve("skills");
        Files.createDirectories(repoSkills);

        List<Path> dirs = ProjectDirsUpToHome.claudeSubdirs("skills", repo.toString());

        assertThat(dirs).containsExactly(repoSkills);
        assertThat(dirs).doesNotContain(parentSkills);
    }

    @Test
    void collectsAgentDesktopSkillDirs(@TempDir Path temp) throws Exception {
        Path root = temp.resolve("proj");
        Path skills = root.resolve(".agent-desktop").resolve("skills");
        Files.createDirectories(skills);

        List<Path> dirs = ProjectDirsUpToHome.agentDesktopSubdirs("skills", root.toString());

        assertThat(dirs).containsExactly(skills);
    }
}
