package com.agentdesktop.server.agent.extension;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SkillFrontmatterParserTest {

    @Test
    void parsesPathsAndAllowedTools() {
        String frontmatter = """
                description: Demo
                paths: src/**, *.java
                allowed-tools:
                  - read_file
                  - grep
                context: fork
                agent: explore
                """;
        Map<String, String> map = SkillFrontmatterParser.parseFrontmatterMap(frontmatter);
        assertThat(SkillFrontmatterParser.parsePathPatterns(map)).containsExactly("src/**", "*.java");
        assertThat(SkillFrontmatterParser.parseAllowedTools(map)).containsExactly("read_file", "grep");
        assertThat(SkillFrontmatterParser.isForkContext(map)).isTrue();
        assertThat(SkillFrontmatterParser.parseAgentType(map)).isEqualTo("explore");
    }
}
