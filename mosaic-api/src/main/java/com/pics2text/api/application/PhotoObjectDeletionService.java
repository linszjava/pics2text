package com.pics2text.api.application;

import com.pics2text.api.infrastructure.persistence.PhotoAssetEntity;
import com.pics2text.api.infrastructure.storage.ObjectStorageGateway;
import com.pics2text.photoasset.domain.PhotoAssetStatus;
import com.pics2text.photoasset.infrastructure.persistence.PhotoAnalysisMapper;
import com.pics2text.photoasset.infrastructure.persistence.PhotoVariantEntity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PhotoObjectDeletionService {
    private final ObjectStorageGateway storage;
    private final PhotoAnalysisMapper analysisMapper;
    private final Counter successes;
    private final Counter failures;
    private final Timer duration;

    public PhotoObjectDeletionService(ObjectStorageGateway storage, PhotoAnalysisMapper analysisMapper,
            MeterRegistry meterRegistry) {
        this.storage = storage;
        this.analysisMapper = analysisMapper;
        this.successes = Counter.builder("pics2text.photo.cleanup.operations")
                .tag("result", "success")
                .description("Number of photo object cleanup operations")
                .register(meterRegistry);
        this.failures = Counter.builder("pics2text.photo.cleanup.operations")
                .tag("result", "failure")
                .description("Number of photo object cleanup operations")
                .register(meterRegistry);
        this.duration = Timer.builder("pics2text.photo.cleanup.duration")
                .description("Time spent deleting all objects belonging to a photo")
                .register(meterRegistry);
    }

    public void deleteAll(PhotoAssetEntity asset) {
        Timer.Sample sample = Timer.start();
        try {
            if (isActiveMultipart(asset)) {
                storage.abortMultipart(asset.getOriginalKey(), asset.getMultipartUploadId());
            } else {
                objectKeys(asset).forEach(storage::delete);
            }
            successes.increment();
        } catch (RuntimeException exception) {
            failures.increment();
            throw exception;
        } finally {
            sample.stop(duration);
        }
    }

    private boolean isActiveMultipart(PhotoAssetEntity asset) {
        return asset.getMultipartUploadId() != null
                && PhotoAssetStatus.UPLOADING.name().equals(asset.getStatus());
    }

    private Set<String> objectKeys(PhotoAssetEntity asset) {
        Set<String> keys = new LinkedHashSet<>();
        add(keys, asset.getOriginalKey());
        add(keys, asset.getSourceKey());
        add(keys, asset.getThumbnailKey());
        add(keys, asset.getPreviewKey());
        analysisMapper.listVariants(asset.getId()).stream()
                .map(PhotoVariantEntity::objectKey)
                .forEach(key -> add(keys, key));
        return keys;
    }

    private void add(Set<String> keys, String key) {
        if (key != null && !key.isBlank()) keys.add(key);
    }
}
