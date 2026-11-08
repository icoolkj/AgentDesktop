package com.agentdesktop.server.agent.tool;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RipgrepRunnerTest {

    @Test
    void runFindsMatchWhenRipgrepAvailable() throws Exception {
        if (!RipgrepRunner.isAvailable()) {
            return;
        }

        Path dir = Files.createTempDirectory("grep-test");
        Path file = dir.resolve("sample.txt");
        Files.writeString(file, "hello agent desktop\nsecond line\n");

        RipgrepRunner.RipgrepResult result = RipgrepRunner.run(
                List.of("-n", "agent", file.toString()),
                dir);

        assertThat(result.ok()).isTrue();
        assertThat(result.lines()).anyMatch(line -> line.contains("agent"));
    }

    @Test
    void resolveRgCommandDoesNotThrow() {
        RipgrepRunner.resolveRgCommand();
    }
}
