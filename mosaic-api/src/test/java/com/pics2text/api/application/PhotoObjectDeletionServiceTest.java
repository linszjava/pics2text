package com.pics2text.api.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pics2text.api.infrastructure.persistence.PhotoAssetEntity;
import com.pics2text.api.infrastructure.storage.ObjectStorageGateway;
import com.pics2text.photoasset.infrastructure.persistence.PhotoAnalysisMapper;
import com.pics2text.photoasset.infrastructure.persistence.PhotoVariantEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhotoObjectDeletionServiceTest {
    @Mock ObjectStorageGateway storage;
    @Mock PhotoAnalysisMapper analysisMapper;
    private PhotoObjectDeletionService service;

    @BeforeEach
    void setUp() {
        service = new PhotoObjectDeletionService(storage, analysisMapper);
    }

    @Test
    void deletesEveryDistinctObjectIncludingSourceAndVariants() {
        UUID photoId = UUID.randomUUID();
        PhotoAssetEntity asset = asset(photoId, "AVAILABLE");
        asset.setOriginalKey("photos/original");
        asset.setSourceKey("photos/source");
        asset.setThumbnailKey("photos/thumb");
        asset.setPreviewKey("photos/preview");
        when(analysisMapper.listVariants(photoId)).thenReturn(List.of(
                variant(photoId, "photos/thumb"), variant(photoId, "photos/variant")));

        service.deleteAll(asset);

        verify(storage).delete("photos/original");
        verify(storage).delete("photos/source");
        verify(storage).delete("photos/thumb");
        verify(storage).delete("photos/preview");
        verify(storage).delete("photos/variant");
    }

    @Test
    void abortsActiveMultipartWithoutLookingUpVariants() {
        UUID photoId = UUID.randomUUID();
        PhotoAssetEntity asset = asset(photoId, "UPLOADING");
        asset.setOriginalKey("photos/original");
        asset.setMultipartUploadId("upload-1");

        service.deleteAll(asset);

        verify(storage).abortMultipart("photos/original", "upload-1");
        verify(storage, never()).delete("photos/original");
        verify(analysisMapper, never()).listVariants(photoId);
    }

    @Test
    void keepsExceptionForRetry() {
        UUID photoId = UUID.randomUUID();
        PhotoAssetEntity asset = asset(photoId, "WITHDRAWN");
        asset.setOriginalKey("photos/original");
        when(analysisMapper.listVariants(photoId)).thenReturn(List.of());
        RuntimeException failure = new RuntimeException("storage unavailable");
        org.mockito.Mockito.doThrow(failure).when(storage).delete("photos/original");

        assertThrows(RuntimeException.class, () -> service.deleteAll(asset));

    }

    private PhotoAssetEntity asset(UUID id, String status) {
        PhotoAssetEntity asset = new PhotoAssetEntity();
        asset.setId(id);
        asset.setStatus(status);
        return asset;
    }

    private PhotoVariantEntity variant(UUID photoId, String key) {
        return new PhotoVariantEntity(photoId, "TEST", key, 10, 10, 100,
                "image/jpeg", Instant.parse("2026-01-01T00:00:00Z"));
    }
}
