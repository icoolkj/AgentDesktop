package com.agentdesktop.server.ws;

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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NdjsonEventPublisherTest {

    @Mock
    private WebSocketSession session;

    @Captor
    private ArgumentCaptor<TextMessage> messages;

    private NdjsonEventPublisher publisher;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        publisher = new NdjsonEventPublisher(objectMapper);
        lenient().when(session.isOpen()).thenReturn(true);
    }

    @Test
    void fileChangedReturnsTrueWhenSent() throws Exception {
        boolean ok = publisher.fileChanged(session, "/tmp/a.txt", "modified", "after", "before");
        assertThat(ok).isTrue();
        verify(session).sendMessage(messages.capture());
        JsonNode event = parseLine(messages.getValue().getPayload());
        assertThat(event.path("type").asText()).isEqualTo("file_changed");
        assertThat(event.path("path").asText()).isEqualTo("/tmp/a.txt");
    }

    @Test
    void fileChangedReturnsFalseWhenSessionClosed() {
        when(session.isOpen()).thenReturn(false);
        boolean ok = publisher.fileChanged(session, "/tmp/a.txt", "modified", "after", "before");
        assertThat(ok).isFalse();
    }

    @Test
    void rewindRestoredEmitsEvent() throws Exception {
        publisher.rewindRestored(session, "sess-1", List.of("/tmp/a.txt"));
        verify(session).sendMessage(messages.capture());
        JsonNode event = parseLine(messages.getValue().getPayload());
        assertThat(event.path("type").asText()).isEqualTo("rewind_restored");
        assertThat(event.path("sessionId").asText()).isEqualTo("sess-1");
        assertThat(event.path("paths").get(0).asText()).isEqualTo("/tmp/a.txt");
    }

    @Test
    void sendSkipsNullSession() throws Exception {
        publisher.send(null, java.util.Map.of("type", "error", "message", "x"));
        verify(session, never()).sendMessage(org.mockito.ArgumentMatchers.any());
    }

    private JsonNode parseLine(String payload) throws Exception {
        return objectMapper.readTree(payload.trim());
    }
}
