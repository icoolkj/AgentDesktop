package com.agentdesktop.server.agent.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubMarketplaceFetcherTest {

    private MockWebServer mockWebServer;
    private GitHubMarketplaceFetcher fetcher;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        objectMapper = new ObjectMapper();
        String baseUrl = mockWebServer.url("/").toString();
        String hostBase = baseUrl.substring(0, baseUrl.length() - 1);
        fetcher = new GitHubMarketplaceFetcher(
                objectMapper,
                HttpClient.newHttpClient(),
                hostBase,
                hostBase + "/raw");
    }

    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    void parseGitHubUrl() {
        GitHubMarketplaceFetcher.GitHubRepo repo =
                GitHubMarketplaceFetcher.parseGitHubUrl("https://github.com/AikidoSec/aikido-claude-plugin.git");
        assertThat(repo.owner()).isEqualTo("AikidoSec");
        assertThat(repo.repo()).isEqualTo("aikido-claude-plugin");
    }

    @Test
    void fetchExternalUrlPlugin() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                        {
                          "sha": "79ac524f87c9faa9a356ff3d495b8a5b77e01bbd",
                          "tree": [
                            {"path": "plugin.json", "type": "blob", "sha": "f1"},
                            {"path": "commands/hello.md", "type": "blob", "sha": "f2"}
                          ],
                          "truncated": false
                        }
                        """));
        mockWebServer.enqueue(new MockResponse().setBody("""
                {"name":"demo","version":"1.0.0"}
                """));
        mockWebServer.enqueue(new MockResponse().setBody("# Hello"));

        var entry = objectMapper.readTree("""
                {
                  "name": "demo",
                  "source": {
                    "source": "url",
                    "url": "https://github.com/example/demo-plugin.git",
                    "sha": "79ac524f87c9faa9a356ff3d495b8a5b77e01bbd"
                  }
                }
                """);

        Path temp = Files.createTempDirectory("ad-fetch-test");
        try {
            Path pluginDir = fetcher.fetchPluginSource(entry, temp);
            assertThat(Files.isRegularFile(pluginDir.resolve("plugin.json"))).isTrue();
            assertThat(Files.isRegularFile(pluginDir.resolve("commands").resolve("hello.md"))).isTrue();
        } finally {
            deleteRecursive(temp);
        }
    }

    @Test
    void fetchGitSubdirPlugin() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                        {
                          "sha": "295ab93b7d765912ee1a0dc7f1abb0ecaf73f138",
                          "tree": [
                            {"path": "plugins/airtable/plugin.json", "type": "blob", "sha": "f1"},
                            {"path": "plugins/airtable/skills/use.md", "type": "blob", "sha": "f2"},
                            {"path": "README.md", "type": "blob", "sha": "f3"}
                          ],
                          "truncated": false
                        }
                        """));
        mockWebServer.enqueue(new MockResponse().setBody("""
                {"name":"airtable","version":"0.1.0"}
                """));
        mockWebServer.enqueue(new MockResponse().setBody("# Skill"));

        var entry = objectMapper.readTree("""
                {
                  "name": "airtable",
                  "source": {
                    "source": "git-subdir",
                    "url": "https://github.com/Airtable/skills.git",
                    "path": "plugins/airtable",
                    "sha": "295ab93b7d765912ee1a0dc7f1abb0ecaf73f138"
                  }
                }
                """);

        Path temp = Files.createTempDirectory("ad-fetch-test");
        try {
            Path pluginDir = fetcher.fetchPluginSource(entry, temp);
            assertThat(Files.isRegularFile(pluginDir.resolve("plugin.json"))).isTrue();
            assertThat(Files.readString(pluginDir.resolve("plugin.json"))).contains("airtable");
            assertThat(Files.isRegularFile(pluginDir.resolve("skills").resolve("use.md"))).isTrue();
            assertThat(Files.exists(pluginDir.resolve("README.md"))).isFalse();
        } finally {
            deleteRecursive(temp);
        }
    }

    private static void deleteRecursive(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                    // test cleanup
                }
            });
        }
    }
}
