package com.pics2text.api.application;

import com.pics2text.api.common.ApiException;
import com.pics2text.api.config.ObjectStorageProperties;
import com.pics2text.api.infrastructure.storage.ObjectStorageGateway;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PhotoObjectVerifier {
    private final ObjectStorageGateway storage;
    private final ObjectStorageProperties properties;
    private final ImageHeaderInspector imageHeaderInspector;

    public PhotoObjectVerifier(ObjectStorageGateway storage, ObjectStorageProperties properties,
            ImageHeaderInspector imageHeaderInspector) {
        this.storage = storage;
        this.properties = properties;
        this.imageHeaderInspector = imageHeaderInspector;
    }

    public VerificationResult verify(String key, long expectedSize, String expectedSha256) {
        var metadata = storage.head(key);
        if (metadata.contentLength() != expectedSize) {
            throw invalid("PHOTO_SIZE_MISMATCH", "对象大小与上传声明不一致");
        }
        try (InputStream input = storage.open(key)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ByteArrayOutputStream header = new ByteArrayOutputStream();
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) continue;
                digest.update(buffer, 0, read);
                total += read;
                int remaining = properties.verificationBufferSize() - header.size();
                if (remaining > 0) header.write(buffer, 0, Math.min(remaining, read));
                if (total > expectedSize) throw invalid("PHOTO_SIZE_MISMATCH", "对象实际内容超过上传声明");
            }
            if (total != expectedSize) throw invalid("PHOTO_SIZE_MISMATCH", "对象实际内容与上传声明不一致");
            String actualSha256 = HexFormat.of().formatHex(digest.digest());
            if (!actualSha256.equals(expectedSha256)) {
                throw invalid("PHOTO_SHA256_MISMATCH", "对象 SHA-256 与上传声明不一致");
            }
            var image = imageHeaderInspector.inspect(header.toByteArray());
            return new VerificationResult(image.contentType(), image.width(), image.height());
        } catch (IOException exception) {
            throw new ApiException("PHOTO_STORAGE_READ_FAILED", "读取上传对象失败", HttpStatus.BAD_GATEWAY, exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private ApiException invalid(String code, String message) {
        return new ApiException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public record VerificationResult(String contentType, int width, int height) {}
}
