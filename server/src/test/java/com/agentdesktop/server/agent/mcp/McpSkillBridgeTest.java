package com.agentdesktop.server.agent.mcp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpSkillBridgeTest {

    @Test
    void inferSkillNameFromResourceUri() {
        assertThat(McpSkillBridge.inferSkillName("file:///skills/commit/SKILL.md", ""))
                .isEqualTo("commit");
        assertThat(McpSkillBridge.inferSkillName("", "plugins/demo/skills/review/SKILL.md"))
                .isEqualTo("review");
    }
}
