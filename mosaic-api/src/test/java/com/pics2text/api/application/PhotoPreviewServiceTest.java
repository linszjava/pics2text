package com.pics2text.api.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pics2text.api.infrastructure.persistence.PhotoAssetEntity;
import com.pics2text.api.infrastructure.persistence.PhotoAssetMapper;
import com.pics2text.api.infrastructure.storage.ObjectStorageGateway;
import com.pics2text.api.common.ApiException;
import com.pics2text.identity.domain.GlobalRole;
import com.pics2text.identity.domain.User;
import com.pics2text.identity.domain.UserStatus;
import com.pics2text.photoasset.domain.AnalysisStatus;
import com.pics2text.photoasset.domain.PhotoAssetStatus;
import com.pics2text.project.domain.ProjectPermission;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhotoPreviewServiceTest {
    @Mock private PhotoAssetMapper mapper;
    @Mock private ProjectAccessService access;
    @Mock private ObjectStorageGateway storage;
    @Mock private AuditService audit;

    private PhotoAssetService service;
    private UUID projectId;
    private UUID photoId;
    private PhotoAssetEntity photo;

    @BeforeEach
    void setUp() {
        service = new PhotoAssetService(mapper, access, storage, null, null, null,
                audit, null, null, null, null);
        projectId = UUID.randomUUID();
        photoId = UUID.randomUUID();
        photo = new PhotoAssetEntity();
        photo.setProjectId(projectId);
        photo.setStatus(PhotoAssetStatus.AVAILABLE.name());
        photo.setOriginalKey("original.jpg");
        photo.setThumbnailKey("thumbnail.webp");
        photo.setPreviewKey("preview.webp");
        photo.setOriginalFilename("photo.jpg");
        when(mapper.selectById(photoId)).thenReturn(photo);
    }

    @Test
    void pendingAnalysisUsesExistingOriginal() {
        photo.setAnalysisStatus(AnalysisStatus.PENDING.name());
        when(storage.presignGet("original.jpg", "photo-thumbnail.webp"))
                .thenReturn(signed("original.jpg"));
        when(storage.presignGet("original.jpg", "photo.jpg"))
                .thenReturn(signed("original.jpg"));

        var preview = service.preview(projectId, photoId);

        assertEquals("original.jpg", preview.thumbnail().url().getPath().substring(1));
        verify(storage).presignGet("original.jpg", "photo-thumbnail.webp");
        verify(storage).presignGet("original.jpg", "photo.jpg");
    }

    @Test
    void completedAnalysisUsesDerivedImages() {
        photo.setAnalysisStatus(AnalysisStatus.SUCCEEDED.name());
        when(storage.presignGet("thumbnail.webp", "photo-thumbnail.webp"))
                .thenReturn(signed("thumbnail.webp"));
        when(storage.presignGet("preview.webp", "photo.jpg"))
                .thenReturn(signed("preview.webp"));

        service.preview(projectId, photoId);

        verify(storage).presignGet("thumbnail.webp", "photo-thumbnail.webp");
        verify(storage).presignGet("preview.webp", "photo.jpg");
    }

    @Test
    void renameUpdatesOnlyMetadata() {
        Instant now = Instant.now();
        User user = new User(UUID.randomUUID(), "owner", "hash", "Owner",
                GlobalRole.USER, UserStatus.ACTIVE, now, now);
        when(access.require(projectId, ProjectPermission.MANAGE_PHOTOS)).thenReturn(user);

        PhotoAssetEntity updated = service.rename(projectId, photoId, "new name.jpg");

        assertEquals("new name.jpg", updated.getOriginalFilename());
        assertEquals("original.jpg", updated.getOriginalKey());
        verify(mapper).updateById(photo);
        verify(audit).record(user.id(), "PHOTO_RENAME", "PHOTO_ASSET", photoId.toString(), true);
    }

    @Test
    void renameRejectsPathCharacters() {
        Instant now = Instant.now();
        User user = new User(UUID.randomUUID(), "owner", "hash", "Owner",
                GlobalRole.USER, UserStatus.ACTIVE, now, now);
        when(access.require(projectId, ProjectPermission.MANAGE_PHOTOS)).thenReturn(user);

        assertThrows(ApiException.class, () -> service.rename(projectId, photoId, "../photo.jpg"));
    }

    private ObjectStorageGateway.PresignedRequest signed(String path) {
        return new ObjectStorageGateway.PresignedRequest(URI.create("http://localhost/" + path),
                "GET", Map.of());
    }
}
