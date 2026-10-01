package com.pics2text.worker.render;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public class RenderObjectStorage {
    private final S3Client client;
    private final RenderStorageProperties properties;

    public RenderObjectStorage(S3Client client, RenderStorageProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public void put(String key, String contentType, byte[] bytes) {
        client.putObject(PutObjectRequest.builder().bucket(properties.bucket()).key(key)
                        .contentType(contentType).contentLength((long) bytes.length).build(),
                RequestBody.fromBytes(bytes));
    }
}
