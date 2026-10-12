package com.pics2text.api.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pics2text.api.common.ApiException;
import com.pics2text.api.config.ObjectStorageProperties;
import com.pics2text.api.infrastructure.persistence.PhotoAssetEntity;
import com.pics2text.api.infrastructure.persistence.PhotoAssetMapper;
import com.pics2text.api.infrastructure.storage.ObjectStorageGateway;
import com.pics2text.identity.domain.GlobalRole;
import com.pics2text.identity.domain.User;
import com.pics2text.identity.domain.UserStatus;
import com.pics2text.photoasset.domain.PhotoAssetStatus;
import com.pics2text.photoasset.domain.UploadMode;
import com.pics2text.photoasset.infrastructure.persistence.PhotoAnalysisMapper;
import com.pics2text.project.domain.ProjectPermission;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhotoUploadCompletionServiceTest {
    @Mock PhotoAssetMapper mapper;
    @Mock ProjectAccessService access;
    @Mock ObjectStorageGateway storage;
    @Mock ObjectStorageProperties properties;
    @Mock ObjectKeyPolicy keyPolicy;
    @Mock PhotoObjectVerifier verifier;
    @Mock AuditService audit;
    @Mock PhotoAnalysisMapper analysisMapper;
    @Mock PhotoObjectDeletionService deletionService;
    @Mock PhotoUploadCompletionPersistenceService completionPersistence;

    private PhotoAssetService service;
    private UUID projectId;
    private UUID photoId;
    private User actor;
    private PhotoUploadCompletionLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new PhotoUploadCompletionLimiter(4, 2, Duration.ofSeconds(1));
        service = new PhotoAssetService(mapper, access, storage, properties, keyPolicy, verifier,
                audit, analysisMapper, deletionService, completionPersistence, limiter, Duration.ofMinutes(30));
        projectId = UUID.randomUUID();
        photoId = UUID.randomUUID();
        Instant now = Instant.now();
        actor = new User(UUID.randomUUID(), "owner", "hash", "Owner",
                GlobalRole.USER, UserStatus.ACTIVE, now, now);
        when(access.require(projectId, ProjectPermission.UPLOAD_PHOTOS)).thenReturn(actor);
    }

    @Test
    void claimsBeforeStreamingVerificationAndFinalizesOutsideTheClaimTransaction() {
        PhotoAssetEntity asset = upload(PhotoAssetStatus.UPLOADING, UploadMode.SINGLE);
        PhotoAssetEntity completed = upload(PhotoAssetStatus.AVAILABLE, UploadMode.SINGLE);
        var verification = new PhotoObjectVerifier.VerificationResult("image/jpeg", 100, 80);
        when(mapper.selectById(photoId)).thenReturn(asset);
        when(mapper.claimVerification(eq(projectId), eq(photoId), any(UUID.class),
                any(Instant.class), any(Instant.class)))
                .thenReturn(1);
        when(verifier.verify(asset.getOriginalKey(), asset.getByteSize(), asset.getSha256()))
                .thenReturn(verification);
        when(completionPersistence.finalizeVerified(eq(photoId), any(UUID.class),
                eq(verification), eq(actor.id()))).thenReturn(completed);

        assertThat(service.complete(projectId, photoId, List.of())).isSameAs(completed);

        InOrder order = inOrder(mapper, verifier, completionPersistence);
        order.verify(mapper).claimVerification(eq(projectId), eq(photoId), any(UUID.class),
                any(Instant.class), any(Instant.class));
        order.verify(verifier).verify(asset.getOriginalKey(), asset.getByteSize(), asset.getSha256());
        order.verify(completionPersistence).finalizeVerified(eq(photoId), any(UUID.class),
                eq(verification), eq(actor.id()));
    }

    @Test
    void terminalCompletionRetryIsIdempotent() {
        PhotoAssetEntity available = upload(PhotoAssetStatus.AVAILABLE, UploadMode.SINGLE);
        when(mapper.selectById(photoId)).thenReturn(available);

        assertThat(service.complete(projectId, photoId, List.of())).isSameAs(available);

        verify(mapper, never()).claimVerification(any(), any(), any(), any(), any());
        verify(verifier, never()).verify(any(), any(Long.class), any());
    }

    @Test
    void staleMultipartVerificationUsesCompletedObjectWithoutCompletingUploadAgain() {
        PhotoAssetEntity asset = upload(PhotoAssetStatus.VERIFYING, UploadMode.MULTIPART);
        asset.setUpdatedAt(Instant.now().minus(Duration.ofMinutes(31)));
        var parts = List.of(new ObjectStorageGateway.CompletedUploadPart(1, "etag"));
        var verification = new PhotoObjectVerifier.VerificationResult("image/jpeg", 100, 80);
        when(mapper.selectById(photoId)).thenReturn(asset);
        when(mapper.claimVerification(eq(projectId), eq(photoId), any(UUID.class),
                any(Instant.class), any(Instant.class)))
                .thenReturn(1);
        when(storage.exists(asset.getOriginalKey())).thenReturn(true);
        when(verifier.verify(asset.getOriginalKey(), asset.getByteSize(), asset.getSha256()))
                .thenReturn(verification);
        when(completionPersistence.finalizeVerified(eq(photoId), any(UUID.class),
                eq(verification), eq(actor.id()))).thenReturn(asset);

        service.complete(projectId, photoId, parts);

        verify(storage, never()).completeMultipart(any(), any(), any());
        verify(verifier).verify(asset.getOriginalKey(), asset.getByteSize(), asset.getSha256());
    }

    @Test
    void staleMultipartVerificationCompletesStillActiveUploadWhenObjectIsMissing() {
        PhotoAssetEntity asset = upload(PhotoAssetStatus.VERIFYING, UploadMode.MULTIPART);
        asset.setUpdatedAt(Instant.now().minus(Duration.ofMinutes(31)));
        var parts = List.of(new ObjectStorageGateway.CompletedUploadPart(1, "etag"));
        var verification = new PhotoObjectVerifier.VerificationResult("image/jpeg", 100, 80);
        when(mapper.selectById(photoId)).thenReturn(asset);
        when(mapper.claimVerification(eq(projectId), eq(photoId), any(UUID.class),
                any(Instant.class), any(Instant.class)))
                .thenReturn(1);
        when(storage.exists(asset.getOriginalKey())).thenReturn(false);
        when(verifier.verify(asset.getOriginalKey(), asset.getByteSize(), asset.getSha256()))
                .thenReturn(verification);
        when(completionPersistence.finalizeVerified(eq(photoId), any(UUID.class),
                eq(verification), eq(actor.id()))).thenReturn(asset);

        service.complete(projectId, photoId, parts);

        verify(storage).completeMultipart(asset.getOriginalKey(), asset.getMultipartUploadId(), parts);
    }

    @Test
    void cancellationLosesCleanlyWhenCompletionAlreadyClaimedTheUpload() {
        PhotoAssetEntity asset = upload(PhotoAssetStatus.UPLOADING, UploadMode.MULTIPART);
        when(mapper.selectById(photoId)).thenReturn(asset);
        when(mapper.cancelUploading(eq(projectId), eq(photoId), any(Instant.class))).thenReturn(0);

        assertThatThrownBy(() -> service.cancelUpload(projectId, photoId))
                .isInstanceOf(ApiException.class)
                .extracting(failure -> ((ApiException) failure).code())
                .isEqualTo("UPLOAD_STATE_INVALID");
        verify(deletionService, never()).deleteAll(asset);
    }

    @Test
    void staleVerifierCannotFailOrDeleteTheObjectOwnedByItsReplacement() {
        PhotoAssetEntity stale = upload(PhotoAssetStatus.UPLOADING, UploadMode.SINGLE);
        PhotoAssetEntity completed = upload(PhotoAssetStatus.AVAILABLE, UploadMode.SINGLE);
        when(mapper.selectById(photoId)).thenReturn(stale, completed);
        when(mapper.claimVerification(eq(projectId), eq(photoId), any(UUID.class),
                any(Instant.class), any(Instant.class))).thenReturn(1);
        when(verifier.verify(stale.getOriginalKey(), stale.getByteSize(), stale.getSha256()))
                .thenThrow(new RuntimeException("old verifier lost its lease"));
        when(completionPersistence.failVerification(eq(photoId), any(UUID.class),
                eq("PHOTO_STORAGE_ERROR"), any(String.class), eq(actor.id()))).thenReturn(false);

        assertThat(service.complete(projectId, photoId, List.of())).isSameAs(completed);

        verify(deletionService, never()).deleteAll(stale);
        verify(mapper, never()).markObjectsDeleted(eq(photoId), any(Instant.class));
    }

    @Test
    void capacityRejectionDoesNotClaimFailOrDeleteTheUpload() {
        PhotoAssetEntity asset = upload(PhotoAssetStatus.UPLOADING, UploadMode.SINGLE);
        when(mapper.selectById(photoId)).thenReturn(asset);
        PhotoUploadCompletionLimiter oneSlot = new PhotoUploadCompletionLimiter(1, 1, Duration.ofSeconds(2));
        service = new PhotoAssetService(mapper, access, storage, properties, keyPolicy, verifier,
                audit, analysisMapper, deletionService, completionPersistence, oneSlot, Duration.ofMinutes(30));

        try (var occupied = oneSlot.acquire(UUID.randomUUID())) {
            assertThatThrownBy(() -> service.complete(projectId, photoId, List.of()))
                    .isInstanceOf(com.pics2text.api.common.RetryableCapacityException.class)
                    .extracting(failure -> ((ApiException) failure).code())
                    .isEqualTo("PHOTO_UPLOAD_CAPACITY_EXCEEDED");
        }

        verify(mapper, never()).claimVerification(any(), any(), any(), any(), any());
        verify(completionPersistence, never()).failVerification(any(), any(), any(), any(), any());
        verify(deletionService, never()).deleteAll(asset);
    }

    private PhotoAssetEntity upload(PhotoAssetStatus status, UploadMode mode) {
        PhotoAssetEntity asset = new PhotoAssetEntity();
        asset.setId(photoId);
        asset.setProjectId(projectId);
        asset.setStatus(status.name());
        asset.setUploadMode(mode.name());
        asset.setOriginalKey("projects/" + projectId + "/photos/" + photoId + "/original/photo.jpg");
        asset.setMultipartUploadId(mode == UploadMode.MULTIPART ? "upload-1" : null);
        asset.setExpectedPartCount(1);
        asset.setByteSize(1024L);
        asset.setSha256("a".repeat(64));
        asset.setDeclaredContentType("image/jpeg");
        asset.setUploadExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        asset.setUpdatedAt(Instant.now());
        return asset;
    }
}
