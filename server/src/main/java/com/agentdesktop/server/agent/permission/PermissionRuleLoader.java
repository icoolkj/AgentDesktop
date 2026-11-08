package com.agentdesktop.server.agent.permission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agentdesktop.server.persistence.PermissionRuleStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads workspace permission rules — Claude Code {@code permissionsLoader.ts} project settings parity.
 * <p>
 * Search order: {@code .agent-desktop/permissions.json} then {@code .claude/settings.json}.
 */
@Component
public class PermissionRuleLoader {

    private static final Logger log = LoggerFactory.getLogger(PermissionRuleLoader.class);
    private static final int MAX_BYTES = 32_768;

    private final ObjectMapper objectMapper;
    private final PermissionRuleStore permissionRuleStore;

    public PermissionRuleLoader(ObjectMapper objectMapper, PermissionRuleStore permissionRuleStore) {
        this.objectMapper = objectMapper;
        this.permissionRuleStore = permissionRuleStore;
    }

    public PermissionRuleSet load(String workspaceRoot) {
        PermissionRuleSet globalDb = permissionRuleStore.loadGlobal();
        if (workspaceRoot == null || workspaceRoot.isBlank()) {
            return globalDb;
        }
        Path root = Path.of(workspaceRoot);
        PermissionRuleSet workspaceFile = loadFromFiles(root);
        PermissionRuleSet workspaceDb = permissionRuleStore.loadWorkspace(workspaceRoot);
        PermissionRuleSet workspaceRules = !workspaceFile.isEmpty() ? workspaceFile : workspaceDb;
        return PermissionRuleSet.merge(globalDb, workspaceRules);
    }

    private PermissionRuleSet loadFromFiles(Path root) {
        for (Path candidate : List.of(
                root.resolve(".agent-desktop").resolve("permissions.json"),
                root.resolve(".claude").resolve("settings.json"))) {
            PermissionRuleSet parsed = tryLoad(candidate);
            if (parsed != null && !parsed.isEmpty()) {
                log.debug("Loaded permission rules from {}", candidate);
                return parsed;
            }
        }
        return PermissionRuleSet.EMPTY;
    }

    private PermissionRuleSet tryLoad(Path file) {
        try {
            if (!Files.isRegularFile(file)) {
                return null;
            }
            if (Files.size(file) > MAX_BYTES) {
                log.warn("Permission rules file too large, skipping: {}", file);
                return null;
            }
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(json);
            JsonNode permissions = root.path("permissions");
            if (permissions.isMissingNode() || permissions.isNull()) {
                permissions = root;
            }
            return parseRules(permissions);
        } catch (Exception e) {
            log.debug("Could not load permission rules from {}: {}", file, e.getMessage());
            return null;
        }
    }

    private static PermissionRuleSet parseRules(JsonNode permissions) {
        PermissionRuleSet.Builder builder = PermissionRuleSet.builder();
        appendRules(builder, PermissionBehavior.DENY, permissions.path("deny"));
        appendRules(builder, PermissionBehavior.ASK, permissions.path("ask"));
        appendRules(builder, PermissionBehavior.ALLOW, permissions.path("allow"));
        return builder.build();
    }

    private static void appendRules(
            PermissionRuleSet.Builder builder,
            PermissionBehavior behavior,
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
            } catch (IllegalArgumentException e) {
                log.warn("Skipping invalid permission rule: {}", node.asText());
            }
        }
    }
}
