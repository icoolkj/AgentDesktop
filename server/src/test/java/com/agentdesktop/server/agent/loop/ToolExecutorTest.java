package com.agentdesktop.server.agent.loop;

import com.agentdesktop.server.agent.llm.LlmToolCall;
import com.agentdesktop.server.agent.checkpoint.FileHistoryPersistence;
import com.agentdesktop.server.agent.checkpoint.FileHistoryService;
import com.agentdesktop.server.agent.checkpoint.ReadFileStateService;
import com.agentdesktop.server.agent.extension.HookRunner;
import com.agentdesktop.server.agent.extension.SessionSkillState;
import com.agentdesktop.server.agent.mcp.McpToolBridge;
import com.agentdesktop.server.agent.permission.PermissionService;
import com.agentdesktop.server.config.AgentLoopProperties;
import com.agentdesktop.server.agent.tool.Tool;
import com.agentdesktop.server.agent.tool.ToolContext;
import com.agentdesktop.server.agent.tool.ToolRegistry;
import com.agentdesktop.server.agent.tool.ToolResult;
import com.agentdesktop.server.agent.llm.LlmToolDefinition;
import com.agentdesktop.server.ws.NdjsonEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolExecutorTest {

    @Mock
    private WebSocketSession webSocketSession;

    private ToolExecutor toolExecutor;
    private RecordingTool readTool;
    private RecordingTool writeTool;

    @BeforeEach
    void setUp() {
        readTool = new RecordingTool("read_file", true);
        writeTool = new RecordingTool("write_file", false);
        McpToolBridge mcpBridge = mock(McpToolBridge.class);
        lenient().when(mcpBridge.definitions(any())).thenReturn(List.of());
        ToolRegistry registry = new ToolRegistry(List.of(readTool, writeTool), mcpBridge, new ObjectMapper());
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
        FileHistoryPersistence fileHistoryPersistence =
                FileHistoryPersistence.forDirectory(new ObjectMapper(), Path.of("target/test-file-history"));
        FileHistoryService fileHistoryService = new FileHistoryService(fileHistoryPersistence);
        ReadFileStateService readFileStateService = new ReadFileStateService();
        AgentLoopProperties loopProperties = new AgentLoopProperties();
        HookRunner hookRunner = new HookRunner(new ObjectMapper());
        ActiveAgentRun activeAgentRun = new ActiveAgentRun();
        toolExecutor = new ToolExecutor(
                registry, permissionService, fileHistoryService, readFileStateService,
                loopProperties, hookRunner, activeAgentRun, new SessionSkillState());
    }

    @Test
    void executesReadOnlyToolsInParallel() {
        readTool.delayMs = 80;
        AgentRunContext run = new AgentRunContext("ws-1");
        ToolContext context = new ToolContext("D:/project", null, Map.of(), "auto", null);
        NdjsonEventPublisher events = new NdjsonEventPublisher(new ObjectMapper());

        List<LlmToolCall> calls = List.of(
                new LlmToolCall("c1", "read_file", "{\"path\":\"a.txt\"}"),
                new LlmToolCall("c2", "read_file", "{\"path\":\"b.txt\"}"));

        long start = System.nanoTime();
        List<ToolExecutionResult> results = toolExecutor.executeBatch(
                webSocketSession, events, calls, context, run);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(results).hasSize(2);
        assertThat(readTool.maxConcurrent.get()).isGreaterThan(1);
        assertThat(elapsedMs).isLessThan(150);
    }

    @Test
    void runsWriteToolSeriallyAfterParallelReads() {
        readTool.delayMs = 30;
        writeTool.delayMs = 10;
        AgentRunContext run = new AgentRunContext("ws-1");
        ToolContext context = new ToolContext("D:/project", null, Map.of(), "auto", null);
        NdjsonEventPublisher events = new NdjsonEventPublisher(new ObjectMapper());

        List<LlmToolCall> calls = List.of(
                new LlmToolCall("c1", "read_file", "{\"path\":\"a.txt\"}"),
                new LlmToolCall("c2", "write_file", "{\"path\":\"out.txt\",\"content\":\"x\"}"),
                new LlmToolCall("c3", "read_file", "{\"path\":\"c.txt\"}"));

        List<ToolExecutionResult> results = toolExecutor.executeBatch(
                webSocketSession, events, calls, context, run);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).call().id()).isEqualTo("c1");
        assertThat(results.get(1).call().id()).isEqualTo("c2");
        assertThat(results.get(2).call().id()).isEqualTo("c3");
        assertThat(writeTool.maxConcurrent.get()).isEqualTo(1);
    }

    private static final class RecordingTool implements Tool {
        private final String toolName;
        private final boolean concurrent;
        private final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger maxConcurrent = new AtomicInteger();
        int delayMs;

        RecordingTool(String toolName, boolean concurrent) {
            this.toolName = toolName;
            this.concurrent = concurrent;
        }

        @Override
        public String name() {
            return toolName;
        }

        @Override
        public String description() {
            return toolName;
        }

        @Override
        public LlmToolDefinition definition() {
            return new LlmToolDefinition(toolName, description(), Map.of("type", "object"));
        }

        @Override
        public boolean concurrencySafe() {
            return concurrent;
        }

        @Override
        public ToolResult execute(String argumentsJson, ToolContext context) {
            int current = inFlight.incrementAndGet();
            maxConcurrent.updateAndGet(prev -> Math.max(prev, current));
            try {
                if (delayMs > 0) {
                    Thread.sleep(delayMs);
                }
                return ToolResult.success("ok");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ToolResult.failure("interrupted");
            } finally {
                inFlight.decrementAndGet();
            }
        }
    }
}
