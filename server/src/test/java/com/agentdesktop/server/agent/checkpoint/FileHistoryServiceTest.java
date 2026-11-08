package com.agentdesktop.server.agent.checkpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileHistoryServiceTest {

    @TempDir
    Path tempDir;

    private FileHistoryService service;
    private FileHistoryPersistence persistence;

    @BeforeEach
    void setUp() {
        persistence = FileHistoryPersistence.forDirectory(new ObjectMapper(), tempDir);
        service = new FileHistoryService(persistence);
    }

    @Test
    void persistsAcrossRestart() {
        String sessionId = "session-abc";
        service.beginTurn(sessionId, "turn-1");
        service.trackEdit(sessionId, "/tmp/demo.txt", "before");

        FileHistoryService reloaded = new FileHistoryService(persistence);
        assertThat(reloaded.listSnapshots(sessionId)).hasSize(1);
        assertThat(reloaded.listSnapshots(sessionId).get(0).fileBackups()).containsEntry("/tmp/demo.txt", "before");
    }

    @Test
    void computeRestoreUsesPersistedSnapshots() {
        String sessionId = "session-restore";
        service.beginTurn(sessionId, "turn-1");
        service.trackEdit(sessionId, "/tmp/a.txt", "old-a");
        service.beginTurn(sessionId, "turn-2");
        service.trackEdit(sessionId, "/tmp/b.txt", FileHistoryService.CREATED);

        FileHistoryService reloaded = new FileHistoryService(persistence);
        FileHistoryService.RestorePlan plan = reloaded.computeRestore(sessionId, "turn-1");
        assertThat(plan.writes()).containsEntry("/tmp/a.txt", "old-a");
        assertThat(plan.deletes()).contains("/tmp/b.txt");
    }

    @Test
    void clearSessionRemovesPersistedFile() {
        String sessionId = "session-clear";
        service.beginTurn(sessionId, "turn-1");
        service.clearSession(sessionId);

        FileHistoryService reloaded = new FileHistoryService(persistence);
        assertThat(reloaded.listSnapshots(sessionId)).isEmpty();
    }
}
