package com.pics2text.worker.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public class RenderObjectStorage {
    private final S3Client client;
    private final RenderStorageProperties properties;
    private final MeterRegistry meterRegistry;

    public RenderObjectStorage(S3Client client, RenderStorageProperties properties, MeterRegistry meterRegistry) {
        this.client = client;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        for (String operation : new String[] {"put", "download"}) {
            meterRegistry.counter("pics2text.render.object.storage.operations",
                    "operation", operation, "result", "success");
            meterRegistry.counter("pics2text.render.object.storage.operations",
                    "operation", operation, "result", "failure");
            operationTimer(operation);
        }
    }

    public void put(String key, String contentType, byte[] bytes) throws IOException {
        record("put", () -> client.putObject(PutObjectRequest.builder().bucket(properties.bucket()).key(key)
                                .contentType(contentType).contentLength((long) bytes.length).build(),
                        RequestBody.fromBytes(bytes)));
    }

    public void put(String key, String contentType, Path path) throws IOException {
        record("put", () -> {
            long size = Files.size(path);
            client.putObject(PutObjectRequest.builder().bucket(properties.bucket()).key(key)
                            .contentType(contentType).contentLength(size).build(), RequestBody.fromFile(path));
        });
    }

    public void download(String key, Path target, long maxBytes) throws IOException {
        record("download", () -> {
            long size = client.headObject(HeadObjectRequest.builder().bucket(properties.bucket()).key(key).build())
                    .contentLength();
            if (size <= 0 || size > maxBytes) throw new IOException("渲染源文件大小超出限制：" + size);
            Files.createDirectories(target.getParent());
            client.getObject(GetObjectRequest.builder().bucket(properties.bucket()).key(key).build(),
                    ResponseTransformer.toFile(target));
            if (Files.size(target) != size) throw new IOException("渲染源文件下载不完整");
        });
    }

    private void record(String operation, IoOperation action) throws IOException {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            action.run();
            meterRegistry.counter("pics2text.render.object.storage.operations",
                    "operation", operation, "result", "success").increment();
        } catch (IOException | RuntimeException exception) {
            meterRegistry.counter("pics2text.render.object.storage.operations",
                    "operation", operation, "result", "failure").increment();
            throw exception;
        } finally {
            sample.stop(operationTimer(operation));
        }
    }

    private Timer operationTimer(String operation) {
        return Timer.builder("pics2text.render.object.storage.duration")
                .description("渲染 Worker 对象存储操作耗时")
                .tag("operation", operation)
                .register(meterRegistry);
    }

    @FunctionalInterface
    private interface IoOperation { void run() throws IOException; }
}
