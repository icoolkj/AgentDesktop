package com.agentdesktop.server.agent.checkpoint;

import com.agentdesktop.server.agent.tool.ToolContext;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-scoped read snapshots for write/edit/delete staleness checks.
 * Exceeds Claude Code {@code readFileState}: also blocks when the IDE buffer diverges from last read.
 */
@Component
public class ReadFileStateService {

    public enum MutationKind {
        WRITE,
        EDIT,
        DELETE
    }

    public record FileReadSnapshot(
            String normalizedContent,
            long mtimeMs,
            int offset,
            int limit,
            int totalLines,
            boolean partialView,
            boolean fromEditorBuffer) {
    }

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, FileReadSnapshot>> bySession =
            new ConcurrentHashMap<>();

    public void recordRead(
            String sessionId,
            Path absolutePath,
            String content,
            int offset,
            int limit,
            int totalLines,
            boolean fromEditorBuffer) {
        if (absolutePath == null) {
            return;
        }
        String normalized = normalize(content == null ? "" : content);
        int safeOffset = Math.max(1, offset);
        int safeLimit = Math.max(1, limit);
        int endLine = Math.min(safeOffset - 1 + safeLimit, totalLines);
        boolean partial = safeOffset > 1 || endLine < totalLines;
        long mtimeMs = readMtimeMs(absolutePath);
        FileReadSnapshot snapshot = new FileReadSnapshot(
                normalized,
                mtimeMs,
                safeOffset,
                safeLimit,
                totalLines,
                partial,
                fromEditorBuffer);
        bySession.computeIfAbsent(sessionKey(sessionId), ignored -> new ConcurrentHashMap<>())
                .put(pathKey(absolutePath), snapshot);
    }

    /**
     * @return error message when mutation must be blocked; empty when allowed
     */
    public Optional<String> validateMutation(
            String sessionId,
            Path absolutePath,
            ToolContext context,
            MutationKind kind) {
        if (absolutePath == null) {
            return Optional.empty();
        }
        if (!Files.exists(absolutePath)) {
            return Optional.empty();
        }
        if (!Files.isRegularFile(absolutePath)) {
            return Optional.empty();
        }

        ConcurrentHashMap<String, FileReadSnapshot> session = bySession.get(sessionKey(sessionId));
        FileReadSnapshot snapshot = session == null ? null : session.get(pathKey(absolutePath));
        if (snapshot == null) {
            return Optional.of(
                    "File has not been read yet. Read it first with read_file before modifying it.");
        }

        if (snapshot.partialView() && kind != MutationKind.DELETE) {
            return Optional.of(
                    "File was only partially read (offset/limit). Read the full file with read_file before modifying it.");
        }

        String buffer = context == null ? null : context.openDocumentContent(absolutePath);
        if (buffer != null && !normalize(buffer).equals(snapshot.normalizedContent())) {
            return Optional.of(
                    "File has unsaved editor changes since the last read_file. Save or revert, then read_file again before modifying.");
        }

        long currentMtime = readMtimeMs(absolutePath);
        if (currentMtime > snapshot.mtimeMs()) {
            try {
                String diskContent = normalize(Files.readString(absolutePath, StandardCharsets.UTF_8));
                if (snapshot.partialView() || !diskContent.equals(snapshot.normalizedContent())) {
                    return Optional.of(
                            "File has been modified on disk since read (by user, linter, or external tool). "
                                    + "Use read_file again before modifying it.");
                }
            } catch (Exception e) {
                return Optional.of(
                        "File changed on disk since read and could not be verified. Use read_file again before modifying.");
            }
        }

        return Optional.empty();
    }

    public void recordMutation(String sessionId, Path absolutePath) {
        if (absolutePath == null || !Files.isRegularFile(absolutePath)) {
            return;
        }
        try {
            String content = Files.readString(absolutePath, StandardCharsets.UTF_8);
            ListHolder lines = lineCount(content);
            recordRead(sessionId, absolutePath, content, 1, lines.count, lines.count, false);
        } catch (Exception ignored) {
            removePath(sessionId, absolutePath);
        }
    }

    public void recordDelete(String sessionId, Path absolutePath) {
        removePath(sessionId, absolutePath);
    }

    public void clearSession(String sessionId) {
        bySession.remove(sessionKey(sessionId));
    }

    private void removePath(String sessionId, Path absolutePath) {
        ConcurrentHashMap<String, FileReadSnapshot> session = bySession.get(sessionKey(sessionId));
        if (session != null) {
            session.remove(pathKey(absolutePath));
        }
    }

    private static String sessionKey(String sessionId) {
        return sessionId == null || sessionId.isBlank() ? "__default__" : sessionId.trim();
    }

    private static String pathKey(Path absolutePath) {
        return absolutePath.toAbsolutePath().normalize().toString().toLowerCase();
    }

    private static long readMtimeMs(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception e) {
            return 0L;
        }
    }

    static String normalize(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static ListHolder lineCount(String content) {
        if (content == null || content.isEmpty()) {
            return new ListHolder(0);
        }
        return new ListHolder(normalize(content).split("\n", -1).length);
    }

    private record ListHolder(int count) {}
}
