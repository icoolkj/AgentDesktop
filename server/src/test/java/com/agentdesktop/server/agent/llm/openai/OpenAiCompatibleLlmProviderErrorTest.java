package com.agentdesktop.server.agent.llm.openai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleLlmProviderErrorTest {

    @Test
    void friendlyMessageForEmptyCpaAssistantResponse() {
        String body = """
                {"id":"msg_cpa","type":"message","role":"assistant","model":"deepseek-v4-pro",\
                "content":[],"stop_reason":"end_turn","usage":{"input_tokens":0,"output_tokens":0}}\
                """;
        String msg = OpenAiCompatibleLlmProvider.extractErrorMessage(body);
        assertThat(msg).contains("deepseek-v4-pro");
        assertThat(msg).contains("Payload");
    }
}
