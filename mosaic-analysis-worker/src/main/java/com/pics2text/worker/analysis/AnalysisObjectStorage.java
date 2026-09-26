package com.pics2text.worker.analysis;

import java.io.IOException;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public class AnalysisObjectStorage {
    private final S3Client client;
    private final AnalysisStorageProperties properties;

    public AnalysisObjectStorage(S3Client client, AnalysisStorageProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public byte[] readBounded(String key, long maxBytes) throws IOException {
        long size = client.headObject(HeadObjectRequest.builder()
                .bucket(properties.bucket()).key(key).build()).contentLength();
        if (size <= 0 || size > maxBytes) {
            throw new UnsafeImageException("源文件大小超出分析限制：" + size);
        }
        try (var input = client.getObject(GetObjectRequest.builder()
                .bucket(properties.bucket()).key(key).build())) {
            byte[] bytes = input.readNBytes(Math.toIntExact(maxBytes + 1));
            if (bytes.length > maxBytes) {
                throw new UnsafeImageException("读取内容超过分析限制");
            }
            return bytes;
        }
    }

    public void put(String key, String contentType, byte[] bytes) {
        client.putObject(PutObjectRequest.builder().bucket(properties.bucket()).key(key)
                        .contentType(contentType).contentLength((long) bytes.length).build(),
                RequestBody.fromBytes(bytes));
    }
}
