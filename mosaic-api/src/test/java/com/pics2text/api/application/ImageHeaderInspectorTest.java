package com.pics2text.api.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pics2text.api.common.ApiException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ImageHeaderInspectorTest {
    private final ImageHeaderInspector inspector = new ImageHeaderInspector();

    @Test
    void readsPngDimensionsFromMagicHeader() {
        byte[] bytes = new byte[24];
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a};
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        System.arraycopy("IHDR".getBytes(StandardCharsets.US_ASCII), 0, bytes, 12, 4);
        putInt(bytes, 16, 1920);
        putInt(bytes, 20, 1080);

        var info = inspector.inspect(bytes);
        assertEquals("image/png", info.contentType());
        assertEquals(1920, info.width());
        assertEquals(1080, info.height());
    }

    @Test
    void readsJpegDimensionsFromStartOfFrame() {
        byte[] bytes = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xc0,
                0, 17, 8, 4, 56, 7, (byte) 128, 3, 1, 0x11, 0, 2, 0x11, 0, 3, 0x11, 0};
        var info = inspector.inspect(bytes);
        assertEquals("image/jpeg", info.contentType());
        assertEquals(1920, info.width());
        assertEquals(1080, info.height());
    }

    @Test
    void rejectsExtensionOnlyDisguisedFile() {
        ApiException exception = assertThrows(ApiException.class,
                () -> inspector.inspect("not an image".getBytes(StandardCharsets.UTF_8)));
        assertEquals("PHOTO_FORMAT_UNSUPPORTED", exception.code());
    }

    private void putInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }
}
