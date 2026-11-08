package com.agentdesktop.server.agent.tool.builtin;

import com.agentdesktop.server.agent.tool.ToolContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WriteFileToolTest {

    private final WriteFileTool tool = new WriteFileTool(new ObjectMapper());

    @TempDir
    Path workspace;

    @Test
    void writesNewFileToDisk() throws Exception {
        ToolContext context = new ToolContext(workspace.toString(), null);
        var result = tool.execute(
                "{\"path\":\"out.txt\",\"content\":\"hello agent\"}",
                context);

        assertTrue(result.ok());
        assertThat(Files.readString(workspace.resolve("out.txt"))).isEqualTo("hello agent");
    }

    @Test
    void overwritesExistingFile() throws Exception {
        Path file = workspace.resolve("a.txt");
        Files.writeString(file, "before");

        ToolContext context = new ToolContext(workspace.toString(), null);
        var result = tool.execute(
                "{\"path\":\"a.txt\",\"content\":\"after\"}",
                context);

        assertTrue(result.ok());
        assertThat(Files.readString(file)).isEqualTo("after");
    }
}
