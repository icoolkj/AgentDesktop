package com.agentdesktop.server.agent.extension;

import com.agentdesktop.server.agent.mcp.McpSkillBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.agentdesktop.server.persistence.GlobalHooksStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class ExtensionLoader {

    private static final Logger log = LoggerFactory.getLogger(ExtensionLoader.class);

    private final SlashCommandRegistry slashCommandRegistry;
    private final SkillCatalog skillCatalog;
    private final HookRegistry hookRegistry;
    private final FeatureSettingsLoader featureSettingsLoader;
    private final GlobalHooksStore globalHooksStore;
    private final PluginRegistry pluginRegistry;
    private final BundledSkillRegistry bundledSkillRegistry;
    private final McpSkillBridge mcpSkillBridge;

    private volatile String cachedWorkspaceRoot;
    private volatile ExtensionBundle cachedBundle;
    private volatile long cacheGeneration;
    private volatile long loadedGeneration;

    public ExtensionLoader(
            SlashCommandRegistry slashCommandRegistry,
            SkillCatalog skillCatalog,
            HookRegistry hookRegistry,
            FeatureSettingsLoader featureSettingsLoader,
            GlobalHooksStore globalHooksStore,
            PluginRegistry pluginRegistry,
            BundledSkillRegistry bundledSkillRegistry,
            McpSkillBridge mcpSkillBridge) {
        this.slashCommandRegistry = slashCommandRegistry;
        this.skillCatalog = skillCatalog;
        this.hookRegistry = hookRegistry;
        this.featureSettingsLoader = featureSettingsLoader;
        this.globalHooksStore = globalHooksStore;
        this.pluginRegistry = pluginRegistry;
        this.bundledSkillRegistry = bundledSkillRegistry;
        this.mcpSkillBridge = mcpSkillBridge;
    }

    public void invalidateCache() {
        cacheGeneration++;
        log.debug("Extension cache invalidated (generation={})", cacheGeneration);
    }

    public ExtensionBundle load(String workspaceRoot) {
        String normalized = workspaceRoot == null || workspaceRoot.isBlank() ? "" : workspaceRoot.trim();
        long generation = cacheGeneration;
        ExtensionBundle cached = cachedBundle;
        if (cached != null && loadedGeneration == generation && Objects.equals(cachedWorkspaceRoot, normalized)) {
            return cached;
        }
        synchronized (this) {
            if (cachedBundle != null
                    && loadedGeneration == cacheGeneration
                    && Objects.equals(cachedWorkspaceRoot, normalized)) {
                return cachedBundle;
            }
            ExtensionBundle loaded = loadUncached(normalized.isBlank() ? null : normalized);
            cachedWorkspaceRoot = normalized;
            cachedBundle = loaded;
            loadedGeneration = cacheGeneration;
            return loaded;
        }
    }

    private ExtensionBundle loadUncached(String workspaceRoot) {
        List<java.nio.file.Path> pluginRoots = ExtensionPaths.pluginRoots(workspaceRoot);
        List<PluginRegistry.PluginManifest> plugins = pluginRegistry.loadFromDirs(pluginRoots);

        List<java.nio.file.Path> commandDirs = new ArrayList<>(ExtensionPaths.commandDirs(workspaceRoot));
        commandDirs.addAll(pluginRegistry.collectCommandDirs(plugins));
        List<SlashCommandDefinition> commands = slashCommandRegistry.loadFromDirs(commandDirs);

        List<java.nio.file.Path> skillDirs = new ArrayList<>(ExtensionPaths.skillDirs(workspaceRoot));
        skillDirs.addAll(pluginRegistry.collectSkillDirs(plugins));
        List<SkillEntry> skills = mergeSkills(skillCatalog.loadFromDirs(skillDirs));

        List<HookDefinition> hooks = new ArrayList<>(globalHooksStore.loadHooks());
        hooks.addAll(hookRegistry.loadFromFiles(ExtensionPaths.hookConfigFiles(workspaceRoot)));
        for (java.nio.file.Path hookFile : pluginRegistry.collectHookFiles(plugins)) {
            hooks.addAll(hookRegistry.loadFromFiles(List.of(hookFile)));
        }

        return new ExtensionBundle(commands, skills, List.copyOf(hooks), List.copyOf(plugins));
    }

    private List<SkillEntry> mergeSkills(List<SkillEntry> diskSkills) {
        java.util.Map<String, SkillEntry> byName = new java.util.LinkedHashMap<>();
        for (SkillEntry bundled : bundledSkillRegistry.bundledSkills()) {
            byName.put(bundled.name(), bundled);
        }
        for (SkillEntry disk : diskSkills) {
            byName.put(disk.name(), disk);
        }
        for (SkillEntry mcp : mcpSkillBridge.listSkills()) {
            byName.putIfAbsent(mcp.name(), mcp);
        }
        return List.copyOf(byName.values());
    }

    public AgentFeatureSettings features() {
        return featureSettingsLoader.load();
    }
}
