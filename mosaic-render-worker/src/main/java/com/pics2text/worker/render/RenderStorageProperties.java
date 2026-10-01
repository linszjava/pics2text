package com.pics2text.worker.render;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "object-storage")
public record RenderStorageProperties(URI endpoint, String region, String accessKey,
        String secretKey, String bucket) { }
