package com.agentdesktop.server.web;

import com.agentdesktop.server.agent.extension.ExtensionLoader;
import com.agentdesktop.server.agent.extension.PluginInstallService;
import com.agentdesktop.server.agent.extension.PluginRegistry;
import com.agentdesktop.server.agent.extension.SkillEntry;
import com.agentdesktop.server.agent.extension.SlashCommandDefinition;
import com.agentdesktop.server.dto.PluginInstallRequestDto;
import com.agentdesktop.server.dto.PluginInstallResultDto;
import com.agentdesktop.server.persistence.AgentConfigCacheService;
import com.agentdesktop.server.persistence.McpConfigStore;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/extensions")
public class ExtensionController {

    private final ExtensionLoader extensionLoader;
    private final PluginInstallService pluginInstallService;

    public ExtensionController(ExtensionLoader extensionLoader, PluginInstallService pluginInstallService) {
        this.extensionLoader = extensionLoader;
        this.pluginInstallService = pluginInstallService;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String workspaceRoot) {
        var bundle = extensionLoader.load(workspaceRoot);
        var features = extensionLoader.features();
        List<Map<String, String>> commands = bundle.slashCommands().stream()
                .map(this::commandToMap)
                .toList();
        List<Map<String, String>> plugins = bundle.plugins().stream()
                .map(this::pluginToMap)
                .toList();
        List<Map<String, Object>> skills = bundle.skills() == null
                ? List.of()
                : bundle.skills().stream().map(this::skillToMap).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("slashCommands", commands);
        result.put("plugins", plugins);
        result.put("skills", skills);
        result.put("skillsLoaded", !skills.isEmpty());
        result.put("hookCount", bundle.hooks() == null ? 0 : bundle.hooks().size());
        result.put("features", Map.of(
                "subagentsEnabled", features.subagentsEnabled(),
                "webFetchEnabled", features.webFetchEnabled(),
                "webSearchEnabled", features.webSearchEnabled(),
                "bashSandboxEnabled", features.bashSandboxEnabled(),
                "bashSandboxMode", features.bashSandboxMode()));
        result.put("pluginDirs", Map.of(
                "user", pluginInstallService.userPluginsDir().toString(),
                "workspace", workspaceRoot == null || workspaceRoot.isBlank()
                        ? null
                        : pluginInstallService.workspacePluginsDir(workspaceRoot).toString()));
        result.put("persistence", Map.of(
                "features", AgentConfigCacheService.MYSQL_LABEL,
                "mcp", McpConfigStore.MYSQL_LABEL,
                "skills", "~/.agent-desktop/skills, skills-agent-desktop, workspace .agent-desktop/skills, plugins/*/skills",
                "slashCommands", "workspace files + plugins/*/commands",
                "plugins", "filesystem (~/.agent-desktop/plugins)"));
        return result;
    }

    @PostMapping("/plugins/install")
    public PluginInstallResultDto installPlugin(@RequestBody PluginInstallRequestDto body) {
        PluginInstallResultDto result = pluginInstallService.install(body);
        if (!result.isOk()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, result.getMessage());
        }
        return result;
    }

    @DeleteMapping("/plugins")
    public PluginInstallResultDto uninstallPlugin(
            @RequestParam String name,
            @RequestParam(defaultValue = "user") String scope,
            @RequestParam(required = false) String workspaceRoot) {
        try {
            PluginInstallResultDto result = pluginInstallService.uninstall(name, scope, workspaceRoot);
            if (!result.isOk()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, result.getMessage());
            }
            return result;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private Map<String, Object> skillToMap(SkillEntry skill) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", skill.name());
        map.put("description", skill.description());
        map.put("source", skill.source());
        map.put("userInvocable", skill.userInvocable());
        map.put("modelInvocable", skill.modelInvocable());
        map.put("conditional", skill.conditional());
        map.put("forkContext", skill.forkContext());
        map.put("allowedTools", skill.allowedTools());
        return map;
    }

    private Map<String, String> commandToMap(SlashCommandDefinition command) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("name", command.name());
        map.put("description", command.description());
        map.put("source", command.sourcePath());
        return map;
    }

    private Map<String, String> pluginToMap(PluginRegistry.PluginManifest plugin) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("name", plugin.name());
        map.put("version", plugin.version());
        map.put("description", plugin.description());
        map.put("root", plugin.root().toString());
        return map;
    }
}
