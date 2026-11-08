package com.agentdesktop.server.agent.loop;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentRunRegistryTest {

    @Test
    void cancelMarksActiveRun() {
        AgentRunRegistry registry = new AgentRunRegistry();
        AgentRunContext run = registry.begin("ws-1");

        assertThat(run.isCancelled()).isFalse();
        registry.cancel("ws-1");
        assertThat(run.isCancelled()).isTrue();
        assertThatThrownBy(run::checkNotCancelled).isInstanceOf(AgentLoopCancelledException.class);

        registry.end("ws-1");
        registry.cancel("ws-1");
        assertThat(run.isCancelled()).isTrue();
    }
}
