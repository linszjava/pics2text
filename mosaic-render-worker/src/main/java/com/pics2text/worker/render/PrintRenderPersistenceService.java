package com.pics2text.worker.render;

import com.pics2text.rendering.infrastructure.persistence.PrintRenderMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class PrintRenderPersistenceService {
    private final PrintRenderMapper mapper;
    PrintRenderPersistenceService(PrintRenderMapper mapper) { this.mapper = mapper; }

    @Transactional
    void complete(UUID id, String outputKey, String outputSha, long outputSize,
            String manifestJson, String manifestHash, String locationsKey, String locationsSha) {
        if (mapper.complete(id, outputKey, outputSha, outputSize, manifestJson, manifestHash,
                locationsKey, locationsSha, Instant.now()) == 0) {
            throw new IllegalStateException("渲染状态已变化，无法保存成品");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void fail(UUID id, String code, String message) {
        String safe = message == null ? code : message.substring(0, Math.min(500, message.length()));
        mapper.fail(id, code, safe, Instant.now());
    }
}
