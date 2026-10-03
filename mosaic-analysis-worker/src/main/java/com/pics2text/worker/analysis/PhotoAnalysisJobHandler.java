package com.pics2text.worker.analysis;

import com.pics2text.job.application.JobExecutionContext;
import com.pics2text.job.application.JobHandler;
import com.pics2text.job.application.PermanentJobException;
import com.pics2text.job.domain.JobType;
import com.pics2text.photoasset.domain.AuthorizationStatus;
import com.pics2text.photoasset.domain.AutoReviewDecision;
import com.pics2text.photoasset.domain.PhotoVariantType;
import com.pics2text.photoasset.domain.ReviewStatus;
import com.pics2text.photoasset.infrastructure.persistence.PhotoAnalysisEntity;
import com.pics2text.photoasset.infrastructure.persistence.PhotoAnalysisMapper;
import com.pics2text.photoasset.infrastructure.persistence.PhotoVariantEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.opencv.core.Mat;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class PhotoAnalysisJobHandler implements JobHandler {
    private final ObjectMapper objectMapper;
    private final PhotoAnalysisMapper mapper;
    private final PhotoAnalysisPersistenceService persistence;
    private final AnalysisObjectStorage storage;
    private final AnalysisProperties properties;
    private final PhotoFeatureExtractor extractor;

    public PhotoAnalysisJobHandler(ObjectMapper objectMapper, PhotoAnalysisMapper mapper,
            PhotoAnalysisPersistenceService persistence, AnalysisObjectStorage storage,
            AnalysisProperties properties) {
        this.objectMapper = objectMapper;
        this.mapper = mapper;
        this.persistence = persistence;
        this.storage = storage;
        this.properties = properties;
        this.extractor = new PhotoFeatureExtractor(properties.faceCascadePath());
    }

    @Override
    public boolean supports(JobType type) {
        return type == JobType.PHOTO_ANALYSIS;
    }

    @Override
    public String execute(JobExecutionContext context) throws Exception {
        UUID photoId = UUID.fromString(objectMapper.readTree(context.payloadJson()).path("photoId").asText());
        var asset = mapper.findAsset(photoId);
        if (asset == null || !context.projectId().equals(asset.getProjectId())) {
            throw new PermanentJobException("待分析照片不存在或不属于当前项目");
        }
        if (!"AVAILABLE".equals(asset.getStatus())) {
            throw new PermanentJobException("照片状态不允许分析：" + asset.getStatus());
        }
        if (mapper.markProcessing(photoId, Instant.now()) == 0) {
            throw new PermanentJobException("照片状态已变化，无法开始分析");
        }
        Mat decoded = null;
        Mat oriented = null;
        try {
            validateDeclaredDimensions(asset.getWidth(), asset.getHeight());
            String sourceKey = asset.getSourceKey() == null ? asset.getOriginalKey() : asset.getSourceKey();
            byte[] source = storage.readBounded(sourceKey, properties.maxSourceBytes());
            context.heartbeat(15);
            int orientation = ImageOrientation.read(source);
            decoded = ImageCodec.decode(source, properties.maxPixels());
            oriented = ImageOrientation.apply(decoded, orientation);
            context.heartbeat(30);
            var features = extractor.extract(oriented);
            Review review = review(features, asset.getAuthorizationStatus());
            SimilarityMatch similarity = findDuplicateGroup(asset.getProjectId(), photoId, features);
            context.heartbeat(55);
            if (context.cancellationRequested()) {
                persistence.markFailed(photoId, "ANALYSIS_CANCELLED", "照片分析已在衍生图生成前取消");
                return objectMapper.writeValueAsString(Map.of("cancelledBeforeDerivatives", true));
            }
            context.enterNonCancellablePhase();

            String base = objectBase(sourceKey);
            String sanitizedKey = base + "/derived/sanitized.jpg";
            String thumbnailKey = base + "/derived/thumbnail-256.webp";
            String previewKey = base + "/derived/preview-1024.webp";
            String analysisKey = base + "/derived/analysis-2048.webp";
            var sanitized = ImageCodec.encodeJpeg(oriented, 94);
            var thumbnail = ImageCodec.resizeWebp(oriented, PhotoVariantType.THUMBNAIL_256.maximumEdge(), 82);
            var preview = ImageCodec.resizeWebp(oriented, PhotoVariantType.PREVIEW_1024.maximumEdge(), 86);
            var analysisImage = ImageCodec.resizeWebp(oriented, PhotoVariantType.ANALYSIS_2048.maximumEdge(), 88);
            storage.put(sanitizedKey, sanitized.contentType(), sanitized.bytes());
            storage.put(thumbnailKey, thumbnail.contentType(), thumbnail.bytes());
            storage.put(previewKey, preview.contentType(), preview.bytes());
            storage.put(analysisKey, analysisImage.contentType(), analysisImage.bytes());
            context.heartbeat(85);

            Instant now = Instant.now();
            PhotoAnalysisEntity analysis = toEntity(photoId, asset.getProjectId(), features, review,
                    similarity.groupId(), orientation, now);
            List<PhotoVariantEntity> variants = List.of(
                    variant(photoId, PhotoVariantType.THUMBNAIL_256, thumbnailKey, thumbnail, now),
                    variant(photoId, PhotoVariantType.PREVIEW_1024, previewKey, preview, now),
                    variant(photoId, PhotoVariantType.ANALYSIS_2048, analysisKey, analysisImage, now));
            persistence.complete(analysis, variants, ReviewStatus.APPROVED.name(), sanitizedKey,
                    thumbnailKey, previewKey, oriented.cols(), oriented.rows(), similarity.photoId());
            return objectMapper.writeValueAsString(Map.of(
                    "photoId", photoId,
                    "algorithmVersion", properties.algorithmVersion(),
                    "autoReviewDecision", review.decision().name(),
                    "duplicateGroupId", similarity.groupId() == null ? "" : similarity.groupId().toString()));
        } catch (UnsafeImageException exception) {
            persistence.markFailed(photoId, "UNSAFE_OR_INVALID_IMAGE", exception.getMessage());
            throw new PermanentJobException(exception.getMessage());
        } catch (PermanentJobException exception) {
            persistence.markFailed(photoId, "PERMANENT_ANALYSIS_FAILURE", exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            persistence.markFailed(photoId, "ANALYSIS_ATTEMPT_FAILED", exception.getMessage());
            throw exception;
        } finally {
            if (decoded != null) decoded.release();
            if (oriented != null) oriented.release();
        }
    }

    private void validateDeclaredDimensions(Integer width, Integer height) {
        if (width == null || height == null || width <= 0 || height <= 0) {
            throw new UnsafeImageException("照片缺少有效尺寸");
        }
        try {
            if (Math.multiplyExact((long) width, height) > properties.maxPixels()) {
                throw new UnsafeImageException("声明的图片像素数量超出分析限制");
            }
        } catch (ArithmeticException exception) {
            throw new UnsafeImageException("声明的图片尺寸非法");
        }
    }

    private Review review(PhotoFeatureExtractor.Features features, String authorizationStatus) {
        List<String> reasons = new ArrayList<>();
        boolean hardReject = false;
        if (features.sharpness() < 10) {
            reasons.add("图片严重模糊");
            hardReject = true;
        } else if (features.sharpness() < properties.minSharpness()) {
            reasons.add("图片清晰度偏低");
        }
        if (features.brightness() < 10 || features.brightness() > 245) {
            reasons.add("图片曝光严重异常");
            hardReject = true;
        } else if (features.brightness() < properties.minBrightness()
                || features.brightness() > properties.maxBrightness()) {
            reasons.add("图片亮度需要人工确认");
        }
        if (!AuthorizationStatus.GRANTED.name().equals(authorizationStatus)) {
            reasons.add("照片授权尚未确认");
        }
        if (hardReject) return new Review(AutoReviewDecision.AUTO_REJECTED, ReviewStatus.REJECTED, reasons);
        if (!reasons.isEmpty()) return new Review(AutoReviewDecision.NEEDS_REVIEW, ReviewStatus.PENDING, reasons);
        return new Review(AutoReviewDecision.AUTO_APPROVED, ReviewStatus.APPROVED, List.of("自动质量检查通过"));
    }

    private SimilarityMatch findDuplicateGroup(UUID projectId, UUID photoId, PhotoFeatureExtractor.Features features) {
        for (PhotoAnalysisEntity candidate : mapper.findHashCandidates(projectId, photoId, properties.candidateLimit())) {
            if (PhotoFeatureExtractor.hammingDistance(features.dHash(), candidate.getDHash())
                    <= properties.dHashDistance()
                    && PhotoFeatureExtractor.hammingDistance(features.pHash(), candidate.getPHash())
                    <= properties.pHashDistance()) {
                UUID group = candidate.getDuplicateGroupId() == null ? UUID.randomUUID() : candidate.getDuplicateGroupId();
                return new SimilarityMatch(group, candidate.getPhotoId());
            }
        }
        return new SimilarityMatch(null, null);
    }

    private PhotoAnalysisEntity toEntity(UUID photoId, UUID projectId,
            PhotoFeatureExtractor.Features features, Review review, UUID duplicateGroup,
            int orientation, Instant now) throws Exception {
        PhotoAnalysisEntity entity = new PhotoAnalysisEntity();
        entity.setPhotoId(photoId);
        entity.setProjectId(projectId);
        entity.setAlgorithmVersion(properties.algorithmVersion());
        entity.setSharpness(features.sharpness());
        entity.setBrightness(features.brightness());
        entity.setContrast(features.contrast());
        entity.setLabL(features.labL());
        entity.setLabA(features.labA());
        entity.setLabB(features.labB());
        entity.setDHash(features.dHash());
        entity.setPHash(features.pHash());
        entity.setFaceCount(features.faces().size());
        entity.setFacesJson(objectMapper.writeValueAsString(features.faces()));
        setSubject(entity, features.subject());
        setCrop(entity, features.crop());
        entity.setDuplicateGroupId(duplicateGroup);
        entity.setAutoReviewDecision(review.decision().name());
        entity.setReviewReasonsJson(objectMapper.writeValueAsString(review.reasons()));
        entity.setOrientationApplied(orientation);
        entity.setAnalyzedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private void setSubject(PhotoAnalysisEntity entity, PhotoFeatureExtractor.Box box) {
        entity.setSubjectX(box.x()); entity.setSubjectY(box.y());
        entity.setSubjectWidth(box.width()); entity.setSubjectHeight(box.height());
    }

    private void setCrop(PhotoAnalysisEntity entity, PhotoFeatureExtractor.Box box) {
        entity.setCropX(box.x()); entity.setCropY(box.y());
        entity.setCropWidth(box.width()); entity.setCropHeight(box.height());
    }

    private PhotoVariantEntity variant(UUID photoId, PhotoVariantType type, String key,
            ImageCodec.EncodedImage image, Instant now) {
        return new PhotoVariantEntity(photoId, type.name(), key, image.width(), image.height(),
                image.bytes().length, image.contentType(), now);
    }

    private String objectBase(String key) {
        int original = key.indexOf("/original/");
        if (original > 0) return key.substring(0, original);
        int slash = key.lastIndexOf('/');
        return slash > 0 ? key.substring(0, slash) : key;
    }

    private record Review(AutoReviewDecision decision, ReviewStatus reviewStatus, List<String> reasons) { }
    private record SimilarityMatch(UUID groupId, UUID photoId) { }
}
