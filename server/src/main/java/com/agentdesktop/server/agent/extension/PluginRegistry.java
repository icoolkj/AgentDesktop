package com.agentdesktop.server.agent.extension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Discovers installed plugins from plugin directories ({@code plugin.json} manifest).
 */
@Component
public class PluginRegistry {

    private static final Logger log = LoggerFactory.getLogger(PluginRegistry.class);

    private final ObjectMapper objectMapper;

    public PluginRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<PluginManifest> loadFromDirs(List<Path> pluginRoots) {
        Map<String, PluginManifest> byName = new LinkedHashMap<>();
        for (Path root : pluginRoots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var stream = Files.list(root)) {
                stream.filter(Files::isDirectory).forEach(dir -> {
                    try {
                        PluginManifest manifest = parsePluginDir(dir);
                        if (manifest != null) {
                            byName.putIfAbsent(manifest.name(), manifest);
                        }
                    } catch (Exception e) {
                        log.debug("Skip plugin dir {}: {}", dir, e.getMessage());
                    }
                });
            } catch (Exception e) {
                log.debug("Cannot list plugins in {}: {}", root, e.getMessage());
            }
        }
        return List.copyOf(byName.values());
    }

    public record PluginManifest(
            String name,
            String version,
            String description,
            Path root,
            Path commandsDir,
            Path skillsDir,
            Path hooksFile) {
    }

    /** Parse a single installed plugin directory (contains plugin.json). */
    public PluginManifest parseInstalledDir(Path dir) {
        try {
            return parsePluginDir(dir);
        } catch (Exception e) {
            log.debug("Invalid plugin dir {}: {}", dir, e.getMessage());
            return null;
        }
    }

    private PluginManifest parsePluginDir(Path dir) throws Exception {
        Path manifestPath = dir.resolve("plugin.json");
        if (!Files.isRegularFile(manifestPath)) {
            manifestPath = dir.resolve(".claude-plugin").resolve("plugin.json");
        }
        if (!Files.isRegularFile(manifestPath)) {
            return null;
        }
        JsonNode root = objectMapper.readTree(Files.readString(manifestPath, StandardCharsets.UTF_8));
        String name = root.path("name").asText(dir.getFileName().toString());
        if (name.isBlank()) {
            return null;
        }
        String version = root.path("version").asText("0.0.0");
        String description = root.path("description").asText("");
        Path pluginRoot = dir;
        Path commands = resolvePath(pluginRoot, root.path("commands").asText("commands"));
        Path skills = resolvePath(pluginRoot, root.path("skills").asText("skills"));
        Path hooks = resolvePath(pluginRoot, root.path("hooks").asText("hooks.json"));
        return new PluginManifest(name, version, description, pluginRoot, commands, skills, hooks);
    }

    private static Path resolvePath(Path pluginRoot, String relative) {
        if (relative == null || relative.isBlank()) {
            return null;
        }
        Path path = pluginRoot.resolve(relative).normalize();
        return path.startsWith(pluginRoot) ? path : pluginRoot.resolve(relative);
    }

    public List<Path> collectCommandDirs(List<PluginManifest> plugins) {
        List<Path> dirs = new ArrayList<>();
        for (PluginManifest plugin : plugins) {
            if (plugin.commandsDir() != null && Files.isDirectory(plugin.commandsDir())) {
                dirs.add(plugin.commandsDir());
            }
        }
        return dirs;
    }

    public List<Path> collectSkillDirs(List<PluginManifest> plugins) {
        List<Path> dirs = new ArrayList<>();
        for (PluginManifest plugin : plugins) {
            if (plugin.skillsDir() != null && Files.isDirectory(plugin.skillsDir())) {
                dirs.add(plugin.skillsDir());
            }
        }
        return dirs;
    }

    public List<Path> collectHookFiles(List<PluginManifest> plugins) {
        List<Path> files = new ArrayList<>();
        for (PluginManifest plugin : plugins) {
            if (plugin.hooksFile() != null && Files.isRegularFile(plugin.hooksFile())) {
                files.add(plugin.hooksFile());
            }
        }
        return files;
    }
}
