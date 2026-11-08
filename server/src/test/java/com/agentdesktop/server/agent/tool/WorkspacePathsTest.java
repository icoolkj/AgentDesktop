package com.agentdesktop.server.agent.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspacePathsTest {

    @TempDir
    Path tempDir;

    @Test
    void resolve_allowsAttachmentStorePathOutsideWorkspace() throws Exception {
        Path workspace = tempDir.resolve("workspace");
        Path attachments = tempDir.resolve("store/attachments/s/u");
        Path attachment = attachments.resolve("file.pdf");
        Files.createDirectories(attachments);

        ToolContext context = new ToolContext(
                workspace.toString(),
                null,
                Map.of(),
                "auto",
                null,
                com.agentdesktop.server.agent.permission.PermissionRuleSet.EMPTY,
                "s",
                "u",
                com.agentdesktop.server.agent.extension.AgentFeatureSettings.defaults(),
                com.agentdesktop.server.agent.extension.ExtensionBundle.empty(),
                0,
                null,
                List.of(attachments.toString()),
                null,
                List.of());

        Path resolved = WorkspacePaths.resolve(context, attachment.toString());
        assertThat(resolved).isEqualTo(attachment);
    }

    @Test
    void resolve_rejectsPathOutsideWorkspaceAndAttachments() {
        Path workspace = tempDir.resolve("workspace");
        Path outside = tempDir.resolve("outside/secret.txt");

        ToolContext context = new ToolContext(workspace.toString(), null);
        assertThatThrownBy(() -> WorkspacePaths.resolve(context, outside.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes workspace");
    }
}
