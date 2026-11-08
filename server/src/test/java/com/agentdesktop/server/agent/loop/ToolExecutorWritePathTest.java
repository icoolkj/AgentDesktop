package com.agentdesktop.server.agent.loop;

import com.agentdesktop.server.agent.checkpoint.FileHistoryPersistence;
import com.agentdesktop.server.agent.checkpoint.FileHistoryService;
import com.agentdesktop.server.agent.checkpoint.ReadFileStateService;
import com.agentdesktop.server.agent.attachment.PdfSupport;
import com.agentdesktop.server.agent.settings.LlmRuntimeConfig;
import com.agentdesktop.server.agent.settings.LlmSettingsService;
import com.agentdesktop.server.agent.extension.HookRunner;
import com.agentdesktop.server.agent.llm.LlmToolCall;
import com.agentdesktop.server.agent.mcp.McpToolBridge;
import com.agentdesktop.server.agent.extension.AgentFeatureSettings;
import com.agentdesktop.server.agent.extension.ExtensionBundle;
import com.agentdesktop.server.agent.extension.SessionSkillState;
import com.agentdesktop.server.agent.permission.PermissionRuleSet;
import com.agentdesktop.server.agent.permission.PermissionService;
import com.agentdesktop.server.agent.tool.ToolContext;
import com.agentdesktop.server.agent.tool.ToolRegistry;
import com.agentdesktop.server.agent.tool.builtin.DeleteFileTool;
import com.agentdesktop.server.agent.tool.builtin.ReadFileTool;
import com.agentdesktop.server.agent.tool.builtin.WriteFileTool;
import com.agentdesktop.server.config.AgentLoopProperties;
import com.agentdesktop.server.ws.NdjsonEventPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolExecutorWritePathTest {

    @Mock
    private WebSocketSession webSocketSession;

    @Captor
    private ArgumentCaptor<TextMessage> outboundMessages;

    private ToolExecutor toolExecutor;
    private ObjectMapper objectMapper;

    @TempDir
    Path workspace;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        ReadFileStateService readFileStateService = new ReadFileStateService();
        LlmSettingsService llmSettingsService = mock(LlmSettingsService.class);
        lenient().when(llmSettingsService.getRuntimeConfig()).thenReturn(
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
        ReadFileTool readFileTool = new ReadFileTool(
                objectMapper, readFileStateService, new PdfSupport(), llmSettingsService);
        WriteFileTool writeFileTool = new WriteFileTool(objectMapper);
        DeleteFileTool deleteFileTool = new DeleteFileTool(objectMapper);
        McpToolBridge mcpBridge = mock(McpToolBridge.class);
        lenient().when(mcpBridge.definitions(any())).thenReturn(List.of());

        ToolRegistry registry = new ToolRegistry(
                List.of(readFileTool, writeFileTool, deleteFileTool),
                mcpBridge,
                objectMapper);

        PermissionService permissionService = new PermissionService() {
            @Override
            public boolean shouldAllowTool(
                    WebSocketSession session,
                    NdjsonEventPublisher events,
                    String permissionMode,
                    com.agentdesktop.server.agent.permission.PermissionRuleSet rules,
                    String toolName,
                    String summary,
                    String path) {
                return true;
            }
        };

        FileHistoryService fileHistoryService =
                new FileHistoryService(FileHistoryPersistence.forDirectory(objectMapper, Path.of("target/test-write-path")));
        AgentLoopProperties loopProperties = new AgentLoopProperties();
        HookRunner hookRunner = new HookRunner(objectMapper);
        ActiveAgentRun activeAgentRun = new ActiveAgentRun();

        toolExecutor = new ToolExecutor(
                registry,
                permissionService,
                fileHistoryService,
                readFileStateService,
                loopProperties,
                hookRunner,
                activeAgentRun,
                new SessionSkillState());

        lenient().when(webSocketSession.isOpen()).thenReturn(true);
    }

    @Test
    void writeFileEmitsFileChangedAfterDiskWrite() throws Exception {
        Path target = workspace.resolve("hello.txt");
        Files.writeString(target, "before");

        ToolContext context = toolContext("sess-write");
        AgentRunContext run = new AgentRunContext("ws-write");
        NdjsonEventPublisher events = new NdjsonEventPublisher(objectMapper);

        readThenWrite(context, run, events, target, "hello.txt", "after");

        assertThat(Files.readString(target)).isEqualTo("after");

        verify(webSocketSession, org.mockito.Mockito.atLeastOnce()).sendMessage(outboundMessages.capture());
        JsonNode fileChanged = findEvent(outboundMessages.getAllValues(), "file_changed");
        assertThat(fileChanged).isNotNull();
        assertThat(fileChanged.path("changeKind").asText()).isEqualTo("modified");
        assertThat(fileChanged.path("content").asText()).isEqualTo("after");
        assertThat(fileChanged.path("previousContent").asText()).isEqualTo("before");
    }

    @Test
    void writeFileRejectedWithoutPriorRead() throws Exception {
        Path target = workspace.resolve("hello.txt");
        Files.writeString(target, "before");

        ToolContext context = toolContext("sess-no-read");
        AgentRunContext run = new AgentRunContext("ws-no-read");
        NdjsonEventPublisher events = new NdjsonEventPublisher(objectMapper);

        toolExecutor.executeBatch(
                webSocketSession,
                events,
                List.of(new LlmToolCall(
                        "tc-write",
                        "write_file",
                        "{\"path\":\"hello.txt\",\"content\":\"after\"}")),
                context,
                run);

        assertThat(Files.readString(target)).isEqualTo("before");

        verify(webSocketSession, org.mockito.Mockito.atLeastOnce()).sendMessage(outboundMessages.capture());
        JsonNode toolResult = findEvent(outboundMessages.getAllValues(), "tool_result");
        assertThat(toolResult).isNotNull();
        assertThat(toolResult.path("ok").asBoolean()).isFalse();
        assertThat(toolResult.path("output").asText()).contains("not been read");
    }

    @Test
    void writeFileRejectedAfterExternalDiskChange() throws Exception {
        Path target = workspace.resolve("hello.txt");
        Files.writeString(target, "before");

        ToolContext context = toolContext("sess-stale");
        AgentRunContext run = new AgentRunContext("ws-stale");
        NdjsonEventPublisher events = new NdjsonEventPublisher(objectMapper);

        toolExecutor.executeBatch(
                webSocketSession,
                events,
                List.of(new LlmToolCall("tc-read", "read_file", "{\"path\":\"hello.txt\"}")),
                context,
                run);

        Files.writeString(target, "external change");

        toolExecutor.executeBatch(
                webSocketSession,
                events,
                List.of(new LlmToolCall(
                        "tc-write",
                        "write_file",
                        "{\"path\":\"hello.txt\",\"content\":\"after\"}")),
                context,
                run);

        assertThat(Files.readString(target)).isEqualTo("external change");

        verify(webSocketSession, org.mockito.Mockito.atLeastOnce()).sendMessage(outboundMessages.capture());
        JsonNode toolResult = findLastEvent(outboundMessages.getAllValues(), "tool_result");
        assertThat(toolResult.path("ok").asBoolean()).isFalse();
        assertThat(toolResult.path("output").asText()).contains("modified on disk");
    }

    @Test
    void deleteFileEmitsDeletedFileChanged() throws Exception {
        Path target = workspace.resolve("gone.txt");
        Files.writeString(target, "remove me");

        ToolContext context = toolContext("sess-delete");
        AgentRunContext run = new AgentRunContext("ws-delete");
        NdjsonEventPublisher events = new NdjsonEventPublisher(objectMapper);

        toolExecutor.executeBatch(
                webSocketSession,
                events,
                List.of(new LlmToolCall("tc-read", "read_file", "{\"path\":\"gone.txt\"}")),
                context,
                run);

        toolExecutor.executeBatch(
                webSocketSession,
                events,
                List.of(new LlmToolCall(
                        "tc-delete",
                        "delete_file",
                        "{\"path\":\"gone.txt\"}")),
                context,
                run);

        assertThat(Files.exists(target)).isFalse();

        verify(webSocketSession, org.mockito.Mockito.atLeastOnce()).sendMessage(outboundMessages.capture());
        JsonNode fileChanged = findEvent(outboundMessages.getAllValues(), "file_changed");
        assertThat(fileChanged).isNotNull();
        assertThat(fileChanged.path("changeKind").asText()).isEqualTo("deleted");
        assertThat(fileChanged.path("previousContent").asText()).isEqualTo("remove me");
        assertThat(fileChanged.has("content")).isFalse();
    }

    private void readThenWrite(
            ToolContext context,
            AgentRunContext run,
            NdjsonEventPublisher events,
            Path target,
            String relativePath,
            String content) {
        toolExecutor.executeBatch(
                webSocketSession,
                events,
                List.of(new LlmToolCall("tc-read", "read_file", "{\"path\":\"" + relativePath + "\"}")),
                context,
                run);
        toolExecutor.executeBatch(
                webSocketSession,
                events,
                List.of(new LlmToolCall(
                        "tc-write",
                        "write_file",
                        "{\"path\":\"" + relativePath + "\",\"content\":\"" + content + "\"}")),
                context,
                run);
    }

    private ToolContext toolContext(String sessionId) {
        return new ToolContext(
                workspace.toString(),
                null,
                java.util.Map.of(),
                "auto",
                null,
                PermissionRuleSet.EMPTY,
                sessionId,
                null,
                AgentFeatureSettings.defaults(),
                ExtensionBundle.empty(),
                0,
                null,
                List.of(),
                null,
                List.of());
    }

    private JsonNode findEvent(List<TextMessage> messages, String type) throws Exception {
        for (TextMessage message : messages) {
            for (String line : message.getPayload().split("\n")) {
                if (line.isBlank()) continue;
                JsonNode node = objectMapper.readTree(line);
                if (type.equals(node.path("type").asText())) {
                    return node;
                }
            }
        }
        return null;
    }

    private JsonNode findLastEvent(List<TextMessage> messages, String type) throws Exception {
        JsonNode last = null;
        for (TextMessage message : messages) {
            for (String line : message.getPayload().split("\n")) {
                if (line.isBlank()) continue;
                JsonNode node = objectMapper.readTree(line);
                if (type.equals(node.path("type").asText())) {
                    last = node;
                }
            }
        }
        return last;
    }
}
