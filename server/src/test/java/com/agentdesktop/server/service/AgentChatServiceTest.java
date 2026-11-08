package com.agentdesktop.server.service;

import com.agentdesktop.server.agent.extension.ExtensionBundle;
import com.agentdesktop.server.agent.extension.ExtensionLoader;
import com.agentdesktop.server.agent.extension.HookRunner;
import com.agentdesktop.server.agent.extension.SessionSkillState;
import com.agentdesktop.server.agent.extension.SlashCommandRegistry;
import com.agentdesktop.server.agent.loop.AgentLoopService;
import com.agentdesktop.server.agent.loop.AgentRunContext;
import com.agentdesktop.server.agent.loop.AgentRunRegistry;
import com.agentdesktop.server.agent.tool.ToolContext;
import com.agentdesktop.server.dto.ChatSendParams;
import com.agentdesktop.server.dto.LlmHistoryMessageDto;
import com.agentdesktop.server.transcript.ServerTranscriptLoader;
import com.agentdesktop.server.agent.permission.PermissionRuleLoader;
import com.agentdesktop.server.agent.checkpoint.FileHistoryPersistence;
import com.agentdesktop.server.agent.checkpoint.FileHistoryService;
import com.agentdesktop.server.agent.settings.LlmRuntimeConfig;
import com.agentdesktop.server.agent.settings.LlmSettingsService;
import com.agentdesktop.server.config.AgentLoopProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agentdesktop.server.ws.NdjsonEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentChatServiceTest {

    @Mock
    private AgentLoopService agentLoopService;

    @Mock
    private WebSocketSession webSocketSession;

    @Mock
    private ServerTranscriptLoader transcriptLoader;

    @Mock
    private ExtensionLoader extensionLoader;

    @Mock
    private SlashCommandRegistry slashCommandRegistry;

    @Mock
    private LlmSettingsService llmSettingsService;

    private AgentChatService agentChatService;
    private AgentRunRegistry runRegistry;
    private ExecutorService agentExecutor;

    @BeforeEach
    void setUp() {
        runRegistry = new AgentRunRegistry();
        agentExecutor = Executors.newSingleThreadExecutor();
        agentChatService = new AgentChatService(
                agentLoopService,
                runRegistry,
                new NdjsonEventPublisher(new ObjectMapper()),
                transcriptLoader,
                new PermissionRuleLoader(
                        new ObjectMapper(),
                        new com.agentdesktop.server.persistence.PermissionRuleStore(new ObjectMapper(), null)),
                new FileHistoryService(FileHistoryPersistence.forDirectory(new ObjectMapper(), Path.of("target/test-file-history-chat"))),
                new AgentLoopProperties(),
                extensionLoader,
                slashCommandRegistry,
                new HookRunner(new ObjectMapper()),
                llmSettingsService,
                new SessionSkillState(),
                agentExecutor
        );
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
        when(extensionLoader.load(any())).thenReturn(ExtensionBundle.empty());
        when(extensionLoader.features()).thenReturn(com.agentdesktop.server.agent.extension.AgentFeatureSettings.defaults());
        when(slashCommandRegistry.resolve(anyString(), any(), any())).thenReturn(com.agentdesktop.server.agent.extension.SlashCommandResult.None.INSTANCE);
        when(transcriptLoader.loadHistory(any(), any())).thenReturn(List.of());
        when(webSocketSession.isOpen()).thenReturn(true);
        when(webSocketSession.getId()).thenReturn("ws-1");
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        agentExecutor.shutdownNow();
    }

    @Test
    void handleChatSend_streamsLlmReply() throws Exception {
        when(agentLoopService.streamOnce(eq(webSocketSession), any(), any(AgentRunContext.class)))
                .thenReturn("Hello from LLM");

        ChatSendParams params = new ChatSendParams();
        params.setMessage("hi");
        params.setSessionId("session-1");

        agentChatService.handleChatSend(webSocketSession, params).get(5, TimeUnit.SECONDS);

        verify(agentLoopService).streamOnce(eq(webSocketSession), any(), any(AgentRunContext.class));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession, org.mockito.Mockito.atLeastOnce()).sendMessage(captor.capture());
        List<String> payloads = captor.getAllValues().stream().map(TextMessage::getPayload).toList();
        assertThat(payloads.stream().anyMatch(p -> p.contains("\"type\":\"done\""))).isTrue();
    }

    @Test
    void handleChatSend_usesClientHistoryWithTools() throws Exception {
        when(agentLoopService.run(eq(webSocketSession), any(), any(ToolContext.class), any(AgentRunContext.class)))
                .thenReturn("Done");

        LlmHistoryMessageDto tool = new LlmHistoryMessageDto();
        tool.setRole("tool");
        tool.setToolCallId("call-1");
        tool.setToolName("list_dir");
        tool.setContent("entries");

        ChatSendParams params = new ChatSendParams();
        params.setMessage("帮我修改 pom.xml");
        params.setSessionId("session-1");
        params.setWorkspaceRoot("D:/project");
        params.setHistory(List.of(tool));

        agentChatService.handleChatSend(webSocketSession, params).get(5, TimeUnit.SECONDS);

        verify(agentLoopService).run(eq(webSocketSession), any(), any(ToolContext.class), any(AgentRunContext.class));
    }
}
