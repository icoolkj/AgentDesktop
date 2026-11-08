package com.agentdesktop.server.agent.extension;

import com.agentdesktop.server.dto.SkillCatalogInstallRequestDto;
import com.agentdesktop.server.dto.SkillInstallRequestDto;
import com.agentdesktop.server.dto.SkillInstallResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.stream.Stream;

@Service
public class SkillInstallService {

    private static final Logger log = LoggerFactory.getLogger(SkillInstallService.class);
    private static final java.util.regex.Pattern FRONTMATTER = java.util.regex.Pattern.compile(
            "^---\\s*\\n(.*?)\\n---\\s*\\n(.*)",
            java.util.regex.Pattern.DOTALL);

    private final ExtensionLoader extensionLoader;

    public SkillInstallService(ExtensionLoader extensionLoader) {
        this.extensionLoader = extensionLoader;
    }

    public SkillInstallResultDto install(SkillInstallRequestDto request) {
        if (request == null || request.getSourcePath() == null || request.getSourcePath().isBlank()) {
            return SkillInstallResultDto.failure("sourcePath is required");
        }

        Path source;
        try {
            source = Path.of(request.getSourcePath().trim()).toAbsolutePath().normalize();
        } catch (Exception e) {
            return SkillInstallResultDto.failure("Invalid sourcePath: " + e.getMessage());
        }

        Path skillDir;
        try {
            skillDir = resolveSkillDirectory(source);
        } catch (IllegalArgumentException e) {
            return SkillInstallResultDto.failure(e.getMessage());
        }

        String skillName = skillDir.getFileName().toString().toLowerCase(Locale.ROOT);
        Path skillsRoot;
        try {
            skillsRoot = resolveSkillsRoot(request.getScope(), request.getWorkspaceRoot());
        } catch (IllegalArgumentException e) {
            return SkillInstallResultDto.failure(e.getMessage());
        }

        return copySkillToTarget(skillDir, skillName, skillsRoot);
    }

    public SkillInstallResultDto installFromCatalog(SkillCatalogInstallRequestDto request) {
        if (request == null || request.getCatalogId() == null || request.getCatalogId().isBlank()) {
            return SkillInstallResultDto.failure("catalogId is required");
        }
        String catalogId = sanitizeDirName(request.getCatalogId().trim());

        Path skillsRoot;
        try {
            skillsRoot = resolveSkillsRoot(request.getScope(), request.getWorkspaceRoot());
        } catch (IllegalArgumentException e) {
            return SkillInstallResultDto.failure(e.getMessage());
        }

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource skillMd = resolver.getResource("classpath:skill-catalog/" + catalogId + "/SKILL.md");
            if (!skillMd.exists()) {
                return SkillInstallResultDto.failure("Unknown catalog skill: " + catalogId);
            }
            Path target = skillsRoot.resolve(catalogId).normalize();
            if (!target.startsWith(skillsRoot)) {
                return SkillInstallResultDto.failure("Invalid catalog skill id");
            }
            Files.createDirectories(skillsRoot);
            if (Files.exists(target)) {
                deleteRecursive(target);
            }
            Files.createDirectories(target);
            try (InputStream in = skillMd.getInputStream()) {
                Files.copy(in, target.resolve("SKILL.md"), StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("Installed catalog skill {} -> {}", catalogId, target);
            return SkillInstallResultDto.success(catalogId, target.toString());
        } catch (Exception e) {
            log.warn("Catalog skill install failed", e);
            return SkillInstallResultDto.failure(e.getMessage() == null ? "Install failed" : e.getMessage());
        }
    }

    public SkillInstallResultDto uninstall(String skillName, String scope, String workspaceRoot) {
        if (skillName == null || skillName.isBlank()) {
            return SkillInstallResultDto.failure("skill name is required");
        }
        Path skillsRoot = resolveSkillsRoot(scope, workspaceRoot);
        Path target = skillsRoot.resolve(sanitizeDirName(skillName)).normalize();
        if (!target.startsWith(skillsRoot)) {
            return SkillInstallResultDto.failure("Invalid skill name");
        }
        if (!Files.isDirectory(target)) {
            return SkillInstallResultDto.failure("Skill not installed: " + skillName);
        }
        try {
            deleteRecursive(target);
            return SkillInstallResultDto.success(skillName, target.toString());
        } catch (IOException e) {
            return SkillInstallResultDto.failure(e.getMessage());
        }
    }

    public List<Map<String, Object>> listCatalog() {
        List<Map<String, Object>> items = new ArrayList<>();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:skill-catalog/*/SKILL.md");
            for (Resource resource : resources) {
                Map<String, Object> item = parseCatalogEntry(resource);
                if (item != null) {
                    items.add(item);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to scan skill catalog", e);
        }
        return items;
    }

    public Map<String, String> skillDirs(String workspaceRoot) {
        Map<String, String> dirs = new LinkedHashMap<>();
        dirs.put("userAd", ExtensionPaths.userAgentDesktopSkillsDir().toString());
        dirs.put("user", ExtensionPaths.userSkillsDir().toString());
        dirs.put("workspace", workspaceRoot == null || workspaceRoot.isBlank()
                ? null
                : ExtensionPaths.workspaceSkillsDir(workspaceRoot).toString());
        return dirs;
    }

    public List<Map<String, Object>> listInstalledSkills(String workspaceRoot) {
        List<SkillEntry> skills = extensionLoader.load(workspaceRoot).skills();
        List<Map<String, Object>> result = new ArrayList<>();
        for (SkillEntry skill : skills) {
            result.add(toSkillMap(skill, workspaceRoot));
        }
        return result;
    }

    private Map<String, Object> toSkillMap(SkillEntry skill, String workspaceRoot) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", skill.name());
        map.put("description", skill.description());
        map.put("source", skill.source());
        map.put("sourceLabel", sourceLabel(skill.source()));
        map.put("userInvocable", skill.userInvocable());
        map.put("modelInvocable", skill.modelInvocable());
        map.put("conditional", skill.conditional());
        map.put("forkContext", skill.forkContext());
        map.put("allowedTools", skill.allowedTools());
        map.put("installPath", resolveInstallPath(skill));
        String managedScope = inferManagedScope(skill.skillRoot(), workspaceRoot);
        map.put("managed", managedScope != null);
        map.put("managedScope", managedScope);
        return map;
    }

    static String inferManagedScope(Path skillRoot, String workspaceRoot) {
        if (skillRoot == null) {
            return null;
        }
        Path normalized = skillRoot.toAbsolutePath().normalize();
        if (normalized.startsWith(ExtensionPaths.userAgentDesktopSkillsDir())) {
            return "user-ad";
        }
        if (normalized.startsWith(ExtensionPaths.userSkillsDir())) {
            return "user";
        }
        if (workspaceRoot != null && !workspaceRoot.isBlank()) {
            Path wsSkills = ExtensionPaths.workspaceSkillsDir(workspaceRoot);
            if (normalized.startsWith(wsSkills)) {
                return "workspace";
            }
        }
        return null;
    }

    private static String resolveInstallPath(SkillEntry skill) {
        if (skill.skillRoot() != null) {
            return skill.skillRoot().toString();
        }
        if (skill.isBundledInBinary()) {
            return "bundled:" + skill.bundledCatalogId();
        }
        if (skill.isMcpSkill()) {
            return "mcp:" + skill.name();
        }
        return null;
    }

    private static String sourceLabel(String source) {
        return switch (source == null ? "" : source) {
            case "user-agent-desktop" -> "用户 (skills-agent-desktop)";
            case "user" -> "用户 (~/.agent-desktop/skills)";
            case "project" -> "工作区 (.agent-desktop/skills)";
            case "project-claude" -> "工作区 (.claude/skills)";
            case "plugin" -> "插件";
            case "bundled" -> "内置 (JAR)";
            case "mcp" -> "MCP 服务器";
            default -> source == null || source.isBlank() ? "未知" : source;
        };
    }

    private SkillInstallResultDto copySkillToTarget(Path skillDir, String skillName, Path skillsRoot) {
        Path target = skillsRoot.resolve(sanitizeDirName(skillName)).normalize();
        if (!target.startsWith(skillsRoot)) {
            return SkillInstallResultDto.failure("Invalid skill name");
        }
        try {
            Files.createDirectories(skillsRoot);
            if (Files.exists(target)) {
                deleteRecursive(target);
            }
            copyDirectory(skillDir, target);
            log.info("Installed skill {} -> {}", skillName, target);
            return SkillInstallResultDto.success(skillName, target.toString());
        } catch (Exception e) {
            log.warn("Skill install failed", e);
            return SkillInstallResultDto.failure(e.getMessage() == null ? "Install failed" : e.getMessage());
        }
    }

    private static Path resolveSkillDirectory(Path source) {
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException("sourcePath must be an existing directory");
        }
        if (Files.isRegularFile(source.resolve("SKILL.md"))) {
            return source;
        }
        try (Stream<Path> children = Files.list(source)) {
            List<Path> skillDirs = children
                    .filter(Files::isDirectory)
                    .filter(dir -> Files.isRegularFile(dir.resolve("SKILL.md")))
                    .toList();
            if (skillDirs.size() == 1) {
                return skillDirs.get(0);
            }
            if (skillDirs.size() > 1) {
                throw new IllegalArgumentException(
                        "Multiple skills found; point sourcePath at a single skill directory containing SKILL.md");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read sourcePath: " + e.getMessage());
        }
        throw new IllegalArgumentException("Directory must contain SKILL.md");
    }

    private static Path resolveSkillsRoot(String scope, String workspaceRoot) {
        String normalized = scope == null ? "user-ad" : scope.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "user-ad", "user_agent_desktop", "skills-agent-desktop" ->
                    ExtensionPaths.userAgentDesktopSkillsDir();
            case "user" -> ExtensionPaths.userSkillsDir();
            case "workspace" -> {
                if (workspaceRoot == null || workspaceRoot.isBlank()) {
                    throw new IllegalArgumentException("workspaceRoot is required for workspace scope");
                }
                yield ExtensionPaths.workspaceSkillsDir(workspaceRoot);
            }
            default -> throw new IllegalArgumentException("Invalid scope: " + scope);
        };
    }

    private static String sanitizeDirName(String name) {
        String cleaned = name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException("Invalid skill name");
        }
        return cleaned.toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> parseCatalogEntry(Resource skillFileResource) {
        try {
            String uri = skillFileResource.getURI().toString();
            int idx = uri.indexOf("skill-catalog/");
            if (idx < 0) {
                return null;
            }
            String tail = uri.substring(idx + "skill-catalog/".length());
            String catalogId = tail.contains("/") ? tail.substring(0, tail.indexOf('/')) : tail;
            if (catalogId.isBlank()) {
                return null;
            }
            String raw;
            try (InputStream in = skillFileResource.getInputStream()) {
                raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            String description = extractDescription(raw);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", catalogId);
            item.put("name", catalogId);
            item.put("description", description);
            return item;
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractDescription(String raw) {
        Matcher matcher = FRONTMATTER.matcher(raw);
        if (!matcher.matches()) {
            return "";
        }
        for (String line : matcher.group(1).split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("description:")) {
                return trimmed.substring("description:".length()).trim();
            }
        }
        return "";
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
