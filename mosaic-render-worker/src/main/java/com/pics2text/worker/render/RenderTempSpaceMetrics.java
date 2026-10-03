package com.pics2text.worker.render;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class RenderTempSpaceMetrics {
    private static final Logger log = LoggerFactory.getLogger(RenderTempSpaceMetrics.class);
    private final Path tempRoot;
    private final MeterRegistry meterRegistry;
    private final AtomicLong usableBytes = new AtomicLong();
    private final AtomicLong totalBytes = new AtomicLong();

    RenderTempSpaceMetrics(PrintRenderProperties properties, MeterRegistry meterRegistry) {
        this.tempRoot = properties.tempRoot().toAbsolutePath().normalize();
        this.meterRegistry = meterRegistry;
        Gauge.builder("pics2text.render.temp.usable.bytes", usableBytes, AtomicLong::get)
                .description("高清渲染临时目录可用字节数").baseUnit("bytes").register(meterRegistry);
        Gauge.builder("pics2text.render.temp.total.bytes", totalBytes, AtomicLong::get)
                .description("高清渲染临时目录文件系统总字节数").baseUnit("bytes").register(meterRegistry);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void afterStartup() { refresh(); }

    @Scheduled(fixedDelayString = "${print-render.temp-space-check-interval:30s}")
    public void refresh() {
        try {
            Files.createDirectories(tempRoot);
            var store = Files.getFileStore(tempRoot);
            usableBytes.set(store.getUsableSpace());
            totalBytes.set(store.getTotalSpace());
        } catch (IOException exception) {
            meterRegistry.counter("pics2text.render.temp.space.checks", "result", "failure").increment();
            log.warn("无法读取高清渲染临时目录空间 {}", tempRoot, exception);
        }
    }
}
