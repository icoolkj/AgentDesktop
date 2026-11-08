package com.agentdesktop.server.agent.permission;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionRuleTest {

    @Test
    void matchesToolNameOnly() {
        PermissionRule rule = PermissionRule.parse("write_file", PermissionBehavior.DENY);
        assertThat(rule.matches("write_file", "src/App.java")).isTrue();
        assertThat(rule.matches("read_file", "src/App.java")).isFalse();
    }

    @Test
    void matchesPathGlob() {
        PermissionRule rule = PermissionRule.parse("write_file(secrets/**)", PermissionBehavior.DENY);
        assertThat(rule.matches("write_file", "secrets/key.txt")).isTrue();
        assertThat(rule.matches("write_file", "src/main/App.java")).isFalse();
    }
}
