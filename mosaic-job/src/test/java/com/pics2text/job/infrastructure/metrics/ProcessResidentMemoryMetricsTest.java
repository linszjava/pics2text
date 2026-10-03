package com.pics2text.job.infrastructure.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessResidentMemoryMetricsTest {

    @TempDir
    Path tempDirectory;

    @Test
    void exposesLinuxResidentMemoryInBytes() throws Exception {
        Path status = tempDirectory.resolve("status");
        Files.writeString(status, "Name:\tjava\nVmSize:\t9999 kB\nVmRSS:\t1234 kB\n");
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new ProcessResidentMemoryMetrics(status).bindTo(registry);

        assertThat(registry.get("pics2text.process.resident.memory").gauge().value())
                .isEqualTo(1234d * 1024d);
    }

    @Test
    void reportsUnavailableWhenProcStatusDoesNotExist() {
        ProcessResidentMemoryMetrics metrics = new ProcessResidentMemoryMetrics(tempDirectory.resolve("missing"));

        assertThat(metrics.readBytes()).isNaN();
    }
}
