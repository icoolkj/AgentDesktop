package com.agentdesktop.server.agent.permission;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionRuleSetTest {

    @Test
    void denyBeforeAllow() {
        PermissionRuleSet rules = PermissionRuleSet.builder()
                .allow("write_file")
                .deny("write_file(secrets/**)")
                .build();
        assertThat(rules.evaluate("write_file", "secrets/a.txt")).isEqualTo(PermissionBehavior.DENY);
        assertThat(rules.evaluate("write_file", "src/App.java")).isEqualTo(PermissionBehavior.ALLOW);
    }
}
