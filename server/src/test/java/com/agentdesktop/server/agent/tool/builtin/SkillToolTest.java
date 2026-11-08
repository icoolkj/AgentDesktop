package com.agentdesktop.server.agent.tool.builtin;

import com.agentdesktop.server.agent.extension.ExtensionBundle;
import com.agentdesktop.server.agent.extension.AgentFeatureSettings;
import com.agentdesktop.server.agent.extension.SkillContentLoader;
import com.agentdesktop.server.agent.extension.SkillEntry;
import com.agentdesktop.server.agent.extension.SessionSkillState;
import com.agentdesktop.server.agent.permission.PermissionRuleSet;
import com.agentdesktop.server.agent.tool.ToolContext;
import com.agentdesktop.server.agent.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillToolTest {

    @Test
    void loadsSkillBodyIntoFollowUpUserText(@TempDir Path skillDir) throws Exception {
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                description: Demo
                ---
                Follow these steps: $ARGUMENTS
                """);

        SkillEntry entry = new SkillEntry(
                "demo",
                "Demo",
                null,
                skillDir,
                skillDir.resolve("SKILL.md"),
                "project",
                true,
                false,
                List.of(),
                List.of(),
                false,
                null);
        ExtensionBundle bundle = new ExtensionBundle(List.of(), List.of(entry), List.of(), List.of());
        ToolContext context = new ToolContext(
                "/tmp/ws",
                null,
                Map.of(),
                "ask",
                null,
                PermissionRuleSet.EMPTY,
                "session-1",
                null,
                AgentFeatureSettings.defaults(),
                bundle,
                0,
                null,
                List.of(),
                null,
                List.of());

        SessionSkillState sessionSkillState = new SessionSkillState();
        SubagentRunner subagentRunner = mock(SubagentRunner.class);
        SkillTool tool = new SkillTool(new ObjectMapper(), new SkillContentLoader(null), sessionSkillState, subagentRunner);
        ToolResult result = tool.execute("{\"skill\":\"demo\",\"args\":\"fix tests\"}", context);

        assertThat(result.ok()).isTrue();
        assertThat(result.followUpUserText()).contains("<system-reminder>");
        assertThat(result.followUpUserText()).contains("fix tests");
        assertThat(sessionSkillState.invokedSkills("session-1")).hasSize(1);
    }
}
