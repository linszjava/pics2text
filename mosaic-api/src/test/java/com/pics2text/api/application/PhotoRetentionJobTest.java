package com.pics2text.api.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pics2text.api.infrastructure.persistence.PhotoAssetEntity;
import com.pics2text.api.infrastructure.persistence.PhotoAssetMapper;
import java.time.Duration;
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
        when(mapper.findExpiredUploads(any(Instant.class), any(Instant.class),
                org.mockito.ArgumentMatchers.eq(100)))
                .thenReturn(List.of(upload));
        when(mapper.findExpiredRetention(any(Instant.class), org.mockito.ArgumentMatchers.eq(100)))
                .thenReturn(List.of(withdrawn));
        when(mapper.expireUploading(eq(upload.getId()), any(Instant.class))).thenReturn(1);

        new PhotoRetentionJob(mapper, deletionService, Duration.ofMinutes(30)).cleanup();

        verify(deletionService).deleteAll(upload);
        verify(deletionService).deleteAll(withdrawn);
        verify(mapper).expireUploading(eq(upload.getId()), any(Instant.class));
        verify(mapper).markObjectsDeleted(org.mockito.ArgumentMatchers.eq(upload.getId()), any(Instant.class));
        verify(mapper).markDeleted(org.mockito.ArgumentMatchers.eq(withdrawn.getId()), any(Instant.class),
                org.mockito.ArgumentMatchers.eq("RETENTION_EXPIRED"), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void leavesDatabaseStateUntouchedWhenObjectDeletionFails() {
        PhotoAssetEntity withdrawn = asset("WITHDRAWN");
        when(mapper.findExpiredUploads(any(Instant.class), any(Instant.class),
                org.mockito.ArgumentMatchers.eq(100)))
                .thenReturn(List.of());
        when(mapper.findExpiredRetention(any(Instant.class), org.mockito.ArgumentMatchers.eq(100)))
                .thenReturn(List.of(withdrawn));
        org.mockito.Mockito.doThrow(new RuntimeException("storage unavailable"))
                .when(deletionService).deleteAll(withdrawn);

        new PhotoRetentionJob(mapper, deletionService, Duration.ofMinutes(30)).cleanup();

        verify(mapper, never()).markDeleted(org.mockito.ArgumentMatchers.any(), any(Instant.class),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void retriesCleanupForDuplicateObjectsLeftByAnInterruptedCompletion() {
        PhotoAssetEntity duplicate = asset("DUPLICATE");
        when(mapper.findExpiredUploads(any(Instant.class), any(Instant.class),
                org.mockito.ArgumentMatchers.eq(100)))
                .thenReturn(List.of(duplicate));
        when(mapper.findExpiredRetention(any(Instant.class), org.mockito.ArgumentMatchers.eq(100)))
                .thenReturn(List.of());

        new PhotoRetentionJob(mapper, deletionService, Duration.ofMinutes(30)).cleanup();

        verify(deletionService).deleteAll(duplicate);
        verify(mapper).markObjectsDeleted(org.mockito.ArgumentMatchers.eq(duplicate.getId()), any(Instant.class));
        verify(mapper, never()).updateFailure(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), any(Instant.class));
    }

    @Test
    void doesNotDeleteAStaleVerificationAfterAnotherRequestHasReclaimedIt() {
        PhotoAssetEntity verifying = asset("VERIFYING");
        verifying.setVerificationToken(UUID.randomUUID());
        when(mapper.findExpiredUploads(any(Instant.class), any(Instant.class), eq(100)))
                .thenReturn(List.of(verifying));
        when(mapper.findExpiredRetention(any(Instant.class), eq(100))).thenReturn(List.of());
        when(mapper.failVerification(eq(verifying.getId()), eq(verifying.getVerificationToken()),
                eq("UPLOAD_EXPIRED"), any(String.class), any(Instant.class))).thenReturn(0);

        new PhotoRetentionJob(mapper, deletionService, Duration.ofMinutes(30)).cleanup();

        verify(deletionService, never()).deleteAll(verifying);
        verify(mapper, never()).markObjectsDeleted(eq(verifying.getId()), any(Instant.class));
    }

    private PhotoAssetEntity asset(String status) {
        PhotoAssetEntity asset = new PhotoAssetEntity();
        asset.setId(UUID.randomUUID());
        asset.setStatus(status);
        return asset;
    }
}
