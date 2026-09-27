package com.pics2text.worker.analysis;

import com.pics2text.layout.application.TextMaskResult;
import com.pics2text.layout.infrastructure.persistence.TextMaskMapper;
import com.pics2text.layout.infrastructure.persistence.TextMaskSlotEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
class TextMaskPersistenceService {
    private static final int BATCH_SIZE = 500;
    private final TextMaskMapper mapper;
    private final ObjectMapper objectMapper;

    TextMaskPersistenceService(TextMaskMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    void complete(UUID maskId, TextMaskResult result, String maskKey, String debugKey) {
        mapper.deleteSlots(maskId);
        List<TextMaskSlotEntity> slots = result.slots().stream()
                .map(slot -> new TextMaskSlotEntity(maskId, slot.index(), slot.row(), slot.column(),
                        slot.x(), slot.y(), slot.width(), slot.height(), slot.coverage(), slot.edge()))
                .toList();
        for (int start = 0; start < slots.size(); start += BATCH_SIZE) {
            mapper.insertSlots(slots.subList(start, Math.min(start + BATCH_SIZE, slots.size())));
        }
        try {
            int changed = mapper.markReady(maskId,
                    com.pics2text.layout.application.TextMaskGenerator.ALGORITHM_VERSION,
                    maskKey, debugKey, result.boundsX(), result.boundsY(), result.boundsWidth(),
                    result.boundsHeight(), result.estimatedStrokeWidth(), result.averageCoverage(),
                    result.slots().size(), objectMapper.writeValueAsString(result.warnings()), Instant.now());
            if (changed == 0) throw new IllegalStateException("文字蒙版状态已变化，无法保存结果");
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法序列化文字蒙版警告", exception);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void markFailed(UUID maskId, String code, String message) {
        String safe = message == null ? code : message.substring(0, Math.min(500, message.length()));
        mapper.markFailed(maskId, code, safe, Instant.now());
    }
}
