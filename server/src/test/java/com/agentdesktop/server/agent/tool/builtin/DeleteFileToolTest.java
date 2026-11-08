package com.agentdesktop.server.agent.tool.builtin;

import com.agentdesktop.server.agent.tool.ToolContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeleteFileToolTest {

    private final DeleteFileTool tool = new DeleteFileTool(new ObjectMapper());

    @TempDir
    Path workspace;

    @Test
    void deletesRegularFile() throws Exception {
        Path file = workspace.resolve("remove-me.txt");
        Files.writeString(file, "bye");

        ToolContext context = new ToolContext(workspace.toString(), null);
        var result = tool.execute("{\"path\":\"remove-me.txt\"}", context);

        assertTrue(result.ok());
        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void failsWhenFileMissing() {
        ToolContext context = new ToolContext(workspace.toString(), null);
        var result = tool.execute("{\"path\":\"missing.txt\"}", context);

        assertFalse(result.ok());
    }
}
