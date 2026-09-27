package com.pics2text.worker.analysis;

import com.pics2text.layout.application.AutoLayoutGenerator;
import com.pics2text.layout.application.AutoLayoutResult;
import com.pics2text.layout.infrastructure.persistence.AutoLayoutAssignmentEntity;
import com.pics2text.layout.infrastructure.persistence.AutoLayoutMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class AutoLayoutPersistenceService {
    private static final int BATCH_SIZE = 500;
    private final AutoLayoutMapper mapper;

    AutoLayoutPersistenceService(AutoLayoutMapper mapper) { this.mapper = mapper; }

    @Transactional
    void complete(UUID layoutId, int candidateCount, String photoSetHash, long runtimeMillis,
            AutoLayoutResult result) {
        mapper.deleteAssignments(layoutId);
        List<AutoLayoutAssignmentEntity> assignments = result.assignments().stream()
                .map(value -> new AutoLayoutAssignmentEntity(layoutId, value.slotIndex(), value.photoId(),
                        value.totalScore(), value.qualityScore(), value.brightnessScore(), value.colorScore(),
                        value.cropScore(), value.duplicateScore(), value.adjacentScore(), value.cropX(),
                        value.cropY(), value.cropWidth(), value.cropHeight())).toList();
        for (int start = 0; start < assignments.size(); start += BATCH_SIZE) {
            mapper.insertAssignments(assignments.subList(start, Math.min(start + BATCH_SIZE, assignments.size())));
        }
        int changed = mapper.markReady(layoutId, AutoLayoutGenerator.ALGORITHM_VERSION, photoSetHash,
                candidateCount, assignments.size(), result.initialAverageScore(), result.finalAverageScore(),
                result.averageBrightnessScore(), result.averageColorScore(), result.averageCropScore(),
                result.averageAdjacentScore(), result.uniquePhotoCount(), result.duplicateGroupReuseCount(),
                result.candidateEvaluations(), result.estimatedMemoryBytes(), runtimeMillis, Instant.now());
        if (changed == 0) throw new IllegalStateException("自动布局状态已变化，无法保存结果");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void markFailed(UUID layoutId, String code, String message) {
        String safe = message == null ? code : message.substring(0, Math.min(500, message.length()));
        mapper.markFailed(layoutId, code, safe, Instant.now());
    }
}
