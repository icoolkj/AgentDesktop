package com.agentdesktop.server.agent.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HookRunnerTest {

    private HookRunner hookRunner;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        hookRunner = new HookRunner(new ObjectMapper());
    }

    @Test
    void allowsWhenNoHooksConfigured() {
        HookOutcome outcome = hookRunner.runEvent(
                List.of(),
                HookEvents.PRE_TOOL_USE,
                HookContext.forTool(HookEvents.PRE_TOOL_USE, "write_file", "{}", null, true, "/ws", "s1"));
        assertThat(outcome.allowed()).isTrue();
    }

    @Test
    void skipsHooksForDifferentEvent() {
        List<HookDefinition> hooks = List.of(new HookDefinition(
                HookEvents.POST_TOOL_USE,
                ".*",
                List.of(new HookDefinition.HookAction("command", failingCommand(), 5))));
        HookOutcome outcome = hookRunner.runEvent(
                hooks,
                HookEvents.PRE_TOOL_USE,
                HookContext.forTool(HookEvents.PRE_TOOL_USE, "write_file", "{}", null, true, "/ws", "s1"));
        assertThat(outcome.allowed()).isTrue();
    }

    @Test
    void skipsHooksWhenMatcherDoesNotMatch() {
        List<HookDefinition> hooks = List.of(new HookDefinition(
                HookEvents.PRE_TOOL_USE,
                "write_file",
                List.of(new HookDefinition.HookAction("command", failingCommand(), 5))));
        HookOutcome outcome = hookRunner.runEvent(
                hooks,
                HookEvents.PRE_TOOL_USE,
                HookContext.forTool(HookEvents.PRE_TOOL_USE, "read_file", "{}", null, true, "/ws", "s1"));
        assertThat(outcome.allowed()).isTrue();
    }

    @Test
    void preToolUseBlocksWhenHookPrintsBlock() throws Exception {
        Path script = writeBlockScript();
        List<HookDefinition> hooks = List.of(new HookDefinition(
                HookEvents.PRE_TOOL_USE,
                "write_file",
                List.of(new HookDefinition.HookAction("command", scriptCommand(script), 10))));
        HookOutcome outcome = hookRunner.runEvent(
                hooks,
                HookEvents.PRE_TOOL_USE,
                HookContext.forTool(HookEvents.PRE_TOOL_USE, "write_file", "{}", null, true, tempDir.toString(), "s1"));
        assertThat(outcome.allowed()).isFalse();
        assertThat(outcome.reason()).contains("Blocked");
    }

    @Test
    void userPromptSubmitCanRewritePrompt() throws Exception {
        Path script = writeJsonScript("{\"updatedPrompt\":\"rewritten prompt\"}");
        List<HookDefinition> hooks = List.of(new HookDefinition(
                HookEvents.USER_PROMPT_SUBMIT,
                ".*",
                List.of(new HookDefinition.HookAction("command", scriptCommand(script), 10))));
        HookOutcome outcome = hookRunner.runEvent(
                hooks,
                HookEvents.USER_PROMPT_SUBMIT,
                HookContext.forUserPrompt("original", tempDir.toString(), "s1"));
        assertThat(outcome.allowed()).isTrue();
        assertThat(outcome.updatedUserPrompt()).isEqualTo("rewritten prompt");
    }

    @Test
    void postToolUseTimeoutIsNonBlocking() {
        List<HookDefinition> hooks = List.of(new HookDefinition(
                HookEvents.POST_TOOL_USE,
                ".*",
                List.of(new HookDefinition.HookAction("command", sleepCommand(), 1))));
        HookOutcome outcome = hookRunner.runEvent(
                hooks,
                HookEvents.POST_TOOL_USE,
                HookContext.forTool(
                        HookEvents.POST_TOOL_USE,
                        "read_file",
                        "{}",
                        "ok",
                        true,
                        tempDir.toString(),
                        "s1"));
        assertThat(outcome.allowed()).isTrue();
    }

    private Path writeBlockScript() throws Exception {
        return writeScript("block");
    }

    private Path writeJsonScript(String jsonLine) throws Exception {
        return writeScript(jsonLine);
    }

    private Path writeScript(String output) throws Exception {
        if (isWindows()) {
            Path script = tempDir.resolve("hook.cmd");
            Files.writeString(script, "@echo off\r\n echo " + output + "\r\n");
            return script;
        }
        Path script = tempDir.resolve("hook.sh");
        // 用单引号包裹输出，否则 shell 会吞掉 JSON 里的双引号，导致解析失败
        Files.writeString(script, "#!/bin/sh\nprintf '%s\\n' '" + output + "'\n");
        return script;
    }

    private static String scriptCommand(Path script) {
        if (isWindows()) {
            return "\"" + script.toAbsolutePath() + "\"";
        }
        return "sh \"" + script.toAbsolutePath() + "\"";
    }

    private static String failingCommand() {
        return isWindows() ? "cmd /c exit 1" : "sh -c 'exit 1'";
    }

    private static String sleepCommand() {
        return isWindows() ? "cmd /c timeout /t 30 /nobreak >nul" : "sleep 30";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
