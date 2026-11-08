package com.agentdesktop.server.persistence;

import com.agentdesktop.server.agent.permission.PermissionRuleSet;
import com.agentdesktop.server.domain.PermissionRuleRow;
import com.agentdesktop.server.mapper.PermissionRuleMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PermissionRuleStore {

    private static final Logger log = LoggerFactory.getLogger(PermissionRuleStore.class);
    public static final String SCOPE_GLOBAL = "global";
    public static final String SCOPE_WORKSPACE = "workspace";

    private final ObjectMapper objectMapper;
    private final PermissionRuleMapper permissionRuleMapper;

    public PermissionRuleStore(ObjectMapper objectMapper, PermissionRuleMapper permissionRuleMapper) {
        this.objectMapper = objectMapper;
        this.permissionRuleMapper = permissionRuleMapper;
    }

    public PermissionRuleSet loadGlobal() {
        return loadScoped(SCOPE_GLOBAL, null);
    }

    public PermissionRuleSet loadWorkspace(String workspaceRoot) {
        if (workspaceRoot == null || workspaceRoot.isBlank()) {
            return PermissionRuleSet.EMPTY;
        }
        return loadScoped(SCOPE_WORKSPACE, workspaceRoot);
    }

    public void saveGlobal(PermissionRuleSet rules) {
        saveScoped(SCOPE_GLOBAL, null, rules);
    }

    public void saveWorkspace(String workspaceRoot, PermissionRuleSet rules) {
        if (workspaceRoot == null || workspaceRoot.isBlank()) {
            return;
        }
        saveScoped(SCOPE_WORKSPACE, workspaceRoot, rules);
    }

    private PermissionRuleSet loadScoped(String scope, String scopeKey) {
        if (permissionRuleMapper == null) {
            return PermissionRuleSet.EMPTY;
        }
        try {
            String key = scopeKey == null ? "" : scopeKey;
            List<PermissionRuleRow> rows = permissionRuleMapper.findByScope(scope, key.isBlank() ? "" : key);
            if (rows == null || rows.isEmpty()) {
                return PermissionRuleSet.EMPTY;
            }
            PermissionRuleRow row = rows.getFirst();
            if (row.getRuleJson() == null || row.getRuleJson().isBlank()) {
                return PermissionRuleSet.EMPTY;
            }
            return parseRules(objectMapper.readTree(row.getRuleJson()));
        } catch (Exception e) {
            log.warn("Failed to load permission rules scope={} key={}: {}", scope, scopeKey, e.getMessage());
            return PermissionRuleSet.EMPTY;
        }
    }

    private void saveScoped(String scope, String scopeKey, PermissionRuleSet rules) {
        if (permissionRuleMapper == null) {
            return;
        }
        try {
            PermissionRuleRow row = new PermissionRuleRow();
            row.setScope(scope);
            row.setScopeKey(scopeKey == null ? "" : scopeKey);
            row.setRuleJson(objectMapper.writeValueAsString(toJsonMap(rules)));
            permissionRuleMapper.upsert(row);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save permission rules", e);
        }
    }

    static PermissionRuleSet parseRules(JsonNode permissions) {
        PermissionRuleSet.Builder builder = PermissionRuleSet.builder();
        appendRules(builder, com.agentdesktop.server.agent.permission.PermissionBehavior.DENY, permissions.path("deny"));
        appendRules(builder, com.agentdesktop.server.agent.permission.PermissionBehavior.ASK, permissions.path("ask"));
        appendRules(builder, com.agentdesktop.server.agent.permission.PermissionBehavior.ALLOW, permissions.path("allow"));
        return builder.build();
    }

    private static void appendRules(
            PermissionRuleSet.Builder builder,
            com.agentdesktop.server.agent.permission.PermissionBehavior behavior,
            JsonNode array) {
        if (!array.isArray()) {
            return;
        }
        for (JsonNode node : array) {
            if (!node.isTextual()) {
                continue;
            }
            try {
                switch (behavior) {
                    case DENY -> builder.deny(node.asText());
                    case ASK -> builder.ask(node.asText());
                    case ALLOW -> builder.allow(node.asText());
                }
            } catch (IllegalArgumentException ignored) {
                // skip invalid rule
            }
        }
    }

    private Map<String, Object> toJsonMap(PermissionRuleSet rules) {
        return Map.of(
                "deny", ruleTexts(rules.deny()),
                "ask", ruleTexts(rules.ask()),
                "allow", ruleTexts(rules.allow()));
    }

    private static java.util.List<String> ruleTexts(java.util.List<com.agentdesktop.server.agent.permission.PermissionRule> rules) {
        return rules.stream().map(PermissionRuleStore::formatRule).toList();
    }

    private static String formatRule(com.agentdesktop.server.agent.permission.PermissionRule rule) {
        if (rule.pathGlob() == null || rule.pathGlob().isBlank()) {
            return rule.toolName();
        }
        return rule.toolName() + "(" + rule.pathGlob() + ")";
    }
}
