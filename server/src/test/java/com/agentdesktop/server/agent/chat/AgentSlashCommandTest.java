package com.agentdesktop.server.agent.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSlashCommandTest {

    @Test
    void parsesCompactAndContext() {
        assertThat(AgentSlashCommand.parse("/compact")).isInstanceOf(AgentSlashCommand.Compact.class);
        assertThat(AgentSlashCommand.parse("/compact keep tests")).isInstanceOf(AgentSlashCommand.Compact.class);
        assertThat(AgentSlashCommand.parse("/context")).isInstanceOf(AgentSlashCommand.Context.class);
        assertThat(AgentSlashCommand.parse("hello")).isInstanceOf(AgentSlashCommand.None.class);
    }
}
