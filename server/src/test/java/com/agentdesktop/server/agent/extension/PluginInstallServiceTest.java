package com.agentdesktop.server.agent.extension;

import com.agentdesktop.server.dto.PluginInstallRequestDto;
import com.agentdesktop.server.dto.PluginInstallResultDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PluginInstallServiceTest {

    private PluginInstallService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new PluginInstallService(new PluginRegistry(new ObjectMapper()));
    }

    @Test
    void installsPluginIntoWorkspacePluginsDir() throws Exception {
        Path workspace = tempDir.resolve("workspace");
        Path source = createSamplePlugin(tempDir.resolve("source-demo"));

        PluginInstallRequestDto request = new PluginInstallRequestDto();
        request.setSourcePath(source.toString());
        request.setScope("workspace");
        request.setWorkspaceRoot(workspace.toString());

        PluginInstallResultDto result = service.install(request);

        assertThat(result.isOk()).isTrue();
        assertThat(result.getName()).isEqualTo("demo-plugin");
        Path installed = workspace.resolve(".agent-desktop").resolve("plugins").resolve("demo-plugin");
        assertThat(Files.isRegularFile(installed.resolve("plugin.json"))).isTrue();
        assertThat(Files.isRegularFile(installed.resolve("commands").resolve("hello.md"))).isTrue();
    }

    @Test
    void reinstallOverwritesExistingPlugin() throws Exception {
        Path workspace = tempDir.resolve("workspace2");
        Path source = createSamplePlugin(tempDir.resolve("source-demo2"));

        PluginInstallRequestDto request = new PluginInstallRequestDto();
        request.setSourcePath(source.toString());
        request.setScope("workspace");
        request.setWorkspaceRoot(workspace.toString());

        assertThat(service.install(request).isOk()).isTrue();
        Files.writeString(source.resolve("marker.txt"), "v2");
        assertThat(service.install(request).isOk()).isTrue();

        Path installed = workspace.resolve(".agent-desktop").resolve("plugins").resolve("demo-plugin");
        assertThat(Files.readString(installed.resolve("marker.txt"))).isEqualTo("v2");
    }

    @Test
    void uninstallRemovesWorkspacePlugin() throws Exception {
        Path workspace = tempDir.resolve("workspace3");
        Path source = createSamplePlugin(tempDir.resolve("source-demo3"));

        PluginInstallRequestDto request = new PluginInstallRequestDto();
        request.setSourcePath(source.toString());
        request.setScope("workspace");
        request.setWorkspaceRoot(workspace.toString());
        service.install(request);

        PluginInstallResultDto removed = service.uninstall("demo-plugin", "workspace", workspace.toString());
        assertThat(removed.isOk()).isTrue();
        Path installed = workspace.resolve(".agent-desktop").resolve("plugins").resolve("demo-plugin");
        assertThat(Files.exists(installed)).isFalse();
    }

    @Test
    void rejectsDirectoryWithoutManifest() {
        Path source = tempDir.resolve("empty-plugin");
        try {
            Files.createDirectories(source);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        PluginInstallRequestDto request = new PluginInstallRequestDto();
        request.setSourcePath(source.toString());
        request.setScope("workspace");
        request.setWorkspaceRoot(tempDir.resolve("ws").toString());

        PluginInstallResultDto result = service.install(request);
        assertThat(result.isOk()).isFalse();
        assertThat(result.getMessage()).contains("plugin.json");
    }

    private static Path createSamplePlugin(Path source) throws Exception {
        Files.createDirectories(source.resolve("commands"));
        Files.writeString(
                source.resolve("plugin.json"),
                """
                        {
                          "name": "demo-plugin",
                          "version": "1.0.0",
                          "description": "Test plugin",
                          "commands": "commands"
                        }
                        """);
        Files.writeString(source.resolve("commands").resolve("hello.md"), "# Hello");
        return source;
    }
}
