package com.pics2text.worker.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrintRenderTempCleanupTest {
    @TempDir Path temp;

    @Test void removesOnlyExpiredRenderDirectories() throws Exception {
        Path stale = Files.createDirectories(temp.resolve("render-stale/photos"));
        Files.writeString(stale.resolve("source.jpg"), "temporary");
        Files.setLastModifiedTime(stale.getParent(), FileTime.from(Instant.now().minus(Duration.ofHours(25))));
        Path recent = Files.createDirectories(temp.resolve("render-recent"));
        Path unrelated = Files.createDirectories(temp.resolve("keep-me"));
        var properties = new PrintRenderProperties(temp, Path.of("/python"), Path.of("/renderer"),
                Path.of("/profile"), 1, 1, 64L * 1024 * 1024, 64,
                Duration.ofHours(6), Duration.ofHours(24));

        new PrintRenderTempCleanup(properties).cleanup();

        assertFalse(Files.exists(stale.getParent()));
        assertTrue(Files.exists(recent));
        assertTrue(Files.exists(unrelated));
    }
}
