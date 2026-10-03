package com.pics2text.worker.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class RenderObjectStorageTest {
    @Mock S3Client client;
    private SimpleMeterRegistry registry;
    private RenderObjectStorage storage;

    @BeforeEach void setUp() {
        registry = new SimpleMeterRegistry();
        var properties = new RenderStorageProperties(URI.create("http://localhost"), "us-east-1",
                "access", "secret", "bucket");
        storage = new RenderObjectStorage(client, properties, registry);
    }

    @Test void recordsSuccessfulPut() throws Exception {
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        storage.put("key", "application/octet-stream", new byte[] {1});

        assertEquals(1, registry.get("pics2text.render.object.storage.operations")
                .tags("operation", "put", "result", "success").counter().count());
        assertEquals(1, registry.get("pics2text.render.object.storage.duration")
                .tag("operation", "put").timer().count());
    }

    @Test void recordsFailedPutAndRethrows() {
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("unavailable").statusCode(503).build());

        assertThrows(S3Exception.class,
                () -> storage.put("key", "application/octet-stream", new byte[] {1}));
        assertEquals(1, registry.get("pics2text.render.object.storage.operations")
                .tags("operation", "put", "result", "failure").counter().count());
    }
}
