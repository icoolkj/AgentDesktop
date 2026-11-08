package com.agentdesktop.server.service;

import com.agentdesktop.server.agent.checkpoint.FileHistoryPersistence;
import com.agentdesktop.server.agent.checkpoint.FileHistoryService;
import com.agentdesktop.server.agent.checkpoint.ReadFileStateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RewindServiceTest {

    @TempDir
    Path tempDir;

    private FileHistoryService fileHistoryService;
    private ReadFileStateService readFileStateService;
    private RewindService rewindService;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        fileHistoryService = new FileHistoryService(FileHistoryPersistence.forDirectory(mapper, tempDir));
        readFileStateService = new ReadFileStateService();
        rewindService = new RewindService(fileHistoryService, readFileStateService);
    }

    @Test
    void restoreFilesRewindsDiskAndClearsReadState() throws Exception {
        Path file = tempDir.resolve("demo.txt");
        Files.writeString(file, "after-agent", StandardCharsets.UTF_8);

        String sessionId = "sess-rewind";
        fileHistoryService.beginTurn(sessionId, "turn-1");
        fileHistoryService.trackEdit(sessionId, file.toString(), "before-agent");
        readFileStateService.recordRead(sessionId, file, "after-agent", 1, 1, 1, false);

        RewindService.RestoreResult result = rewindService.restoreFiles(sessionId, "turn-1");

        assertThat(Files.readString(file)).isEqualTo("before-agent");
        assertThat(result.paths()).contains(file.toString());
        assertThat(readFileStateService.validateMutation(
                sessionId,
                file,
                null,
                ReadFileStateService.MutationKind.WRITE)).isPresent();
    }

    @Test
    void listCheckpointsReflectsFileHistory() {
        String sessionId = "sess-list";
        fileHistoryService.beginTurn(sessionId, "turn-a");
        fileHistoryService.trackEdit(sessionId, "/tmp/x.txt", "v1");

        assertThat(rewindService.listCheckpoints(sessionId))
                .hasSize(1)
                .first()
                .satisfies(info -> {
                    assertThat(info.turnId()).isEqualTo("turn-a");
                    assertThat(info.fileCount()).isEqualTo(1);
                });
    }
}
