package com.agentdesktop.server.agent.checkpoint;

import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-session file checkpoints — Claude Code {@code fileHistory.ts} parity (max 100 turns, JSON persist).
 */
@Service
public class FileHistoryService {

    public static final String CREATED = "__FILE_HISTORY_CREATED__";

    private static final int MAX_SNAPSHOTS = 100;

    private final FileHistoryPersistence persistence;
    private final Map<String, Deque<FileHistorySnapshot>> bySession = new ConcurrentHashMap<>();
    private final Set<String> loadedSessions = ConcurrentHashMap.newKeySet();

    public FileHistoryService(FileHistoryPersistence persistence) {
        this.persistence = persistence;
    }

    public void beginTurn(String sessionId, String turnId) {
        if (sessionId == null || sessionId.isBlank() || turnId == null || turnId.isBlank()) {
            return;
        }
        ensureLoaded(sessionId);
        Deque<FileHistorySnapshot> deque = bySession.computeIfAbsent(sessionId, key -> new ArrayDeque<>());
        deque.addLast(new FileHistorySnapshot(turnId, new HashMap<>(), System.currentTimeMillis()));
        while (deque.size() > MAX_SNAPSHOTS) {
            deque.removeFirst();
        }
        persist(sessionId);
    }

    public void trackEdit(String sessionId, String absolutePath, String previousContent) {
        if (sessionId == null || absolutePath == null) {
            return;
        }
        ensureLoaded(sessionId);
        Deque<FileHistorySnapshot> deque = bySession.get(sessionId);
        if (deque == null || deque.isEmpty()) {
            return;
        }
        FileHistorySnapshot latest = deque.peekLast();
        String backup = previousContent == null ? CREATED : previousContent;
        latest.fileBackups().putIfAbsent(absolutePath, backup);
        persist(sessionId);
    }

    public List<FileHistorySnapshot> listSnapshots(String sessionId) {
        ensureLoaded(sessionId);
        Deque<FileHistorySnapshot> deque = bySession.get(sessionId);
        if (deque == null || deque.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(deque);
    }

    public record RestorePlan(Map<String, String> writes, List<String> deletes) {
    }

    /**
     * Undo file edits from {@code turnId} onward — restore pre-edit content per path.
     */
    public RestorePlan computeRestore(String sessionId, String turnId) {
        ensureLoaded(sessionId);
        Deque<FileHistorySnapshot> deque = bySession.get(sessionId);
        if (deque == null || deque.isEmpty() || turnId == null) {
            return new RestorePlan(Map.of(), List.of());
        }

        List<FileHistorySnapshot> snapshots = new ArrayList<>(deque);
        int startIdx = -1;
        for (int i = 0; i < snapshots.size(); i++) {
            if (turnId.equals(snapshots.get(i).turnId())) {
                startIdx = i;
                break;
            }
        }
        if (startIdx < 0) {
            return new RestorePlan(Map.of(), List.of());
        }

        Map<String, String> restores = new LinkedHashMap<>();
        for (int i = startIdx; i < snapshots.size(); i++) {
            for (Map.Entry<String, String> entry : snapshots.get(i).fileBackups().entrySet()) {
                restores.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }

        Map<String, String> writes = new LinkedHashMap<>();
        List<String> deletes = new ArrayList<>();
        for (Map.Entry<String, String> entry : restores.entrySet()) {
            if (CREATED.equals(entry.getValue())) {
                deletes.add(entry.getKey());
            } else {
                writes.put(entry.getKey(), entry.getValue());
            }
        }
        return new RestorePlan(writes, deletes);
    }

    public void truncateAfter(String sessionId, String turnId) {
        ensureLoaded(sessionId);
        Deque<FileHistorySnapshot> deque = bySession.get(sessionId);
        if (deque == null || turnId == null) {
            return;
        }
        while (!deque.isEmpty() && !turnId.equals(deque.peekLast().turnId())) {
            deque.removeLast();
        }
        persist(sessionId);
    }

    public FileHistorySnapshot latestSnapshot(String sessionId) {
        ensureLoaded(sessionId);
        Deque<FileHistorySnapshot> deque = bySession.get(sessionId);
        if (deque == null || deque.isEmpty()) {
            return null;
        }
        return deque.peekLast();
    }

    public void clearSession(String sessionId) {
        if (sessionId != null) {
            bySession.remove(sessionId);
            loadedSessions.remove(sessionId);
            persistence.delete(sessionId);
        }
    }

    private void ensureLoaded(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        if (!loadedSessions.add(sessionId)) {
            return;
        }
        Deque<FileHistorySnapshot> loaded = persistence.load(sessionId);
        if (!loaded.isEmpty()) {
            bySession.put(sessionId, loaded);
        }
    }

    private void persist(String sessionId) {
        Deque<FileHistorySnapshot> deque = bySession.get(sessionId);
        persistence.save(sessionId, deque);
    }

    public record FileHistorySnapshot(
            String turnId,
            Map<String, String> fileBackups,
            long timestampMs) {
    }
}
