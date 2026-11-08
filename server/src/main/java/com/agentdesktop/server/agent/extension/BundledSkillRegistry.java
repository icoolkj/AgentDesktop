package com.agentdesktop.server.agent.extension;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Skills shipped inside the server JAR — always listed without disk install. */
@Component
public class BundledSkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(BundledSkillRegistry.class);

    private volatile List<SkillEntry> cached;

    public List<SkillEntry> bundledSkills() {
        List<SkillEntry> local = cached;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cached != null) {
                return cached;
            }
            cached = List.copyOf(loadBundled());
            return cached;
        }
    }

    private List<SkillEntry> loadBundled() {
        Map<String, SkillEntry> byName = new LinkedHashMap<>();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:skill-catalog/*/SKILL.md");
            for (Resource resource : resources) {
                SkillEntry entry = parseBundled(resource);
                if (entry != null) {
                    byName.putIfAbsent(entry.name(), entry);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load bundled skills", e);
        }
        return List.copyOf(byName.values());
    }

    private SkillEntry parseBundled(Resource resource) {
        try {
            String uri = resource.getURI().toString();
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
            try (InputStream in = resource.getInputStream()) {
                raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            return SkillCatalog.parseSkillFileContent(
                    catalogId.toLowerCase(Locale.ROOT),
                    raw,
                    "bundled",
                    null,
                    null,
                    catalogId);
        } catch (Exception e) {
            log.debug("Skip bundled skill {}: {}", resource, e.getMessage());
            return null;
        }
    }
}
