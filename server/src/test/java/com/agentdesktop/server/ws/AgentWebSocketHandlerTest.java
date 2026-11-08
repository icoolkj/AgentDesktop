package com.agentdesktop.server.ws;

import com.agentdesktop.server.service.AgentChatService;
import com.agentdesktop.server.service.InlineEditService;
import com.agentdesktop.server.service.RewindService;
import com.agentdesktop.server.agent.permission.PermissionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentWebSocketHandlerTest {

    private static final class TestableHandler extends AgentWebSocketHandler {
        TestableHandler(
                AgentChatService agentChatService,
                InlineEditService inlineEditService,
                PermissionService permissionService,
                RewindService rewindService,
                NdjsonEventPublisher events,
                ObjectMapper objectMapper) {
            super(agentChatService, inlineEditService, permissionService, rewindService, events, objectMapper);
        }

        void dispatch(WebSocketSession session, TextMessage message) throws Exception {
            handleTextMessage(session, message);
        }
    }

    @Mock
    private AgentChatService agentChatService;

    @Mock
    private InlineEditService inlineEditService;

    @Mock
    private PermissionService permissionService;

    @Mock
    private RewindService rewindService;

    @Mock
    private NdjsonEventPublisher events;

    @Mock
    private WebSocketSession session;

    @Captor
    private ArgumentCaptor<TextMessage> responses;

    private TestableHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new TestableHandler(
                agentChatService,
                inlineEditService,
                permissionService,
                rewindService,
                events,
                objectMapper);
    }

    @Test
    void pingReturnsPong() throws Exception {
        when(session.isOpen()).thenReturn(true);
        handler.dispatch(
                session,
                new TextMessage("""
                        {"jsonrpc":"2.0","method":"ping","id":1}
                        """));

        verify(session).sendMessage(responses.capture());
        JsonNode body = objectMapper.readTree(responses.getValue().getPayload());
        assertThat(body.path("result").path("pong").asBoolean()).isTrue();
        assertThat(body.path("id").asInt()).isEqualTo(1);
    }

    @Test
    void checkpointRestoreEmitsRewindRestored() throws Exception {
        when(session.isOpen()).thenReturn(true);
        when(rewindService.restoreFiles(eq("sess-1"), eq("turn-1")))
                .thenReturn(new RewindService.RestoreResult(1, 0, List.of("C:/a.txt")));

        handler.dispatch(
                session,
                new TextMessage("""
                        {"jsonrpc":"2.0","method":"checkpoint.restore","id":2,
                         "params":{"sessionId":"sess-1","turnId":"turn-1"}}
                        """));

        verify(events).rewindRestored(session, "sess-1", List.of("C:/a.txt"));
        verify(session).sendMessage(responses.capture());
        JsonNode body = objectMapper.readTree(responses.getValue().getPayload());
        assertThat(body.path("result").path("ok").asBoolean()).isTrue();
    }
}
