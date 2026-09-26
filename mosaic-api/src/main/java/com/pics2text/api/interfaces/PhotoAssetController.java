package com.pics2text.api.interfaces;

import com.pics2text.api.application.PhotoAssetService;
import com.pics2text.api.infrastructure.persistence.PhotoAssetEntity;
import com.pics2text.api.infrastructure.storage.ObjectStorageGateway;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/photos")
public class PhotoAssetController {
    private final PhotoAssetService service;

    public PhotoAssetController(PhotoAssetService service) {
        this.service = service;
    }

    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public InitiatedUploadResponse initiate(@PathVariable UUID projectId,
            @Valid @RequestBody InitiateUploadRequest request) {
        var result = service.initiate(projectId, request.filename(), request.contentType(),
                request.byteSize(), request.sha256(), request.authorizationGranted());
        return InitiatedUploadResponse.from(result);
    }

    @PostMapping("/{photoId}/uploads/parts/{partNumber}/presign")
    public PresignedResponse presignPart(@PathVariable UUID projectId, @PathVariable UUID photoId,
            @PathVariable int partNumber) {
        return PresignedResponse.from(service.presignPart(projectId, photoId, partNumber));
    }

    @PostMapping("/{photoId}/uploads/complete")
    public PhotoResponse complete(@PathVariable UUID projectId, @PathVariable UUID photoId,
            @Valid @RequestBody CompleteUploadRequest request) {
        List<ObjectStorageGateway.CompletedUploadPart> parts = request.parts() == null ? List.of()
                : request.parts().stream().map(part ->
                        new ObjectStorageGateway.CompletedUploadPart(part.partNumber(), part.eTag())).toList();
        return PhotoResponse.from(service.complete(projectId, photoId, parts));
    }

    @GetMapping("/{photoId}/uploads/progress")
    public PhotoAssetService.UploadProgress progress(@PathVariable UUID projectId, @PathVariable UUID photoId) {
        return service.progress(projectId, photoId);
    }

    @DeleteMapping("/{photoId}/uploads")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID projectId, @PathVariable UUID photoId) {
        service.cancelUpload(projectId, photoId);
    }

    @GetMapping
    public List<PhotoResponse> list(@PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(projectId, page, size).stream().map(PhotoResponse::from).toList();
    }

    @GetMapping("/{photoId}")
    public PhotoResponse get(@PathVariable UUID projectId, @PathVariable UUID photoId) {
        return PhotoResponse.from(service.get(projectId, photoId));
    }

    @PostMapping("/{photoId}/download-url")
    public PresignedResponse download(@PathVariable UUID projectId, @PathVariable UUID photoId) {
        return PresignedResponse.from(service.download(projectId, photoId));
    }

    @PostMapping("/{photoId}/withdraw")
    public PhotoResponse withdraw(@PathVariable UUID projectId, @PathVariable UUID photoId) {
        return PhotoResponse.from(service.withdraw(projectId, photoId));
    }

    @DeleteMapping("/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID photoId) {
        service.delete(projectId, photoId);
    }

    public record InitiateUploadRequest(
            @NotBlank @Size(max = 255) String filename,
            @NotBlank String contentType,
            @Min(1) long byteSize,
            @NotBlank @Pattern(regexp = "^[0-9a-fA-F]{64}$") String sha256,
            boolean authorizationGranted) {}

    public record CompleteUploadRequest(List<@Valid CompletedPartRequest> parts) {}
    public record CompletedPartRequest(@Min(1) int partNumber, @NotBlank String eTag) {}

    public record InitiatedUploadResponse(PhotoResponse photo, boolean duplicate,
            PresignedResponse directUpload, Long partSize, Integer partCount, Instant expiresAt) {
        static InitiatedUploadResponse from(PhotoAssetService.InitiatedUpload result) {
            var asset = result.asset();
            return new InitiatedUploadResponse(PhotoResponse.from(asset), result.duplicate(),
                    PresignedResponse.fromNullable(result.directUpload()), asset.getPartSize(),
                    asset.getExpectedPartCount(), asset.getUploadExpiresAt());
        }
    }

    public record PresignedResponse(URI url, String method, Map<String, List<String>> headers) {
        static PresignedResponse from(ObjectStorageGateway.PresignedRequest request) {
            return new PresignedResponse(request.url(), request.method(), request.headers());
        }
        static PresignedResponse fromNullable(ObjectStorageGateway.PresignedRequest request) {
            return request == null ? null : from(request);
        }
    }

    public record PhotoResponse(UUID id, UUID projectId, UUID uploaderId, String originalFilename,
            String declaredContentType, String detectedContentType, long byteSize, Integer width,
            Integer height, String sha256, String authorizationStatus, String reviewStatus,
            String status, String uploadMode, UUID duplicateOf, String failureCode,
            String failureMessage, Instant retentionUntil, Instant availableAt, Instant createdAt,
            Instant updatedAt) {
        static PhotoResponse from(PhotoAssetEntity asset) {
            return new PhotoResponse(asset.getId(), asset.getProjectId(), asset.getUploaderId(),
                    asset.getOriginalFilename(), asset.getDeclaredContentType(), asset.getDetectedContentType(),
                    asset.getByteSize(), asset.getWidth(), asset.getHeight(), asset.getSha256(),
                    asset.getAuthorizationStatus(), asset.getReviewStatus(), asset.getStatus(),
                    asset.getUploadMode(), asset.getDuplicateOf(), asset.getFailureCode(),
                    asset.getFailureMessage(), asset.getRetentionUntil(), asset.getAvailableAt(),
                    asset.getCreatedAt(), asset.getUpdatedAt());
        }
    }
}
