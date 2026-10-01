package com.pics2text.worker.render;

import com.pics2text.layout.infrastructure.persistence.LayoutEditorMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class LayoutPreviewPersistenceService {
    private final LayoutEditorMapper mapper;
    LayoutPreviewPersistenceService(LayoutEditorMapper mapper) { this.mapper = mapper; }

    @Transactional
    void complete(UUID previewId, String key, int width, int height, int count) {
        if (mapper.markPreviewReady(previewId, key, width, height, count, Instant.now()) == 0) {
            throw new IllegalStateException("布局预览状态已变化，无法保存结果");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void markFailed(UUID previewId, String code, String message) {
        String safe = message == null ? code : message.substring(0, Math.min(500, message.length()));
        mapper.markPreviewFailed(previewId, code, safe, Instant.now());
    }
}
