package com.pics2text.api.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.pics2text.api.common.ApiException;
import com.pics2text.api.config.ObjectStorageProperties;
import com.pics2text.api.infrastructure.storage.ObjectStorageGateway;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhotoObjectVerifierTest {
    @Mock ObjectStorageGateway storage;
    private PhotoObjectVerifier verifier;
    private byte[] png;

    @BeforeEach
    void setUp() {
        var properties = new ObjectStorageProperties(URI.create("http://localhost"), URI.create("http://localhost"),
                "us-east-1", "key", "secret", "bucket", Duration.ofMinutes(15), Duration.ofHours(24),
                10, 8, 1024);
        verifier = new PhotoObjectVerifier(storage, properties, new ImageHeaderInspector());
        png = new byte[24];
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a};
        System.arraycopy(signature, 0, png, 0, 8);
        png[12] = 'I'; png[13] = 'H'; png[14] = 'D'; png[15] = 'R';
        png[19] = 10; png[23] = 20;
    }

    @Test
    void verifiesActualHashMagicAndDimensions() throws Exception {
        when(storage.head("key")).thenReturn(new ObjectStorageGateway.StoredObject(png.length, "image/png"));
        when(storage.open("key")).thenReturn(new ByteArrayInputStream(png));
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png));
        var result = verifier.verify("key", png.length, hash);
        assertEquals(10, result.width());
        assertEquals(20, result.height());
    }

    @Test
    void rejectsForgedDeclaredHash() {
        when(storage.head("key")).thenReturn(new ObjectStorageGateway.StoredObject(png.length, "image/png"));
        when(storage.open("key")).thenReturn(new ByteArrayInputStream(png));
        ApiException exception = assertThrows(ApiException.class,
                () -> verifier.verify("key", png.length, "0".repeat(64)));
        assertEquals("PHOTO_SHA256_MISMATCH", exception.code());
    }
}
