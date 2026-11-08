package com.agentdesktop.server.transcript;

import com.agentdesktop.server.dto.LlmHistoryMessageDto;
import com.agentdesktop.server.dto.LlmHistoryMessageDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ServerTranscriptLoaderTest {

    @TempDir
    Path temp;

    @Test
    void loadsUserAssistantChainFromJsonl() throws Exception {
        String prevHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", temp.toString());
            Path projects = temp.resolve(".agent-desktop").resolve("projects")
                    .resolve(AgentDesktopPaths.sanitizePath("D:/proj"));
            Files.createDirectories(projects);
            String sessionId = "11111111-1111-1111-1111-111111111111";
            Path file = projects.resolve(sessionId + ".jsonl");
            Files.writeString(file, """
                    {"type":"user","uuid":"u1","parentUuid":null,"timestamp":"2026-01-01T00:00:00.000Z","message":{"role":"user","content":"hello"}}
                    {"type":"assistant","uuid":"a1","parentUuid":"u1","timestamp":"2026-01-01T00:00:01.000Z","message":{"role":"assistant","content":"hi there"}}
                    """);

            var loader = new ServerTranscriptLoader(new ObjectMapper());
            List<LlmHistoryMessageDto> history = loader.loadHistory("11111111-1111-1111-1111-111111111111", "D:/proj");
            assertThat(history).hasSize(2);
            assertThat(history.get(0).getContent()).isEqualTo("hello");
            assertThat(history.get(1).getContent()).isEqualTo("hi there");
        } finally {
            if (prevHome != null) {
                System.setProperty("user.home", prevHome);
            }
        }
    }

    @Test
    void sanitizePathMatchesDesktop() {
        assertThat(AgentDesktopPaths.sanitizePath("D:/idea2024/Workspace2025/crm/yshop-crm"))
                .isEqualTo("D--idea2024-Workspace2025-crm-yshop-crm");
    }
}
