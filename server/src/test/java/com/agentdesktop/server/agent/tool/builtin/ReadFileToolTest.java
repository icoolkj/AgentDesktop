package com.agentdesktop.server.agent.tool.builtin;

import com.agentdesktop.server.agent.attachment.PdfSupport;
import com.agentdesktop.server.agent.checkpoint.ReadFileStateService;
import com.agentdesktop.server.agent.settings.LlmRuntimeConfig;
import com.agentdesktop.server.agent.settings.LlmSettingsService;
import com.agentdesktop.server.agent.tool.ToolContext;
import com.agentdesktop.server.agent.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReadFileToolTest {

    private final ReadFileStateService readFileStateService = new ReadFileStateService();
    private final PdfSupport pdfSupport = new PdfSupport();
    private final LlmSettingsService llmSettingsService = mock(LlmSettingsService.class);
    private ReadFileTool tool;

    @TempDir
    Path workspace;

    @BeforeEach
    void setUp() {
        when(llmSettingsService.getRuntimeConfig()).thenReturn(
                new LlmRuntimeConfig(
                        true,
                        "openai-compatible",
                        "https://api.openai.com/v1",
                        "test-key",
                        "gpt-4o",
                        0.2,
                        4096,
                        120,
                        true,
                        true));
        tool = new ReadFileTool(new ObjectMapper(), readFileStateService, pdfSupport, llmSettingsService);
    }

    @Test
    void readsFromUnsavedEditorBufferWhenSnapshotPresent() throws Exception {
        Path file = workspace.resolve("src/App.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "disk-only\n");

        ToolContext context = new ToolContext(
                workspace.toString(),
                null,
                Map.of(file.toAbsolutePath().normalize().toString(), "buffer-line-1\nbuffer-line-2\n"));

        ToolResult result = tool.execute("{\"path\":\"src/App.java\"}", context);

        assertTrue(result.ok());
        assertTrue(result.output().contains("(reading unsaved editor buffer)"));
        assertTrue(result.output().contains("buffer-line-1"));
        assertTrue(!result.output().contains("disk-only"));
    }
}
