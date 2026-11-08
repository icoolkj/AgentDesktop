package com.agentdesktop.server.agent.loop;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantContentFilterTest {

    @Test
    void sanitizeForUser_removesDsmlBlocks() {
        String raw = """
                你好
                <|DSML|tool_calls>
                <|DSML|invoke name="read_file">
                <|DSML|parameter name="path">pom.xml</|DSML|parameter>
                </|DSML|invoke>
                </|DSML|tool_calls>
                ## 架构
                """;
        String clean = AssistantContentFilter.sanitizeForUser(raw);
        assertThat(clean).contains("你好").contains("架构");
        assertThat(clean.toUpperCase()).doesNotContain("DSML");
    }

    @Test
    void isUserVisible_falseForDsmlOnly() {
        String raw = "<|DSML|invoke name=\"list_dir\">";
        assertThat(AssistantContentFilter.isUserVisible(raw)).isFalse();
    }

    @Test
    void sanitize_stripsMixedTextAndIncompleteDsml() {
        String raw = """
                让我再看看 yshop-server 的主启动类：
                <|DSML|tool_callse="list_dir string="truee
                """;
        String clean = AssistantContentFilter.sanitizeForUser(raw);
        assertThat(clean).isEqualTo("让我再看看 yshop-server 的主启动类：");
        assertThat(AssistantContentFilter.isCleanUserText(clean)).isTrue();
    }
}
