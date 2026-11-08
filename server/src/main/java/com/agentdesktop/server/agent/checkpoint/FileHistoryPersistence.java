package com.agentdesktop.server.agent.checkpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Persists {@link FileHistoryService} snapshots as JSON under {@code ~/.agent-desktop/file-history/}.
 */
@Component
public class FileHistoryPersistence {

    private static final Logger log = LoggerFactory.getLogger(FileHistoryPersistence.class);

    private final ObjectMapper objectMapper;
    private final Path baseDir;

    @Autowired
    public FileHistoryPersistence(ObjectMapper objectMapper) {
        this(objectMapper, defaultBaseDir());
    }

    /** Tests and custom storage roots. */
    public static FileHistoryPersistence forDirectory(ObjectMapper objectMapper, Path baseDir) {
        return new FileHistoryPersistence(objectMapper, baseDir);
    }

    private FileHistoryPersistence(ObjectMapper objectMapper, Path baseDir) {
        this.objectMapper = objectMapper;
        this.baseDir = baseDir;
    }

    private static Path defaultBaseDir() {
        return Path.of(System.getProperty("user.home"), ".agent-desktop", "file-history");
    }

    public Deque<FileHistoryService.FileHistorySnapshot> load(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return new ArrayDeque<>();
        }
        Path file = fileForSession(sessionId);
        if (!Files.isRegularFile(file)) {
            return new ArrayDeque<>();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            PersistedFileHistory persisted = objectMapper.readValue(json, PersistedFileHistory.class);
            Deque<FileHistoryService.FileHistorySnapshot> deque = new ArrayDeque<>();
            if (persisted.snapshots() != null) {
                for (PersistedSnapshot snapshot : persisted.snapshots()) {
                    deque.addLast(new FileHistoryService.FileHistorySnapshot(
                            snapshot.turnId(),
                            snapshot.fileBackups() == null ? Map.of() : Map.copyOf(snapshot.fileBackups()),
                            snapshot.timestampMs()));
                }
            }
            return deque;
        } catch (Exception e) {
            log.warn("Failed to load file history for session {}: {}", sessionId, e.getMessage());
            return new ArrayDeque<>();
        }
    }

    public void save(String sessionId, Deque<FileHistoryService.FileHistorySnapshot> snapshots) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            Files.createDirectories(baseDir);
            Path file = fileForSession(sessionId);
            if (snapshots == null || snapshots.isEmpty()) {
                Files.deleteIfExists(file);
                return;
            }
            List<PersistedSnapshot> list = new ArrayList<>();
            for (FileHistoryService.FileHistorySnapshot snapshot : snapshots) {
                list.add(new PersistedSnapshot(
                        snapshot.turnId(),
                        snapshot.timestampMs(),
                        Map.copyOf(snapshot.fileBackups())));
            }
            PersistedFileHistory payload = new PersistedFileHistory(sessionId, list);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), payload);
        } catch (IOException e) {
            log.warn("Failed to save file history for session {}: {}", sessionId, e.getMessage());
        }
    }

    public void delete(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(fileForSession(sessionId));
        } catch (IOException e) {
            log.warn("Failed to delete file history for session {}: {}", sessionId, e.getMessage());
        }
    }

    private Path fileForSession(String sessionId) {
        String safe = sessionId.replaceAll("[^a-zA-Z0-9._-]", "_");
        return baseDir.resolve(safe + ".json");
    }

    record PersistedFileHistory(String sessionId, List<PersistedSnapshot> snapshots) {
    }

    record PersistedSnapshot(String turnId, long timestampMs, Map<String, String> fileBackups) {
    }
}
