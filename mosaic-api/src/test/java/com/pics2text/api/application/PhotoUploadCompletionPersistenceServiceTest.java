package com.pics2text.api.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pics2text.api.infrastructure.persistence.PhotoAssetEntity;
import com.pics2text.api.infrastructure.persistence.PhotoAssetMapper;
import com.pics2text.job.application.JobCoordinator;
import com.pics2text.photoasset.domain.PhotoAssetStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class PhotoUploadCompletionPersistenceServiceTest {
    @Mock PhotoAssetMapper mapper;
    @Mock JobCoordinator jobs;
    @Mock ObjectMapper objectMapper;
    @Mock AuditService audit;

    @Test
    void serializesProjectDuplicateDecisionAndEnqueuesAnalysisOnce() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        PhotoAssetEntity asset = verifying(projectId, photoId, token);
        var verification = new PhotoObjectVerifier.VerificationResult("image/jpeg", 120, 90);
        when(mapper.selectById(photoId)).thenReturn(asset);
        when(mapper.markAvailableFromVerification(eq(photoId), eq(token), eq("image/jpeg"),
                eq(120), eq(90), any(Instant.class))).thenReturn(1);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"photoId\":\"" + photoId + "\"}");

        PhotoAssetEntity result = new PhotoUploadCompletionPersistenceService(mapper, jobs, objectMapper, audit)
                .finalizeVerified(photoId, token, verification, actorId);

        assertThat(result.getStatus()).isEqualTo(PhotoAssetStatus.AVAILABLE.name());
        verify(mapper).lockProjectUploadCompletion(projectId);
        verify(mapper).markAvailableFromVerification(eq(photoId), eq(token), eq("image/jpeg"),
                eq(120), eq(90), any(Instant.class));
        verify(jobs).enqueue(eq(projectId), eq(actorId), any(), eq("PHOTO_ASSET"), eq(photoId),
                any(), eq(5), eq(true));
        verify(audit).record(actorId, "PHOTO_UPLOAD_COMPLETE", "PHOTO_ASSET", photoId.toString(), true);
    }

    @Test
    void marksConcurrentHashMatchAsDuplicateWithoutCreatingAnotherJob() {
        UUID projectId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        PhotoAssetEntity asset = verifying(projectId, photoId, token);
        PhotoAssetEntity duplicate = new PhotoAssetEntity();
        duplicate.setId(UUID.randomUUID());
        when(mapper.selectById(photoId)).thenReturn(asset);
        when(mapper.findAvailableDuplicate(projectId, asset.getSha256())).thenReturn(duplicate);
        when(mapper.markDuplicateFromVerification(eq(photoId), eq(token), eq(duplicate.getId()),
                any(Instant.class))).thenReturn(1);

        PhotoAssetEntity result = new PhotoUploadCompletionPersistenceService(mapper, jobs, objectMapper, audit)
                .finalizeVerified(photoId, token,
                        new PhotoObjectVerifier.VerificationResult("image/jpeg", 120, 90), actorId);

        assertThat(result.getStatus()).isEqualTo(PhotoAssetStatus.DUPLICATE.name());
        assertThat(result.getDuplicateOf()).isEqualTo(duplicate.getId());
        verify(jobs, never()).enqueue(any(), any(), any(), any(), any(), any(), any(Integer.class), any(Boolean.class));
    }

    @Test
    void refusesToFinalizeAfterTheVerificationTokenWasReplaced() {
        UUID projectId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        PhotoAssetEntity asset = verifying(projectId, photoId, token);
        when(mapper.selectById(photoId)).thenReturn(asset);

        assertThatThrownBy(() -> new PhotoUploadCompletionPersistenceService(mapper, jobs, objectMapper, audit)
                .finalizeVerified(photoId, token,
                        new PhotoObjectVerifier.VerificationResult("image/jpeg", 120, 90), UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("认领已失效");

        verify(jobs, never()).enqueue(any(), any(), any(), any(), any(), any(), any(Integer.class), any(Boolean.class));
        verify(audit, never()).record(any(), any(), any(), any(), any(Boolean.class));
    }

    private PhotoAssetEntity verifying(UUID projectId, UUID photoId, UUID token) {
        PhotoAssetEntity asset = new PhotoAssetEntity();
        asset.setId(photoId);
        asset.setProjectId(projectId);
        asset.setStatus(PhotoAssetStatus.VERIFYING.name());
        asset.setVerificationToken(token);
        asset.setSha256("b".repeat(64));
        asset.setUpdatedAt(Instant.now());
        return asset;
    }
}
