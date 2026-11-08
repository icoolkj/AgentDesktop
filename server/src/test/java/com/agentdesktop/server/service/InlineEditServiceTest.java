package com.agentdesktop.server.service;

import com.agentdesktop.server.agent.llm.LlmMessage;
import com.agentdesktop.server.dto.InlineEditParams;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InlineEditServiceTest {

    @Test
    void normalizeReplacement_stripsMarkdownFence() {
        assertThat(InlineEditService.normalizeReplacement("```java\nreturn 1;\n```"))
                .isEqualTo("return 1;");
    }

    @Test
    void normalizeReplacement_preservesPlainCode() {
        assertThat(InlineEditService.normalizeReplacement("  const x = 1;\n"))
                .isEqualTo("const x = 1;");
    }

    @Test
    void buildMessages_includesSelectionAndInstruction() {
        InlineEditParams params = new InlineEditParams();
        params.setRelativePath("src/App.java");
        params.setLanguage("java");
        params.setSelectionText("old code");
        params.setStartLine(10);
        params.setStartColumn(4);
        params.setEndLine(12);
        params.setEndColumn(1);

        List<LlmMessage> messages = InlineEditService.buildMessages(params, "refactor this");

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).role().name()).isEqualTo("SYSTEM");
        assertThat(messages.get(1).role().name()).isEqualTo("USER");
        assertThat(messages.get(1).content()).contains("src/App.java");
        assertThat(messages.get(1).content()).contains("old code");
        assertThat(messages.get(1).content()).contains("refactor this");
    }
}
