package com.agentdesktop.server.agent.extension;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Hot-reloads extension bundle when skill directories change on disk. */
@Component
public class SkillDirectoryWatcher {

    private static final Logger log = LoggerFactory.getLogger(SkillDirectoryWatcher.class);

    private final ExtensionLoader extensionLoader;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "skill-dir-watcher");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);

    private WatchService watchService;
    private final Set<WatchKey> keys = new HashSet<>();

    public SkillDirectoryWatcher(ExtensionLoader extensionLoader) {
        this.extensionLoader = extensionLoader;
        executor.submit(this::startWatching);
    }

    private void startWatching() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            watchService = FileSystems.getDefault().newWatchService();
            registerIfExists(ExtensionPaths.userSkillsDir());
            registerIfExists(ExtensionPaths.userAgentDesktopSkillsDir());
            registerTree(ExtensionPaths.userPluginsDir());
            log.info("Skill directory watcher started");
            while (running.get()) {
                WatchKey key = watchService.take();
                boolean changed = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    changed = true;
                }
                if (changed) {
                    extensionLoader.invalidateCache();
                }
                if (!key.reset()) {
                    keys.remove(key);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Skill directory watcher stopped: {}", e.getMessage());
        }
    }

    private void registerIfExists(Path dir) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        WatchKey key = dir.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);
        keys.add(key);
    }

    private void registerTree(Path root) throws IOException {
        if (root == null || !Files.isDirectory(root)) {
            return;
        }
        try (var stream = Files.walk(root, 4)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                try {
                    registerIfExists(dir);
                } catch (IOException e) {
                    log.debug("Skip watch registration for {}: {}", dir, e.getMessage());
                }
            });
        }
    }

    @PreDestroy
    void shutdown() {
        running.set(false);
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {
                // shutting down
            }
        }
        executor.shutdownNow();
    }
}
