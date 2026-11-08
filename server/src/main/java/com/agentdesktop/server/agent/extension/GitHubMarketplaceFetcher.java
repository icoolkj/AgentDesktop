package com.agentdesktop.server.agent.extension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Resolves marketplace plugin sources (local path, git-subdir, external url) via GitHub.
 */
final class GitHubMarketplaceFetcher {

    private static final Logger log = LoggerFactory.getLogger(GitHubMarketplaceFetcher.class);
    private static final Pattern GITHUB_REPO = Pattern.compile(
            "(?:https?://)?(?:www\\.)?github\\.com/([^/]+)/([^/.]+)(?:\\.git)?/?$",
            Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiBase;
    private final String rawBase;

    GitHubMarketplaceFetcher(ObjectMapper objectMapper, HttpClient httpClient) {
        this(objectMapper, httpClient, "https://api.github.com", "https://raw.githubusercontent.com");
    }

    GitHubMarketplaceFetcher(
            ObjectMapper objectMapper, HttpClient httpClient, String apiBase, String rawBase) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.apiBase = trimTrailingSlash(apiBase);
        this.rawBase = trimTrailingSlash(rawBase);
    }

    Path fetchPluginSource(JsonNode entry, Path tempRoot) throws Exception {
        JsonNode sourceNode = entry.get("source");
        if (sourceNode == null || sourceNode.isNull()) {
            throw new IOException("Missing source in marketplace entry");
        }
        Path pluginDir = tempRoot.resolve("plugin");
        Files.createDirectories(pluginDir);

        if (sourceNode.isTextual()) {
            String relative = normalizeRelativePath(sourceNode.asText(""));
            fetchOfficialRepoSubdir(pluginDir, relative, "main");
            return pluginDir;
        }

        if (!sourceNode.isObject()) {
            throw new IOException("Unsupported marketplace source format");
        }

        String kind = sourceNode.path("source").asText("");
        String repoUrl = sourceNode.path("url").asText("");
        if (repoUrl.isBlank()) {
            throw new IOException("Marketplace source URL is missing");
        }

        GitHubRepo repo = parseGitHubUrl(repoUrl);
        String subPath = normalizeRelativePath(sourceNode.path("path").asText(""));
        String sha = sourceNode.path("sha").asText("");
        String ref = sourceNode.path("ref").asText("");
        String revision = !sha.isBlank() ? sha : (!ref.isBlank() ? ref : "main");

        if ("git-subdir".equalsIgnoreCase(kind)) {
            if (subPath.isBlank()) {
                throw new IOException("git-subdir source requires path");
            }
            fetchRepoSubdir(pluginDir, repo, revision, subPath);
            return pluginDir;
        }

        if ("url".equalsIgnoreCase(kind)) {
            fetchRepoRoot(pluginDir, repo, revision);
            return pluginDir;
        }

        throw new IOException("Unsupported marketplace source type: " + kind);
    }

    private void fetchOfficialRepoSubdir(Path targetDir, String subPath, String ref) throws Exception {
        GitHubRepo official = new GitHubRepo("anthropics", "claude-plugins-official");
        fetchRepoSubdir(targetDir, official, ref, subPath);
    }

    private void fetchRepoRoot(Path targetDir, GitHubRepo repo, String revision) throws Exception {
        try {
            fetchViaTreeApi(targetDir, repo, revision, "");
        } catch (Exception treeError) {
            log.debug("GitHub tree fetch failed for {}/{}@{}: {}", repo.owner(), repo.repo(), revision,
                    treeError.getMessage());
            fetchViaRepoZip(targetDir, repo, revision, "");
        }
    }

    private void fetchRepoSubdir(Path targetDir, GitHubRepo repo, String revision, String subPath)
            throws Exception {
        try {
            fetchViaTreeApi(targetDir, repo, revision, subPath);
        } catch (Exception treeError) {
            log.debug("GitHub tree fetch failed for {}/{}@{} path {}: {}", repo.owner(), repo.repo(), revision,
                    subPath, treeError.getMessage());
            fetchViaRepoZip(targetDir, repo, revision, subPath);
        }
    }

    private void fetchViaTreeApi(Path targetDir, GitHubRepo repo, String revision, String subPath)
            throws Exception {
        String resolvedRevision = resolveRevision(repo, revision);
        String prefix = normalizeRelativePath(subPath);
        if (!prefix.isEmpty() && !prefix.endsWith("/")) {
            prefix = prefix + "/";
        }

        JsonNode tree = fetchTree(repo, resolvedRevision);
        if (tree.path("truncated").asBoolean(false)) {
            throw new IOException("GitHub tree truncated — use zip fallback");
        }

        List<JsonNode> blobs = new ArrayList<>();
        for (JsonNode item : tree.path("tree")) {
            if (!"blob".equals(item.path("type").asText(""))) {
                continue;
            }
            String path = item.path("path").asText("");
            if (prefix.isEmpty()) {
                blobs.add(item);
            } else if (path.startsWith(prefix)) {
                blobs.add(item);
            }
        }

        if (blobs.isEmpty()) {
            throw new IOException("No files found at " + (prefix.isEmpty() ? "repo root" : prefix));
        }

        for (JsonNode blob : blobs) {
            String path = blob.path("path").asText("");
            String relative = prefix.isEmpty() ? path : path.substring(prefix.length());
            if (relative.isBlank()) {
                continue;
            }
            Path out = targetDir.resolve(relative).normalize();
            if (!out.startsWith(targetDir)) {
                throw new IOException("Path escapes plugin directory: " + relative);
            }
            downloadRawFile(repo, resolvedRevision, path, out);
        }
    }

    private JsonNode fetchTree(GitHubRepo repo, String revision) throws Exception {
        URI uri = URI.create(apiBase + "/repos/" + repo.owner() + "/" + repo.repo()
                + "/git/trees/" + revision + "?recursive=1");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "agent-desktop")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("GitHub tree API HTTP " + response.statusCode() + " for "
                    + repo.owner() + "/" + repo.repo() + "@" + revision);
        }
        return objectMapper.readTree(response.body());
    }

    private String resolveRevision(GitHubRepo repo, String revision) throws Exception {
        if (revision == null || revision.isBlank()) {
            return "main";
        }
        if (revision.length() >= 40 && revision.matches("[0-9a-fA-F]+")) {
            return revision;
        }
        URI uri = URI.create(apiBase + "/repos/" + repo.owner() + "/" + repo.repo() + "/commits/" + revision);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "agent-desktop")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return revision;
        }
        JsonNode root = objectMapper.readTree(response.body());
        String sha = root.path("sha").asText("");
        return sha.isBlank() ? revision : sha;
    }

    private void downloadRawFile(GitHubRepo repo, String revision, String path, Path out) throws Exception {
        URI uri = URI.create(rawBase + "/" + repo.owner() + "/" + repo.repo() + "/" + revision + "/" + path);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to download " + path + ": HTTP " + response.statusCode());
        }
        Files.createDirectories(out.getParent());
        try (InputStream body = response.body()) {
            Files.copy(body, out, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void fetchViaRepoZip(Path targetDir, GitHubRepo repo, String revision, String subPath)
            throws Exception {
        String zipUrl = "https://codeload.github.com/" + repo.owner() + "/" + repo.repo()
                + "/zip/" + revision;
        Path extractRoot = targetDir.getParent().resolve("zip-extract");
        Files.createDirectories(extractRoot);
        downloadAndUnzip(zipUrl, extractRoot);
        Path repoRoot = findSingleExtractRoot(extractRoot);
        Path source = subPath.isBlank() ? repoRoot : repoRoot.resolve(normalizeRelativePath(subPath)).normalize();
        if (!Files.isDirectory(source)) {
            throw new IOException("Plugin source not found in repository zip: " + subPath);
        }
        copyDirectory(source, targetDir);
    }

    private static Path findSingleExtractRoot(Path extractDir) throws IOException {
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
            throw new IOException("Failed to download repository zip: HTTP " + response.statusCode());
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

    private static void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path path : walk.sorted(Comparator.naturalOrder()).toList()) {
                Path dest = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    static GitHubRepo parseGitHubUrl(String url) {
        Matcher matcher = GITHUB_REPO.matcher(url.trim().replace('\\', '/'));
        if (!matcher.find()) {
            throw new IllegalArgumentException("Not a GitHub repository URL: " + url);
        }
        return new GitHubRepo(matcher.group(1), matcher.group(2));
    }

    static String normalizeRelativePath(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.trim().replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    record GitHubRepo(String owner, String repo) {
        GitHubRepo {
            owner = owner == null ? "" : owner;
            repo = repo == null ? "" : repo;
        }

        @Override
        public String toString() {
            return owner + "/" + repo;
        }
    }
}
