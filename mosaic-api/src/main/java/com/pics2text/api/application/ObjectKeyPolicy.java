package com.pics2text.api.application;

import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ObjectKeyPolicy {
    public Keys keys(UUID projectId, UUID photoId, String contentType) {
        String extension = switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "bin";
        };
        String base = "projects/" + projectId + "/photos/" + photoId;
        return new Keys(base + "/original/source." + extension,
                base + "/derived/thumbnail.webp", base + "/derived/preview.webp");
    }

    public record Keys(String original, String thumbnail, String preview) {}
}
