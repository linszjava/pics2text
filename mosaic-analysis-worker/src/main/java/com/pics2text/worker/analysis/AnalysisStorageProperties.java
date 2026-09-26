package com.pics2text.worker.analysis;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "object-storage")
public record AnalysisStorageProperties(URI endpoint, String region, String accessKey,
        String secretKey, String bucket) {
}
