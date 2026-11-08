package com.agentdesktop.server.persistence;

import com.agentdesktop.server.agent.extension.ExtensionPaths;
import com.agentdesktop.server.agent.extension.HookDefinition;
import com.agentdesktop.server.agent.extension.HookRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.util.List;

@Component
public class GlobalHooksStore {

    private static final Logger log = LoggerFactory.getLogger(GlobalHooksStore.class);
    public static final String MYSQL_LABEL = "MySQL: agent_desktop.agent_config.hooks_global";

    private final AgentConfigCacheService configCache;
    private final HookRegistry hookRegistry;

    public GlobalHooksStore(AgentConfigCacheService configCache, HookRegistry hookRegistry) {
        this.configCache = configCache;
        this.hookRegistry = hookRegistry;
    }

    public List<HookDefinition> loadHooks() {
        return configCache.load(AgentConfigKeys.HOOKS_GLOBAL, StoredHooks.class)
                .map(stored -> stored.hooks() == null ? List.<HookDefinition>of() : stored.hooks())
                .orElseGet(this::loadLegacyOrEmpty);
    }

    public void saveHooks(List<HookDefinition> hooks) {
        configCache.save(AgentConfigKeys.HOOKS_GLOBAL, new StoredHooks(hooks == null ? List.of() : hooks));
    }

    private List<HookDefinition> loadLegacyOrEmpty() {
        var path = ExtensionPaths.userRoot().resolve("hooks.json");
        if (!Files.isRegularFile(path)) {
            return List.of();
        }
        List<HookDefinition> fromFile = hookRegistry.loadFromFiles(List.of(path));
        if (!fromFile.isEmpty()) {
            log.info("Migrating global hooks from {} to MySQL", path);
            saveHooks(fromFile);
        }
        return fromFile;
    }

    /** JSON-serializable wrapper for hooks list. */
    public record StoredHooks(List<HookDefinition> hooks) {
    }
}
