package com.agentdesktop.server.agent.loop;

import com.agentdesktop.server.agent.llm.LlmResponse;
import com.agentdesktop.server.agent.llm.LlmToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCallResolverTest {

    private final ToolCallResolver resolver = new ToolCallResolver();

    @Test
    void usesApiToolCallsOnly() {
        LlmResponse response = new LlmResponse(
                "text",
                List.of(new LlmToolCall("c1", "read_file", "{\"path\":\"pom.xml\"}")),
                "tool_calls",
                null);

        assertThat(resolver.resolve(response)).hasSize(1);
        assertThat(resolver.hasUnparsedToolMarkup(response)).isFalse();
    }

    @Test
    void ignoresDsmlInContentWithoutApiToolCalls() {
        LlmResponse response = new LlmResponse(
                """
                <|DSML|invoke name="list_dir">
                <|DSML|parameter name="path">.</|DSML|parameter>
                </|DSML|invoke>
                """,
                List.of(),
                "stop",
                null);

        assertThat(resolver.resolve(response)).isEmpty();
        assertThat(resolver.hasUnparsedToolMarkup(response)).isTrue();
    }

    @Test
    void reportsUnparsedWhenMarkupNotExecutable() {
        LlmResponse response = new LlmResponse(
                "TOOL_CALLSE incomplete internal markup",
                List.of(),
                "stop",
                null);

        assertThat(resolver.resolve(response)).isEmpty();
        assertThat(resolver.hasUnparsedToolMarkup(response)).isTrue();
    }

    @Test
    void plainTextAnswerHasNoUnparsedMarkup() {
        LlmResponse response = new LlmResponse(
                "## Summary\n\nDone.",
                List.of(),
                "stop",
                null);

        assertThat(resolver.resolve(response)).isEmpty();
        assertThat(resolver.hasUnparsedToolMarkup(response)).isFalse();
    }
}
