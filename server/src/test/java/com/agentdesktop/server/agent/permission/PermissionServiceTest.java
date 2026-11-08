package com.agentdesktop.server.agent.permission;

import com.agentdesktop.server.ws.NdjsonEventPublisher;
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private WebSocketSession session;

    @Captor
    private ArgumentCaptor<TextMessage> outbound;

    private PermissionService permissionService;
    private NdjsonEventPublisher events;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        permissionService = new PermissionService();
        objectMapper = new ObjectMapper();
        events = new NdjsonEventPublisher(objectMapper);
        lenient().when(session.isOpen()).thenReturn(true);
    }

    @Test
    void autoModeAllowsWriteWithoutPrompt() {
        boolean allowed = permissionService.shouldAllowTool(
                session,
                events,
                "auto",
                PermissionRuleSet.EMPTY,
                "write_file",
                "write_file → a.txt",
                "a.txt");
        assertThat(allowed).isTrue();
    }

    @Test
    void planModeBlocksWriteTools() {
        boolean allowed = permissionService.shouldAllowTool(
                session,
                events,
                "plan",
                PermissionRuleSet.EMPTY,
                "write_file",
                "write_file → a.txt",
                "a.txt");
        assertThat(allowed).isFalse();
    }

    @Test
    void denyRuleBlocksBeforeModeCheck() {
        PermissionRuleSet rules = PermissionRuleSet.builder()
                .deny("write_file(secrets/**)")
                .build();
        boolean allowed = permissionService.shouldAllowTool(
                session,
                events,
                "auto",
                rules,
                "write_file",
                "write_file → secrets/x.txt",
                "secrets/x.txt");
        assertThat(allowed).isFalse();
    }

    @Test
    void askModeWaitsForReply() throws Exception {
        AtomicReference<String> requestId = new AtomicReference<>();
        doAnswer(invocation -> {
            String payload = ((TextMessage) invocation.getArgument(0)).getPayload().trim();
            JsonNode node = objectMapper.readTree(payload);
            requestId.set(node.path("requestId").asText());
            permissionService.reply(requestId.get(), true);
            return null;
        }).when(session).sendMessage(any());

        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() ->
                permissionService.shouldAllowTool(
                        session,
                        events,
                        "ask",
                        PermissionRuleSet.EMPTY,
                        "write_file",
                        "write_file → a.txt",
                        "a.txt"));

        assertThat(future.get(5, TimeUnit.SECONDS)).isTrue();
        assertThat(requestId.get()).isNotBlank();
    }

    @Test
    void identifiesWriteAndBashTools() {
        assertThat(permissionService.isWriteTool("write_file")).isTrue();
        assertThat(permissionService.isWriteTool("search_replace")).isTrue();
        assertThat(permissionService.isWriteTool("delete_file")).isTrue();
        assertThat(permissionService.isBashTool("run_terminal_cmd")).isTrue();
        assertThat(permissionService.isWriteTool("read_file")).isFalse();
    }
}
