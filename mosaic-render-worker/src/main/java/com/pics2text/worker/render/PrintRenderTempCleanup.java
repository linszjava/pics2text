package com.pics2text.worker.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class PrintRenderTempCleanup {
    private static final Logger log = LoggerFactory.getLogger(PrintRenderTempCleanup.class);
    private final PrintRenderProperties properties;

    PrintRenderTempCleanup(PrintRenderProperties properties) { this.properties = properties; }

    @EventListener(ApplicationReadyEvent.class)
    public void afterStartup() { cleanup(); }

    @Scheduled(fixedDelayString = "${print-render.cleanup-interval:1h}")
    public void cleanup() {
        Path root = properties.tempRoot().toAbsolutePath().normalize();
        Instant cutoff = Instant.now().minus(properties.staleTempAge());
        try {
            Files.createDirectories(root);
            try (var children = Files.list(root)) {
                children.filter(path -> path.getFileName().toString().startsWith("render-"))
                        .filter(path -> Files.isDirectory(path) && !Files.isSymbolicLink(path))
                        .filter(path -> olderThan(path, cutoff))
                        .forEach(this::deleteTree);
            }
        } catch (IOException exception) {
            log.warn("无法扫描高清渲染临时目录 {}", root, exception);
        }
    }

    private boolean olderThan(Path path, Instant cutoff) {
        try { return Files.getLastModifiedTime(path).toInstant().isBefore(cutoff); }
        catch (IOException exception) { return false; }
    }

    private void deleteTree(Path root) {
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); }
                catch (IOException exception) { log.warn("无法清理高清渲染临时文件 {}", path, exception); }
            });
        } catch (IOException exception) {
            log.warn("无法清理高清渲染临时目录 {}", root, exception);
        }
    }
}
