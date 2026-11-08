package com.agentdesktop.server.agent.chat;

import com.agentdesktop.server.agent.tool.ToolContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunModeTest {

    @Test
    void workspaceOpenUsesAgentLoopRegardlessOfMessage() {
        ToolContext ctx = new ToolContext("/proj", null, java.util.Map.of(), "auto", null);
        assertThat(AgentRunMode.resolve(ctx)).isEqualTo(AgentRunMode.Mode.AGENT_LOOP);
    }

    @Test
    void noWorkspaceUsesStreamOnly() {
        ToolContext ctx = new ToolContext(null, null, java.util.Map.of(), "ask", null);
        assertThat(AgentRunMode.resolve(ctx)).isEqualTo(AgentRunMode.Mode.STREAM_ONLY);
    }
}
