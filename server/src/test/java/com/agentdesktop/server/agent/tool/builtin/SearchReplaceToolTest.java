package com.agentdesktop.server.agent.tool.builtin;

import com.agentdesktop.server.agent.tool.ToolContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchReplaceToolTest {

    private final SearchReplaceTool tool = new SearchReplaceTool(new ObjectMapper());

    @TempDir
    Path workspace;

    @Test
    void replacesUniqueOccurrence() throws Exception {
        Path file = workspace.resolve("a.txt");
        Files.writeString(file, "hello world\n");

        ToolContext context = new ToolContext(workspace.toString(), null);
        var result = tool.execute(
                "{\"path\":\"a.txt\",\"old_string\":\"world\",\"new_string\":\"agent\"}",
                context);

        assertTrue(result.ok());
        assertTrue(Files.readString(file).contains("hello agent"));
    }

    @Test
    void usesEditorBufferWhenPresent() throws Exception {
        Path file = workspace.resolve("b.txt");
        Files.writeString(file, "disk\n");

        ToolContext context = new ToolContext(
                workspace.toString(),
                null,
                java.util.Map.of(file.toAbsolutePath().normalize().toString(), "buffer\n"));

        var result = tool.execute(
                "{\"path\":\"b.txt\",\"old_string\":\"buffer\",\"new_string\":\"edited\"}",
                context);

        assertTrue(result.ok());
        assertTrue(Files.readString(file).contains("edited"));
    }
}
