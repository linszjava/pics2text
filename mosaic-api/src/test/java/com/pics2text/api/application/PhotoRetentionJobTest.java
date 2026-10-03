package com.pics2text.api.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pics2text.api.infrastructure.persistence.PhotoAssetEntity;
import com.pics2text.api.infrastructure.persistence.PhotoAssetMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhotoRetentionJobTest {
    @Mock PhotoAssetMapper mapper;
    @Mock PhotoObjectDeletionService deletionService;

    @Test
    void marksExpiredUploadAndWithdrawnPhotoOnlyAfterObjectsAreDeleted() {
        PhotoAssetEntity upload = asset("UPLOADING");
        PhotoAssetEntity withdrawn = asset("WITHDRAWN");
        when(mapper.findExpiredUploads(any(Instant.class), org.mockito.ArgumentMatchers.eq(100)))
                .thenReturn(List.of(upload));
        when(mapper.findExpiredRetention(any(Instant.class), org.mockito.ArgumentMatchers.eq(100)))
                .thenReturn(List.of(withdrawn));

        new PhotoRetentionJob(mapper, deletionService).cleanup();

        verify(deletionService).deleteAll(upload);
        verify(deletionService).deleteAll(withdrawn);
        verify(mapper).updateFailure(org.mockito.ArgumentMatchers.eq(upload.getId()),
                org.mockito.ArgumentMatchers.eq("FAILED"), org.mockito.ArgumentMatchers.eq("UPLOAD_EXPIRED"),
                org.mockito.ArgumentMatchers.anyString(), any(Instant.class));
        verify(mapper).markObjectsDeleted(org.mockito.ArgumentMatchers.eq(upload.getId()), any(Instant.class));
        verify(mapper).markDeleted(org.mockito.ArgumentMatchers.eq(withdrawn.getId()), any(Instant.class),
                org.mockito.ArgumentMatchers.eq("RETENTION_EXPIRED"), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void leavesDatabaseStateUntouchedWhenObjectDeletionFails() {
        PhotoAssetEntity withdrawn = asset("WITHDRAWN");
        when(mapper.findExpiredUploads(any(Instant.class), org.mockito.ArgumentMatchers.eq(100)))
                .thenReturn(List.of());
        when(mapper.findExpiredRetention(any(Instant.class), org.mockito.ArgumentMatchers.eq(100)))
                .thenReturn(List.of(withdrawn));
        org.mockito.Mockito.doThrow(new RuntimeException("storage unavailable"))
                .when(deletionService).deleteAll(withdrawn);

        new PhotoRetentionJob(mapper, deletionService).cleanup();

        verify(mapper, never()).markDeleted(org.mockito.ArgumentMatchers.any(), any(Instant.class),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    private PhotoAssetEntity asset(String status) {
        PhotoAssetEntity asset = new PhotoAssetEntity();
        asset.setId(UUID.randomUUID());
        asset.setStatus(status);
        return asset;
    }
}
