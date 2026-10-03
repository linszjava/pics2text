package com.pics2text.worker.render;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RenderTempSpaceMetricsTest {
    @TempDir Path temp;

    @Test void publishesTempFilesystemCapacity() {
        var properties = new PrintRenderProperties(temp.resolve("renders"), Path.of("/python"),
                Path.of("/renderer"), Path.of("/profile"), 1, 1,
                64L * 1024 * 1024, 64, Duration.ofHours(6), Duration.ofHours(24));
        var registry = new SimpleMeterRegistry();
        var metrics = new RenderTempSpaceMetrics(properties, registry);

        metrics.refresh();

        assertTrue(registry.get("pics2text.render.temp.usable.bytes").gauge().value() > 0);
        assertTrue(registry.get("pics2text.render.temp.total.bytes").gauge().value() > 0);
    }
}
