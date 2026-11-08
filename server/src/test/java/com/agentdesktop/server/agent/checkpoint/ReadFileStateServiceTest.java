package com.agentdesktop.server.agent.checkpoint;

import com.agentdesktop.server.agent.tool.ToolContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ReadFileStateServiceTest {

    private final ReadFileStateService service = new ReadFileStateService();

    @TempDir
    Path workspace;

    @Test
    void blocksWriteWhenFileNotRead() throws Exception {
        Path file = workspace.resolve("a.txt");
        Files.writeString(file, "hello");

        Optional<String> error = service.validateMutation(
                "sess-1",
                file,
                new ToolContext(workspace.toString(), null),
                ReadFileStateService.MutationKind.WRITE);

        assertThat(error).isPresent();
        assertThat(error.get()).contains("not been read");
    }

    @Test
    void allowsWriteAfterFullRead() throws Exception {
        Path file = workspace.resolve("a.txt");
        Files.writeString(file, "hello");

        service.recordRead("sess-1", file, "hello", 1, 1, 1, false);

        Optional<String> error = service.validateMutation(
                "sess-1",
                file,
                new ToolContext(workspace.toString(), null),
                ReadFileStateService.MutationKind.WRITE);

        assertThat(error).isEmpty();
    }

    @Test
    void blocksWriteAfterExternalDiskChange() throws Exception {
        Path file = workspace.resolve("a.txt");
        Files.writeString(file, "before");

        service.recordRead("sess-1", file, "before", 1, 1, 1, false);
        Thread.sleep(5);
        Files.writeString(file, "after external edit");

        Optional<String> error = service.validateMutation(
                "sess-1",
                file,
                new ToolContext(workspace.toString(), null),
                ReadFileStateService.MutationKind.WRITE);

        assertThat(error).isPresent();
        assertThat(error.get()).contains("modified on disk");
    }

    @Test
    void blocksWriteWhenEditorBufferDiffersFromRead() throws Exception {
        Path file = workspace.resolve("a.txt");
        Files.writeString(file, "disk");

        service.recordRead("sess-1", file, "disk", 1, 1, 1, false);

        ToolContext context = new ToolContext(
                workspace.toString(),
                null,
                Map.of(file.toAbsolutePath().normalize().toString(), "unsaved"));

        Optional<String> error = service.validateMutation(
                "sess-1",
                file,
                context,
                ReadFileStateService.MutationKind.WRITE);

        assertThat(error).isPresent();
        assertThat(error.get()).contains("unsaved editor changes");
    }

    @Test
    void blocksWriteAfterPartialRead() throws Exception {
        Path file = workspace.resolve("big.txt");
        Files.writeString(file, "line1\nline2\nline3\n");

        service.recordRead("sess-1", file, Files.readString(file), 1, 1, 3, false);

        Optional<String> error = service.validateMutation(
                "sess-1",
                file,
                new ToolContext(workspace.toString(), null),
                ReadFileStateService.MutationKind.WRITE);

        assertThat(error).isPresent();
        assertThat(error.get()).contains("partially read");
    }

    @Test
    void allowsNewFileWriteWithoutPriorRead() throws Exception {
        Path file = workspace.resolve("new.txt");

        Optional<String> error = service.validateMutation(
                "sess-1",
                file,
                new ToolContext(workspace.toString(), null),
                ReadFileStateService.MutationKind.WRITE);

        assertThat(error).isEmpty();
    }
}
