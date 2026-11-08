package com.agentdesktop.server.agent.extension;

import com.agentdesktop.server.dto.PluginInstallRequestDto;
import com.agentdesktop.server.dto.PluginInstallResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Install Claude Code-compatible plugins into user or workspace plugin directories.
 */
@Service
public class PluginInstallService {

    private static final Logger log = LoggerFactory.getLogger(PluginInstallService.class);

    private final PluginRegistry pluginRegistry;

    public PluginInstallService(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    public PluginInstallResultDto install(PluginInstallRequestDto request) {
        if (request == null || request.getSourcePath() == null || request.getSourcePath().isBlank()) {
            return PluginInstallResultDto.failure("sourcePath is required");
        }

        Path source;
        try {
            source = Path.of(request.getSourcePath().trim()).toAbsolutePath().normalize();
        } catch (Exception e) {
            return PluginInstallResultDto.failure("Invalid sourcePath: " + e.getMessage());
        }

        if (!Files.isDirectory(source)) {
            return PluginInstallResultDto.failure("sourcePath must be an existing plugin directory");
        }

        PluginRegistry.PluginManifest manifest = pluginRegistry.parseInstalledDir(source);
        if (manifest == null) {
            return PluginInstallResultDto.failure(
                    "Plugin directory must contain plugin.json (or .claude-plugin/plugin.json)");
        }

        Path pluginsRoot;
        try {
            pluginsRoot = resolvePluginsRoot(request.getScope(), request.getWorkspaceRoot());
        } catch (IllegalArgumentException e) {
            return PluginInstallResultDto.failure(e.getMessage());
        }
        try {
            Files.createDirectories(pluginsRoot);
        } catch (IOException e) {
            return PluginInstallResultDto.failure("Cannot create plugins directory: " + e.getMessage());
        }

        Path target = pluginsRoot.resolve(sanitizeDirName(manifest.name())).normalize();
        if (!target.startsWith(pluginsRoot)) {
            return PluginInstallResultDto.failure("Invalid plugin name");
        }

        try {
            if (Files.exists(target)) {
                deleteRecursive(target);
            }
            copyDirectory(source, target);
            log.info("Installed plugin {} -> {}", manifest.name(), target);
            return PluginInstallResultDto.success(
                    manifest.name(),
                    manifest.version(),
                    target.toString());
        } catch (Exception e) {
            log.warn("Plugin install failed", e);
            return PluginInstallResultDto.failure(e.getMessage() == null ? "Install failed" : e.getMessage());
        }
    }

    public PluginInstallResultDto uninstall(String pluginName, String scope, String workspaceRoot) {
        if (pluginName == null || pluginName.isBlank()) {
            return PluginInstallResultDto.failure("plugin name is required");
        }
        Path pluginsRoot = resolvePluginsRoot(scope, workspaceRoot);
        Path target = pluginsRoot.resolve(sanitizeDirName(pluginName)).normalize();
        if (!target.startsWith(pluginsRoot)) {
            return PluginInstallResultDto.failure("Invalid plugin name");
        }
        if (!Files.isDirectory(target)) {
            return PluginInstallResultDto.failure("Plugin not installed: " + pluginName);
        }
        try {
            deleteRecursive(target);
            return PluginInstallResultDto.success(pluginName, null, null);
        } catch (IOException e) {
            return PluginInstallResultDto.failure(e.getMessage());
        }
    }

    public Path userPluginsDir() {
        return ExtensionPaths.userPluginsDir();
    }

    public Path workspacePluginsDir(String workspaceRoot) {
        return ExtensionPaths.workspacePluginsDir(workspaceRoot);
    }

    private static Path resolvePluginsRoot(String scope, String workspaceRoot) {
        String normalized = scope == null ? "user" : scope.trim().toLowerCase(Locale.ROOT);
        if ("workspace".equals(normalized)) {
            if (workspaceRoot == null || workspaceRoot.isBlank()) {
                throw new IllegalArgumentException("workspaceRoot is required for workspace scope");
            }
            return ExtensionPaths.workspacePluginsDir(workspaceRoot);
        }
        return ExtensionPaths.userPluginsDir();
    }

    private static String sanitizeDirName(String name) {
        String cleaned = name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException("Invalid plugin name");
        }
        return cleaned;
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path dest = target.resolve(source.relativize(dir));
                Files.createDirectories(dest);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path dest = target.resolve(source.relativize(file));
                Files.createDirectories(dest.getParent());
                Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteRecursive(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw e;
        }
    }
}
