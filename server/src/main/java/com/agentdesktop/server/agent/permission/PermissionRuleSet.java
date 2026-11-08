package com.agentdesktop.server.agent.permission;

import java.util.ArrayList;
import java.util.List;

/**
 * Merged permission rules — Claude Code {@code permissionsLoader.ts} parity (deny → ask → allow).
 */
public record PermissionRuleSet(List<PermissionRule> deny, List<PermissionRule> ask, List<PermissionRule> allow) {

    public static final PermissionRuleSet EMPTY = new PermissionRuleSet(List.of(), List.of(), List.of());

    /**
     * @return first matching rule behavior, or {@code null} if no rule matches
     */
    public PermissionBehavior evaluate(String toolName, String relativePath) {
        for (PermissionRule rule : deny) {
            if (rule.matches(toolName, relativePath)) {
                return PermissionBehavior.DENY;
            }
        }
        for (PermissionRule rule : ask) {
            if (rule.matches(toolName, relativePath)) {
                return PermissionBehavior.ASK;
            }
        }
        for (PermissionRule rule : allow) {
            if (rule.matches(toolName, relativePath)) {
                return PermissionBehavior.ALLOW;
            }
        }
        return null;
    }

    public static PermissionRuleSet merge(PermissionRuleSet base, PermissionRuleSet overlay) {
        if (base == null || base.isEmpty()) {
            return overlay == null ? EMPTY : overlay;
        }
        if (overlay == null || overlay.isEmpty()) {
            return base;
        }
        return new PermissionRuleSet(
                concat(base.deny(), overlay.deny()),
                concat(base.ask(), overlay.ask()),
                concat(base.allow(), overlay.allow()));
    }

    private static List<PermissionRule> concat(List<PermissionRule> first, List<PermissionRule> second) {
        List<PermissionRule> merged = new ArrayList<>(first.size() + second.size());
        merged.addAll(first);
        merged.addAll(second);
        return List.copyOf(merged);
    }

    public boolean isEmpty() {
        return deny.isEmpty() && ask.isEmpty() && allow.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<PermissionRule> deny = new ArrayList<>();
        private final List<PermissionRule> ask = new ArrayList<>();
        private final List<PermissionRule> allow = new ArrayList<>();

        public Builder deny(String rule) {
            deny.add(PermissionRule.parse(rule, PermissionBehavior.DENY));
            return this;
        }

        public Builder ask(String rule) {
            ask.add(PermissionRule.parse(rule, PermissionBehavior.ASK));
            return this;
        }

        public Builder allow(String rule) {
            allow.add(PermissionRule.parse(rule, PermissionBehavior.ALLOW));
            return this;
        }

        public PermissionRuleSet build() {
            return new PermissionRuleSet(List.copyOf(deny), List.copyOf(ask), List.copyOf(allow));
        }
    }
}
