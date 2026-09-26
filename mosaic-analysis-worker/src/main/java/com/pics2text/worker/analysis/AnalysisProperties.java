package com.pics2text.worker.analysis;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "photo-analysis")
public record AnalysisProperties(long maxSourceBytes, long maxPixels, int candidateLimit,
        int dHashDistance, int pHashDistance, double minSharpness, double minBrightness,
        double maxBrightness, String faceCascadePath, String algorithmVersion) {
}
