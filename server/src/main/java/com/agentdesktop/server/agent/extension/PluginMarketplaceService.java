package com.agentdesktop.server.agent.extension;

import com.agentdesktop.server.dto.PluginInstallRequestDto;
import com.agentdesktop.server.dto.PluginInstallResultDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** CC official plugin marketplace — HTTP zip install (git optional fallback). */
@Service
public class PluginMarketplaceService {

    private static final Logger log = LoggerFactory.getLogger(PluginMarketplaceService.class);
    private static final String MARKETPLACE_NAME = "claude-plugins-official";
    private static final String MARKETPLACE_JSON_URL =
            "https://raw.githubusercontent.com/anthropics/claude-plugins-official/main/.claude-plugin/marketplace.json";
    private static final String REPO_ZIP_URL =
            "https://codeload.github.com/anthropics/claude-plugins-official/zip/refs/heads/main";
    private static final String REPO_GIT_URL = "https://github.com/anthropics/claude-plugins-official.git";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final ObjectMapper objectMapper;
    private final PluginInstallService pluginInstallService;
    private final ExtensionLoader extensionLoader;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private volatile List<Map<String, String>> cachedPlugins;
    private volatile Map<String, JsonNode> cachedPluginNodes;
    private volatile Instant cacheExpiresAt = Instant.EPOCH;
    private volatile String lastCatalogLoadError;

    private final GitHubMarketplaceFetcher githubFetcher;

    public PluginMarketplaceService(
            ObjectMapper objectMapper,
            PluginInstallService pluginInstallService,
            ExtensionLoader extensionLoader) {
        this.objectMapper = objectMapper;
        this.pluginInstallService = pluginInstallService;
        this.extensionLoader = extensionLoader;
        this.githubFetcher = new GitHubMarketplaceFetcher(objectMapper, httpClient);
    }

    public Map<String, Object> listMarketplace(String workspaceRoot) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("marketplace", MARKETPLACE_NAME);
        result.put("source", REPO_GIT_URL);
        result.put("installMethods", List.of("github", "zip", "git"));
        result.put("defaultInstallMethod", "github");
        Map<String, String> installedPaths = listInstalledPluginPaths(workspaceRoot);
        List<Map<String, Object>> plugins = new ArrayList<>();
        for (Map<String, String> entry : fetchPluginEntries()) {
            Map<String, Object> item = new LinkedHashMap<>(entry);
            String nameKey = String.valueOf(entry.get("name")).toLowerCase(Locale.ROOT);
            String installPath = installedPaths.get(nameKey);
            item.put("installed", installPath != null);
            if (installPath != null) {
                item.put("installPath", installPath);
            }
            plugins.add(item);
        }
        result.put("plugins", plugins);
        Map<String, String> dirs = new LinkedHashMap<>();
        dirs.put("user", ExtensionPaths.userPluginsDir().toString());
        dirs.put("workspace", workspaceRoot == null || workspaceRoot.isBlank()
                ? null
                : ExtensionPaths.workspacePluginsDir(workspaceRoot).toString());
        result.put("pluginDirs", dirs);
        result.put("installedCount", installedPaths.size());
        if (lastCatalogLoadError != null && plugins.isEmpty()) {
            result.put("loadError", lastCatalogLoadError);
        }
        return result;
    }

    public PluginInstallResultDto installPlugin(
            String pluginName, String scope, String workspaceRoot, String installMethod) {
        if (pluginName == null || pluginName.isBlank()) {
            return PluginInstallResultDto.failure("plugin name is required");
        }
        JsonNode entry = findPluginEntry(pluginName.trim());
        if (entry == null) {
            return PluginInstallResultDto.failure("Unknown marketplace plugin: " + pluginName);
        }
        Path tempRoot = null;
        try {
            tempRoot = Files.createTempDirectory("ad-marketplace-");
            Path pluginSource = resolvePluginSource(entry, tempRoot, installMethod);
            if (!Files.isDirectory(pluginSource)) {
                return PluginInstallResultDto.failure("Plugin source directory is missing after download");
            }
            Path manifest = pluginSource.resolve("plugin.json");
            if (!Files.isRegularFile(manifest)) {
                manifest = pluginSource.resolve(".claude-plugin").resolve("plugin.json");
            }
            if (!Files.isRegularFile(manifest)) {
                return PluginInstallResultDto.failure(
                        "Downloaded plugin is missing plugin.json — check network access to GitHub");
            }
            PluginInstallRequestDto request = new PluginInstallRequestDto();
            request.setSourcePath(pluginSource.toString());
            request.setScope(scope == null ? "user" : scope);
            request.setWorkspaceRoot(workspaceRoot);
            PluginInstallResultDto result = pluginInstallService.install(request);
            if (result.isOk()) {
                extensionLoader.invalidateCache();
            }
            return result;
        } catch (Exception e) {
            log.warn("Marketplace plugin install failed for {}", pluginName, e);
            return PluginInstallResultDto.failure(e.getMessage() == null ? "Install failed" : e.getMessage());
        } finally {
            if (tempRoot != null) {
                deleteRecursive(tempRoot);
            }
        }
    }

    private Path resolvePluginSource(JsonNode entry, Path tempRoot, String installMethod) throws Exception {
        String method = installMethod == null ? "github" : installMethod.trim().toLowerCase(Locale.ROOT);
        if ("git".equals(method)) {
            return fetchViaGitClone(entry, tempRoot);
        }
        if ("zip".equals(method)) {
            return fetchViaLegacyMonorepoZip(entry, tempRoot);
        }
        return githubFetcher.fetchPluginSource(entry, tempRoot);
    }

    private Path fetchViaGitClone(JsonNode entry, Path tempRoot) throws Exception {
        JsonNode sourceNode = entry.get("source");
        if (sourceNode == null || sourceNode.isNull()) {
            throw new IOException("Missing source in marketplace entry");
        }
        if (sourceNode.isTextual()) {
            Path repoDir = tempRoot.resolve("repo");
            gitCloneShallow(REPO_GIT_URL, repoDir);
            Path pluginSource = repoDir.resolve(GitHubMarketplaceFetcher.normalizeRelativePath(sourceNode.asText("")))
                    .normalize();
            if (!Files.isDirectory(pluginSource)) {
                throw new IOException("Plugin source not found after git clone: " + sourceNode.asText(""));
            }
            return pluginSource;
        }
        if (sourceNode.isObject()) {
            String url = sourceNode.path("url").asText("");
            if (url.isBlank()) {
                throw new IOException("Marketplace source URL is missing");
            }
            Path cloneDir = tempRoot.resolve("clone");
            gitCloneShallow(url, cloneDir);
            String subPath = GitHubMarketplaceFetcher.normalizeRelativePath(sourceNode.path("path").asText(""));
            Path pluginSource = subPath.isBlank() ? cloneDir : cloneDir.resolve(subPath).normalize();
            if (!Files.isDirectory(pluginSource)) {
                throw new IOException("Plugin source not found after git clone: " + subPath);
            }
            return pluginSource;
        }
        throw new IOException("Unsupported marketplace source format");
    }

    private Path fetchViaLegacyMonorepoZip(JsonNode entry, Path tempRoot) throws Exception {
        Path repoDir = downloadMarketplaceRepo(tempRoot, "zip");
        JsonNode sourceNode = entry.get("source");
        if (sourceNode != null && sourceNode.isTextual()) {
            Path pluginSource = repoDir.resolve(GitHubMarketplaceFetcher.normalizeRelativePath(sourceNode.asText("")))
                    .normalize();
            if (!Files.isDirectory(pluginSource)) {
                throw new IOException("Plugin source not found in marketplace zip: " + sourceNode.asText(""));
            }
            return pluginSource;
        }
        Path fallback = repoDir.resolve(entry.path("name").asText("")).normalize();
        if (!Files.isDirectory(fallback)) {
            throw new IOException(
                    "Legacy zip install only supports in-repo plugins — use default github install method");
        }
        return fallback;
    }

    public PluginInstallResultDto installPlugin(String pluginName, String scope, String workspaceRoot) {
        return installPlugin(pluginName, scope, workspaceRoot, "github");
    }

    private Path downloadMarketplaceRepo(Path tempRoot, String installMethod) throws Exception {
        String method = installMethod == null ? "zip" : installMethod.trim().toLowerCase(Locale.ROOT);
        if ("git".equals(method)) {
            Path repoDir = tempRoot.resolve("repo");
            gitCloneShallow(REPO_GIT_URL, repoDir);
            return repoDir;
        }
        Path extractDir = tempRoot.resolve("extract");
        Files.createDirectories(extractDir);
        downloadAndUnzip(REPO_ZIP_URL, extractDir);
        try (Stream<Path> children = Files.list(extractDir)) {
            List<Path> roots = children.filter(Files::isDirectory).toList();
            if (roots.size() == 1) {
                return roots.get(0);
            }
        }
        return extractDir;
    }

    private void downloadAndUnzip(String zipUrl, Path targetDir) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(zipUrl))
                .timeout(Duration.ofMinutes(3))
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to download marketplace zip: HTTP " + response.statusCode());
        }
        try (InputStream body = response.body(); ZipInputStream zip = new ZipInputStream(body)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    Files.createDirectories(targetDir.resolve(entry.getName()));
                } else {
                    Path out = targetDir.resolve(entry.getName()).normalize();
                    if (!out.startsWith(targetDir)) {
                        throw new IOException("Zip entry escapes target directory");
                    }
                    Files.createDirectories(out.getParent());
                    Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }

    private Map<String, String> listInstalledPluginPaths(String workspaceRoot) {
        Map<String, String> byName = new LinkedHashMap<>();
        for (PluginRegistry.PluginManifest plugin : extensionLoader.load(workspaceRoot).plugins()) {
            byName.put(plugin.name().toLowerCase(Locale.ROOT), plugin.root().toString());
        }
        return byName;
    }

    private List<Map<String, String>> fetchPluginEntries() {
        if (cachedPlugins != null && Instant.now().isBefore(cacheExpiresAt)) {
            return copyPluginEntries(cachedPlugins);
        }
        synchronized (this) {
            if (cachedPlugins != null && Instant.now().isBefore(cacheExpiresAt)) {
                return copyPluginEntries(cachedPlugins);
            }
            cachedPlugins = loadPluginEntriesFromNetwork();
            cacheExpiresAt = Instant.now().plus(CACHE_TTL);
            return copyPluginEntries(cachedPlugins);
        }
    }

    private List<Map<String, String>> loadPluginEntriesFromNetwork() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MARKETPLACE_JSON_URL))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                lastCatalogLoadError = "GitHub marketplace.json HTTP " + response.statusCode();
                return List.of();
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode plugins = root.path("plugins");
            if (!plugins.isArray()) {
                lastCatalogLoadError = "Invalid marketplace.json format";
                return List.of();
            }
            lastCatalogLoadError = null;
            List<Map<String, String>> items = new ArrayList<>();
            Map<String, JsonNode> nodes = new LinkedHashMap<>();
            for (JsonNode plugin : plugins) {
                Map<String, String> item = new LinkedHashMap<>();
                String name = plugin.path("name").asText("");
                item.put("name", name);
                item.put("description", plugin.path("description").asText(""));
                item.put("version", plugin.path("version").asText(""));
                item.put("source", summarizeSource(plugin.get("source")));
                items.add(item);
                if (!name.isBlank()) {
                    nodes.put(name.toLowerCase(Locale.ROOT), plugin);
                }
            }
            cachedPluginNodes = nodes;
            return items;
        } catch (Exception e) {
            lastCatalogLoadError = e.getMessage() == null ? "Failed to fetch marketplace catalog" : e.getMessage();
            log.warn("Failed to fetch marketplace catalog: {}", lastCatalogLoadError);
            cachedPluginNodes = Map.of();
            return List.of();
        }
    }

    private static String summarizeSource(JsonNode source) {
        if (source == null || source.isNull()) {
            return "";
        }
        if (source.isTextual()) {
            return source.asText("");
        }
        if (source.isObject()) {
            String kind = source.path("source").asText("");
            String url = source.path("url").asText("");
            if (!url.isBlank()) {
                return kind + ":" + url;
            }
            return kind;
        }
        return "";
    }

    private static List<Map<String, String>> copyPluginEntries(List<Map<String, String>> source) {
        List<Map<String, String>> copy = new ArrayList<>();
        for (Map<String, String> item : source) {
            copy.add(new LinkedHashMap<>(item));
        }
        return copy;
    }

    private JsonNode findPluginEntry(String pluginName) {
        fetchPluginEntries();
        Map<String, JsonNode> nodes = cachedPluginNodes;
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }
        JsonNode exact = nodes.get(pluginName.toLowerCase(Locale.ROOT));
        if (exact != null) {
            return exact;
        }
        for (JsonNode plugin : nodes.values()) {
            if (pluginName.equalsIgnoreCase(plugin.path("name").asText(""))) {
                return plugin;
            }
        }
        return null;
    }

    private static void gitCloneShallow(String repoUrl, Path targetDir) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(
                "git", "clone", "--depth", "1", repoUrl, targetDir.toString());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        int code = process.waitFor();
        if (code != 0) {
            throw new IOException("git clone failed with exit code " + code);
        }
    }

    private static void deleteRecursive(Path root) {
        try {
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
            }
        } catch (IOException | RuntimeException e) {
            Throwable cause = e instanceof RuntimeException re && re.getCause() != null ? re.getCause() : e;
            log.debug("Failed to delete temp marketplace dir: {}", cause.getMessage());
        }
    }
}
