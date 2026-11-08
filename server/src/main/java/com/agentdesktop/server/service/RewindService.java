package com.agentdesktop.server.service;

import com.agentdesktop.server.agent.checkpoint.FileHistoryService;
import com.agentdesktop.server.agent.checkpoint.ReadFileStateService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class RewindService {

    private final FileHistoryService fileHistoryService;
    private final ReadFileStateService readFileStateService;

    public RewindService(FileHistoryService fileHistoryService, ReadFileStateService readFileStateService) {
        this.fileHistoryService = fileHistoryService;
        this.readFileStateService = readFileStateService;
    }

    public record CheckpointInfo(String turnId, long timestampMs, int fileCount) {
    }

    public record RestoreResult(int restoredFiles, int deletedFiles, List<String> paths) {
    }

    public List<CheckpointInfo> listCheckpoints(String sessionId) {
        return fileHistoryService.listSnapshots(sessionId).stream()
                .map(s -> new CheckpointInfo(
                        s.turnId(),
                        s.timestampMs(),
                        s.fileBackups().size()))
                .toList();
    }

    /**
     * Restore workspace files to state before edits in {@code turnId} and later turns.
     */
    public RestoreResult restoreFiles(String sessionId, String turnId) throws Exception {
        FileHistoryService.RestorePlan plan = fileHistoryService.computeRestore(sessionId, turnId);

        List<String> touched = new ArrayList<>();
        for (Map.Entry<String, String> entry : plan.writes().entrySet()) {
            Path file = Path.of(entry.getKey());
            String content = entry.getValue();
            if (FileHistoryService.CREATED.equals(content)) {
                Files.deleteIfExists(file);
                touched.add(entry.getKey());
            } else {
                Path parent = file.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(file, content == null ? "" : content, StandardCharsets.UTF_8);
                touched.add(entry.getKey());
            }
        }
        for (String deletePath : plan.deletes()) {
            Files.deleteIfExists(Path.of(deletePath));
            touched.add(deletePath);
        }

        fileHistoryService.truncateAfter(sessionId, turnId);
        readFileStateService.clearSession(sessionId);
        return new RestoreResult(plan.writes().size(), plan.deletes().size(), touched);
    }
}
