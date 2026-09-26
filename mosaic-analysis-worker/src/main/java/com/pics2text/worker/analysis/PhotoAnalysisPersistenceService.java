package com.pics2text.worker.analysis;

import com.pics2text.photoasset.infrastructure.persistence.PhotoAnalysisEntity;
import com.pics2text.photoasset.infrastructure.persistence.PhotoAnalysisMapper;
import com.pics2text.photoasset.infrastructure.persistence.PhotoVariantEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class PhotoAnalysisPersistenceService {
    private final PhotoAnalysisMapper mapper;

    PhotoAnalysisPersistenceService(PhotoAnalysisMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    void complete(PhotoAnalysisEntity analysis, List<PhotoVariantEntity> variants,
            String reviewStatus, String sanitizedKey, String thumbnailKey, String previewKey,
            int width, int height, UUID matchedPhotoId) {
        Instant now = Instant.now();
        mapper.upsertAnalysis(analysis);
        variants.forEach(mapper::upsertVariant);
        if (analysis.getDuplicateGroupId() != null && matchedPhotoId != null) {
            mapper.assignDuplicateGroup(matchedPhotoId, analysis.getDuplicateGroupId(), now);
        }
        if (mapper.markSucceeded(analysis.getPhotoId(), reviewStatus, sanitizedKey, thumbnailKey,
                previewKey, width, height, now) == 0) {
            throw new IllegalStateException("照片状态已变化，无法保存分析结果");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void markFailed(UUID photoId, String code, String message) {
        String safe = message == null ? code : message.substring(0, Math.min(message.length(), 500));
        mapper.markFailed(photoId, code, safe, Instant.now());
    }
}
