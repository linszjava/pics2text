package com.pics2text.api.application;

import com.pics2text.api.infrastructure.persistence.PhotoAssetEntity;
import com.pics2text.api.infrastructure.storage.ObjectStorageGateway;
import com.pics2text.photoasset.domain.PhotoAssetStatus;
import com.pics2text.photoasset.infrastructure.persistence.PhotoAnalysisMapper;
import com.pics2text.photoasset.infrastructure.persistence.PhotoVariantEntity;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PhotoObjectDeletionService {
    private final ObjectStorageGateway storage;
    private final PhotoAnalysisMapper analysisMapper;

    public PhotoObjectDeletionService(ObjectStorageGateway storage, PhotoAnalysisMapper analysisMapper) {
        this.storage = storage;
        this.analysisMapper = analysisMapper;
    }

    public void deleteAll(PhotoAssetEntity asset) {
        if (isActiveMultipart(asset)) {
            storage.abortMultipart(asset.getOriginalKey(), asset.getMultipartUploadId());
        } else {
            objectKeys(asset).forEach(storage::delete);
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
