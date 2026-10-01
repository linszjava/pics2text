package com.pics2text.worker.render;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "print-render")
public record PrintRenderProperties(Path tempRoot, Path pythonExecutable, Path rendererScript,
        Path srgbProfile, long maxSourceBytes, long maxOutputBytes, long nativeMemoryBytes,
        int maxOpenFiles, Duration timeout, Duration staleTempAge) {
    public PrintRenderProperties {
        if (tempRoot == null || pythonExecutable == null || rendererScript == null || srgbProfile == null
                || maxSourceBytes <= 0 || maxOutputBytes <= 0 || nativeMemoryBytes < 64L * 1024 * 1024
                || maxOpenFiles < 64 || timeout == null || timeout.isZero() || timeout.isNegative()
                || staleTempAge == null || staleTempAge.compareTo(timeout) <= 0) {
            throw new IllegalArgumentException("高清渲染资源限制配置无效");
        }
    }
}
