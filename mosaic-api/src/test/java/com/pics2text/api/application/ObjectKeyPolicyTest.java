package com.pics2text.api.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ObjectKeyPolicyTest {
    @Test
    void createsIsolatedAndPredictableObjectKeys() {
        UUID project = UUID.randomUUID();
        UUID photo = UUID.randomUUID();
        var keys = new ObjectKeyPolicy().keys(project, photo, "image/jpeg");
        String base = "projects/" + project + "/photos/" + photo;
        assertEquals(base + "/original/source.jpg", keys.original());
        assertEquals(base + "/derived/thumbnail.webp", keys.thumbnail());
        assertEquals(base + "/derived/preview.webp", keys.preview());
    }
}
