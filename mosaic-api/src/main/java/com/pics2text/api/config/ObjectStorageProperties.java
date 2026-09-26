package com.pics2text.api.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "object-storage")
public record ObjectStorageProperties(
        URI endpoint,
        URI publicEndpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket,
        Duration presignDuration,
        Duration uploadTtl,
        long multipartThreshold,
        long multipartPartSize,
        int verificationBufferSize) {
}
