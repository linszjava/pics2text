package com.pics2text.api.application;

import com.pics2text.api.common.ApiException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ImageHeaderInspector {

    public ImageInfo inspect(byte[] bytes) {
        if (isPng(bytes)) return png(bytes);
        if (isJpeg(bytes)) return jpeg(bytes);
        if (isWebp(bytes)) return webp(bytes);
        throw invalid("PHOTO_FORMAT_UNSUPPORTED", "文件魔数不是受支持的 JPEG、PNG 或 WebP 图片");
    }

    private ImageInfo png(byte[] bytes) {
        if (bytes.length < 24 || !"IHDR".equals(ascii(bytes, 12, 4))) {
            throw invalid("PHOTO_HEADER_INVALID", "PNG 文件头不完整");
        }
        return dimensions("image/png", int32(bytes, 16), int32(bytes, 20));
    }

    private ImageInfo jpeg(byte[] bytes) {
        int position = 2;
        while (position + 9 < bytes.length) {
            if ((bytes[position] & 0xff) != 0xff) { position++; continue; }
            int marker = bytes[position + 1] & 0xff;
            position += 2;
            if (marker == 0xd8 || marker == 0xd9 || marker == 0x01) continue;
            if (marker == 0xda) break;
            if (position + 2 > bytes.length) break;
            int length = uint16(bytes, position);
            if (length < 2 || position + length > bytes.length) break;
            if (isStartOfFrame(marker) && length >= 7) {
                return dimensions("image/jpeg", uint16(bytes, position + 5), uint16(bytes, position + 3));
            }
            position += length;
        }
        throw invalid("PHOTO_HEADER_INVALID", "JPEG 文件头中未找到有效尺寸");
    }

    private ImageInfo webp(byte[] bytes) {
        if (bytes.length < 30) throw invalid("PHOTO_HEADER_INVALID", "WebP 文件头不完整");
        String chunk = ascii(bytes, 12, 4);
        if ("VP8X".equals(chunk)) {
            int width = 1 + uint24le(bytes, 24);
            int height = 1 + uint24le(bytes, 27);
            return dimensions("image/webp", width, height);
        }
        if ("VP8L".equals(chunk) && (bytes[20] & 0xff) == 0x2f) {
            int b1 = bytes[21] & 0xff, b2 = bytes[22] & 0xff, b3 = bytes[23] & 0xff, b4 = bytes[24] & 0xff;
            int width = 1 + (b1 | ((b2 & 0x3f) << 8));
            int height = 1 + ((b2 >> 6) | (b3 << 2) | ((b4 & 0x0f) << 10));
            return dimensions("image/webp", width, height);
        }
        if ("VP8 ".equals(chunk)) {
            for (int i = 20; i + 9 < bytes.length; i++) {
                if ((bytes[i + 3] & 0xff) == 0x9d && (bytes[i + 4] & 0xff) == 0x01
                        && (bytes[i + 5] & 0xff) == 0x2a) {
                    int width = little16(bytes, i + 6) & 0x3fff;
                    int height = little16(bytes, i + 8) & 0x3fff;
                    return dimensions("image/webp", width, height);
                }
            }
        }
        throw invalid("PHOTO_HEADER_INVALID", "WebP 文件头中未找到有效尺寸");
    }

    private ImageInfo dimensions(String contentType, int width, int height) {
        if (width <= 0 || height <= 0 || width > 100000 || height > 100000) {
            throw invalid("PHOTO_DIMENSIONS_INVALID", "图片分辨率无效或超出安全上限");
        }
        return new ImageInfo(contentType, width, height);
    }

    private boolean isPng(byte[] b) {
        return b.length >= 8 && (b[0] & 0xff) == 0x89 && ascii(b, 1, 3).equals("PNG")
                && b[4] == 0x0d && b[5] == 0x0a && b[6] == 0x1a && b[7] == 0x0a;
    }
    private boolean isJpeg(byte[] b) { return b.length >= 2 && (b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xd8; }
    private boolean isWebp(byte[] b) { return b.length >= 12 && "RIFF".equals(ascii(b, 0, 4)) && "WEBP".equals(ascii(b, 8, 4)); }
    private boolean isStartOfFrame(int marker) {
        return marker >= 0xc0 && marker <= 0xcf && marker != 0xc4 && marker != 0xc8 && marker != 0xcc;
    }
    private String ascii(byte[] b, int offset, int length) { return new String(b, offset, length, StandardCharsets.US_ASCII); }
    private int uint16(byte[] b, int offset) { return ((b[offset] & 0xff) << 8) | (b[offset + 1] & 0xff); }
    private int little16(byte[] b, int offset) { return (b[offset] & 0xff) | ((b[offset + 1] & 0xff) << 8); }
    private int uint24le(byte[] b, int offset) { return (b[offset] & 0xff) | ((b[offset + 1] & 0xff) << 8) | ((b[offset + 2] & 0xff) << 16); }
    private int int32(byte[] b, int offset) { return ((b[offset] & 0xff) << 24) | ((b[offset + 1] & 0xff) << 16) | ((b[offset + 2] & 0xff) << 8) | (b[offset + 3] & 0xff); }
    private ApiException invalid(String code, String message) { return new ApiException(code, message, HttpStatus.UNPROCESSABLE_ENTITY); }

    public record ImageInfo(String contentType, int width, int height) {}
}
