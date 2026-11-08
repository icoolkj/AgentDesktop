package com.agentdesktop.server.agent.loop;

import com.agentdesktop.server.agent.llm.LlmMessage;
import com.agentdesktop.server.agent.llm.LlmToolCall;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolResultPairingTest {

    @Test
    void insertsMissingToolResults() {
        List<LlmMessage> messages = new ArrayList<>();
        messages.add(LlmMessage.assistant("", List.of(
                new LlmToolCall("id-1", "read_file", "{\"path\":\"a.txt\"}"),
                new LlmToolCall("id-2", "read_file", "{\"path\":\"b.txt\"}"))));
        messages.add(LlmMessage.tool("id-1", "read_file", "content a"));

        ToolResultPairing.repair(messages);

        assertThat(messages).hasSize(3);
        assertThat(messages.get(2).toolCallId()).isEqualTo("id-2");
        assertThat(messages.get(2).content()).contains("unavailable");
    }

    @Test
    void insertSyntheticErrorsOnCancel() {
        List<LlmMessage> messages = new ArrayList<>();
        messages.add(LlmMessage.assistant("", List.of(
                new LlmToolCall("id-1", "list_dir", "{}"))));

        ToolResultPairing.insertSyntheticErrors(messages, "Interrupted by user");

        assertThat(messages).hasSize(2);
        assertThat(messages.get(1).content()).isEqualTo("Interrupted by user");
    }
}
