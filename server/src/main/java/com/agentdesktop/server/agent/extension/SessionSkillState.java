package com.agentdesktop.server.agent.extension;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-session skill runtime state: invoked bodies (compact retention), activated conditional skills,
 * and active allowed-tools filter from the latest skill invocation.
 */
@Component
public class SessionSkillState {

    private static final int MAX_INVOKED_SKILLS = 12;

    private final ConcurrentHashMap<String, SessionData> bySession = new ConcurrentHashMap<>();

    public void recordInvoked(String sessionId, String name, String skillPath, String content) {
        if (sessionId == null || sessionId.isBlank() || name == null || name.isBlank()) {
            return;
        }
        SessionData data = bySession.computeIfAbsent(sessionId, ignored -> new SessionData());
        synchronized (data) {
            if (data.invoked.size() >= MAX_INVOKED_SKILLS) {
                String oldest = data.invoked.keySet().iterator().next();
                data.invoked.remove(oldest);
            }
            data.invoked.put(name, new InvokedSkillRecord(name, skillPath, content, System.currentTimeMillis()));
        }
    }

    public List<InvokedSkillRecord> invokedSkills(String sessionId) {
        SessionData data = bySession.get(sessionId);
        if (data == null) {
            return List.of();
        }
        synchronized (data) {
            return List.copyOf(data.invoked.values());
        }
    }

    public void activateConditional(String sessionId, String skillName) {
        if (sessionId == null || sessionId.isBlank() || skillName == null || skillName.isBlank()) {
            return;
        }
        SessionData data = bySession.computeIfAbsent(sessionId, ignored -> new SessionData());
        synchronized (data) {
            data.activatedConditional.add(skillName.toLowerCase());
        }
    }

    public Set<String> activatedConditionalNames(String sessionId) {
        SessionData data = bySession.get(sessionId);
        if (data == null) {
            return Set.of();
        }
        synchronized (data) {
            return Set.copyOf(data.activatedConditional);
        }
    }

    public void setActiveAllowedTools(String sessionId, List<String> allowedTools) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        SessionData data = bySession.computeIfAbsent(sessionId, ignored -> new SessionData());
        synchronized (data) {
            data.activeAllowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
        }
    }

    public List<String> activeAllowedTools(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        SessionData data = bySession.get(sessionId);
        if (data == null) {
            return List.of();
        }
        synchronized (data) {
            return data.activeAllowedTools;
        }
    }

    public void clearSession(String sessionId) {
        if (sessionId != null) {
            bySession.remove(sessionId);
        }
    }

    public List<SkillEntry> listingSkills(String sessionId, List<SkillEntry> allSkills) {
        Set<String> activated = activatedConditionalNames(sessionId);
        return allSkills.stream()
                .filter(skill -> skill.modelInvocable()
                        && (!skill.conditional() || activated.contains(skill.name())))
                .toList();
    }

    private static final class SessionData {
        private final Map<String, InvokedSkillRecord> invoked = new LinkedHashMap<>();
        private final Set<String> activatedConditional = new LinkedHashSet<>();
        private List<String> activeAllowedTools = List.of();
    }
}
