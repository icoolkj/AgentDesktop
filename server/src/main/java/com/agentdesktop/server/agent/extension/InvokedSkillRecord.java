package com.agentdesktop.server.agent.extension;

/** Skill body retained across compaction (CC invokedSkills parity). */
public record InvokedSkillRecord(
        String name,
        String skillPath,
        String content,
        long invokedAtEpochMs) {
}
