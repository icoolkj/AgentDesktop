package com.agentdesktop.server.agent.mcp;

import com.agentdesktop.server.agent.extension.SkillCatalog;
import com.agentdesktop.server.agent.extension.SkillContentLoader;
import com.agentdesktop.server.agent.extension.SkillEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Exposes MCP resource/prompt skills to the extension bundle (CC {@code loadedFrom === 'mcp'} parity). */
@Component
public class McpSkillBridge {

    private static final Logger log = LoggerFactory.getLogger(McpSkillBridge.class);

    private final McpConfigLoader configLoader;
    private final McpClientFactory clientFactory;

    private volatile List<SkillEntry> cachedSkills = List.of();

    public McpSkillBridge(McpConfigLoader configLoader, McpClientFactory clientFactory) {
        this.configLoader = configLoader;
        this.clientFactory = clientFactory;
    }

    public void invalidateCache() {
        cachedSkills = List.of();
    }

    public List<SkillEntry> listSkills() {
        refreshIfNeeded();
        return cachedSkills;
    }

    public String loadSkillContent(SkillEntry skill, String args) throws Exception {
        if (skill.mcpResourceUri() != null && skill.mcpServer() != null) {
            McpServerConfig config = configLoader.loadServers().get(skill.mcpServer());
            if (config == null) {
                throw new IllegalStateException("MCP server not configured: " + skill.mcpServer());
            }
            McpClient client = clientFactory.clientFor(config);
            String raw = client.readResource(skill.mcpServer(), skill.mcpResourceUri());
            return formatMcpBody(raw, args);
        }
        if (skill.mcpPromptName() != null && skill.mcpServer() != null) {
            McpServerConfig config = configLoader.loadServers().get(skill.mcpServer());
            if (config == null) {
                throw new IllegalStateException("MCP server not configured: " + skill.mcpServer());
            }
            McpClient client = clientFactory.clientFor(config);
            Map<String, String> arguments = parsePromptArgs(args);
            String raw = client.getPrompt(skill.mcpServer(), skill.mcpPromptName(), arguments);
            return formatMcpBody(raw, args);
        }
        throw new IllegalStateException("MCP skill missing server reference: " + skill.name());
    }

    private synchronized void refreshIfNeeded() {
        Map<String, McpServerConfig> servers = configLoader.loadServers();
        if (servers.isEmpty()) {
            cachedSkills = List.of();
            return;
        }

        Map<String, SkillEntry> byName = new LinkedHashMap<>();
        for (Map.Entry<String, McpServerConfig> entry : servers.entrySet()) {
            String serverName = entry.getKey();
            McpServerConfig config = entry.getValue();
            try {
                McpClient client = clientFactory.clientFor(config);
                for (McpResourceInfo resource : client.listResources(serverName, config)) {
                    SkillEntry skill = resourceToSkill(serverName, resource, client, config);
                    if (skill != null) {
                        byName.putIfAbsent(skill.name(), skill);
                    }
                }
            } catch (Exception e) {
                log.debug("MCP resources unavailable for {}: {}", serverName, e.getMessage());
            }
        }
        cachedSkills = List.copyOf(byName.values());
    }

    private SkillEntry resourceToSkill(
            String serverName,
            McpResourceInfo resource,
            McpClient client,
            McpServerConfig config) {
        String uri = resource.uri() == null ? "" : resource.uri();
        String namePart = inferSkillName(uri, resource.name());
        if (namePart.isBlank()) {
            return null;
        }
        String normalizedServer = normalizeMcpName(serverName);
        String skillName = normalizedServer + ":" + namePart.toLowerCase(Locale.ROOT);

        String description = resource.description();
        if (description == null || description.isBlank()) {
            description = "MCP skill from " + serverName;
        }

        try {
            String raw = client.readResource(serverName, uri);
            SkillEntry parsed = SkillCatalog.parseSkillFileContent(
                    skillName,
                    raw,
                    "mcp",
                    null,
                    null,
                    null);
            if (parsed != null) {
                return new SkillEntry(
                        parsed.name(),
                        parsed.description(),
                        parsed.whenToUse(),
                        null,
                        null,
                        "mcp",
                        parsed.userInvocable(),
                        parsed.disableModelInvocation(),
                        parsed.pathPatterns(),
                        parsed.allowedTools(),
                        false,
                        parsed.agentType(),
                        serverName,
                        uri,
                        null,
                        null);
            }
        } catch (Exception ignored) {
            // fall through to metadata-only entry
        }

        return new SkillEntry(
                skillName,
                description,
                null,
                null,
                null,
                "mcp",
                false,
                false,
                List.of(),
                List.of(),
                false,
                null,
                serverName,
                uri,
                null,
                null);
    }

    static String inferSkillName(String uri, String resourceName) {
        String candidate = resourceName == null ? "" : resourceName.trim();
        if (candidate.isBlank()) {
            candidate = uri == null ? "" : uri.trim();
        }
        String lower = candidate.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (lower.endsWith("/skill.md")) {
            int slash = lower.lastIndexOf('/');
            int prev = lower.lastIndexOf('/', slash - 1);
            if (prev >= 0 && slash > prev) {
                return candidate.substring(prev + 1, slash);
            }
        }
        if (lower.contains("/skills/")) {
            int idx = lower.indexOf("/skills/");
            String tail = candidate.substring(idx + "/skills/".length());
            int slash = tail.indexOf('/');
            return slash > 0 ? tail.substring(0, slash) : tail;
        }
        if (lower.endsWith(".md") && lower.contains("skill")) {
            return stripExtension(candidate.substring(candidate.lastIndexOf('/') + 1));
        }
        return "";
    }

    static String normalizeMcpName(String serverName) {
        return serverName.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }

    private static String stripExtension(String fileName) {
        if (fileName.toLowerCase(Locale.ROOT).endsWith(".md")) {
            return fileName.substring(0, fileName.length() - 3);
        }
        return fileName;
    }

    private static Map<String, String> parsePromptArgs(String args) {
        if (args == null || args.isBlank()) {
            return Map.of();
        }
        return Map.of("args", args.trim());
    }

    private static String formatMcpBody(String raw, String args) {
        String body = SkillContentLoader.stripFrontmatter(raw).trim();
        if (body.isBlank()) {
            body = raw.trim();
        }
        return SkillContentLoader.substituteArguments(body, args == null ? "" : args);
    }
}
