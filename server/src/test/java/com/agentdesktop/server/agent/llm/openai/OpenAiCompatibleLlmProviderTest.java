package com.agentdesktop.server.agent.llm.openai;

import com.agentdesktop.server.agent.llm.LlmChatRequest;
import com.agentdesktop.server.agent.llm.LlmException;
import com.agentdesktop.server.agent.llm.LlmMessage;
import com.agentdesktop.server.agent.llm.LlmStreamEvent;
import com.agentdesktop.server.agent.settings.LlmRuntimeConfig;
import com.agentdesktop.server.agent.settings.LlmSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiCompatibleLlmProviderTest {

    @Mock
    private LlmSettingsService llmSettingsService;

    private MockWebServer mockWebServer;
    private OpenAiCompatibleLlmProvider provider;

    private LlmRuntimeConfig runtimeConfig;

    @BeforeEach
    void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        runtimeConfig = new LlmRuntimeConfig(
                true,
                "openai-compatible",
                mockWebServer.url("/v1").toString(),
                "test-key",
                "gpt-4o-mini",
                0.7,
                4096,
                5,
                false,
                false
        );

        lenient().when(llmSettingsService.getRuntimeConfig()).thenReturn(runtimeConfig);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        provider = new OpenAiCompatibleLlmProvider(llmSettingsService, new ObjectMapper(), httpClient);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    void isConfigured_falseWhenApiKeyMissing() {
        when(llmSettingsService.getRuntimeConfig()).thenReturn(new LlmRuntimeConfig(
                true, "openai-compatible", "http://localhost", "", "gpt-4o-mini", 0.7, 4096, 5, false, false));
        assertThat(provider.isConfigured()).isFalse();
    }

    @Test
    void isConfigured_trueWhenApiKeyPresent() {
        assertThat(provider.isConfigured()).isTrue();
        assertThat(provider.providerId()).isEqualTo("openai-compatible");
    }

    @Test
    void complete_parsesAssistantMessage() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                        {
                          "choices": [{
                            "message": { "role": "assistant", "content": "Hello there" },
                            "finish_reason": "stop"
                          }],
                          "usage": { "prompt_tokens": 3, "completion_tokens": 2, "total_tokens": 5 }
                        }
                        """)
                .addHeader("Content-Type", "application/json"));

        var response = provider.complete(LlmChatRequest.builder()
                .messages(List.of(LlmMessage.user("hi")))
                .build());

        assertThat(response.content()).isEqualTo("Hello there");
        assertThat(response.finishReason()).isEqualTo("stop");
        assertThat(response.usage().totalTokens()).isEqualTo(5);

        RecordedRequest request = mockWebServer.takeRequest(2, TimeUnit.SECONDS);
        assertThat(request.getPath()).isEqualTo("/v1/chat/completions");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-key");
        assertThat(request.getBody().readUtf8()).contains("\"stream\":false");
    }

    @Test
    void complete_throwsOnApiError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\":{\"message\":\"Invalid API key\"}}")
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> provider.complete(LlmChatRequest.builder()
                .messages(List.of(LlmMessage.user("hi")))
                .build()))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("Invalid API key");
    }

    @Test
    void streamComplete_emitsContentDeltasAndCompleted() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                        data: {"choices":[{"index":0,"delta":{"content":"Hel"}}]}

                        data: {"choices":[{"index":0,"delta":{"content":"lo"}}]}

                        data: {"choices":[{"index":0,"finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":2,"total_tokens":3}}

                        data: [DONE]

                        """)
                .addHeader("Content-Type", "text/event-stream"));

        List<LlmStreamEvent> events = new ArrayList<>();
        provider.streamComplete(
                LlmChatRequest.builder().messages(List.of(LlmMessage.user("hi"))).build(),
                events::add
        );

        assertThat(events.stream().filter(e -> e instanceof LlmStreamEvent.ContentDelta).count()).isEqualTo(2);
        assertThat(events.get(events.size() - 1)).isInstanceOf(LlmStreamEvent.Completed.class);

        LlmStreamEvent.Completed completed = (LlmStreamEvent.Completed) events.get(events.size() - 1);
        assertThat(completed.response().content()).isEqualTo("Hello");
        assertThat(completed.response().usage().totalTokens()).isEqualTo(3);

        RecordedRequest request = mockWebServer.takeRequest(2, TimeUnit.SECONDS);
        assertThat(request.getBody().readUtf8()).contains("\"stream\":true");
    }

    @Test
    void normalizeBaseUrl_trimsTrailingSlash() {
        assertThat(OpenAiCompatibleLlmProvider.normalizeBaseUrl("http://localhost:11434/v1/"))
                .isEqualTo("http://localhost:11434/v1");
    }
}
