package com.pics2text.job.infrastructure.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class ProcessResidentMemoryMetrics implements MeterBinder {
    private static final Pattern VM_RSS = Pattern.compile("^VmRSS:\\s+(\\d+)\\s+kB\\s*$", Pattern.MULTILINE);
    private static final double BYTES_PER_KIBIBYTE = 1024d;

    private final Path processStatus;

    public ProcessResidentMemoryMetrics() {
        this(Path.of("/proc/self/status"));
    }

    ProcessResidentMemoryMetrics(Path processStatus) {
        this.processStatus = processStatus;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("pics2text.process.resident.memory", this, ProcessResidentMemoryMetrics::readBytes)
                .description("Linux 进程当前常驻内存 RSS")
                .baseUnit("bytes")
                .register(registry);
    }

    double readBytes() {
        try {
            Matcher matcher = VM_RSS.matcher(Files.readString(processStatus));
            return matcher.find() ? Double.parseDouble(matcher.group(1)) * BYTES_PER_KIBIBYTE : Double.NaN;
        } catch (IOException | NumberFormatException ignored) {
            return Double.NaN;
        }
    }
}
