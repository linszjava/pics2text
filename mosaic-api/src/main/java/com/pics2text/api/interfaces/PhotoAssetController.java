package com.pics2text.api.interfaces;

import com.pics2text.api.application.PhotoAssetService;
import com.pics2text.api.infrastructure.persistence.PhotoAssetEntity;
import com.pics2text.api.infrastructure.storage.ObjectStorageGateway;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import com.pics2text.api.application.PhotoRelayUploadService;
import com.pics2text.api.infrastructure.persistence.PhotoRelayUploadTaskEntity;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/photos")
public class PhotoAssetController {
    private final PhotoAssetService service;
    private final PhotoRelayUploadService relay;

    public PhotoAssetController(PhotoAssetService service, PhotoRelayUploadService relay) {
        this.service = service;
        this.relay = relay;
    }

    @PostMapping(value="/relay-uploads", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RelayUploadResponse relayUpload(@PathVariable UUID projectId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue="true") boolean authorizationGranted) {
        var result=relay.receive(projectId,file,authorizationGranted);
        return RelayUploadResponse.from(result);
    }

    @GetMapping("/relay-uploads/{taskId}")
    public RelayUploadResponse relayStatus(@PathVariable UUID projectId,@PathVariable UUID taskId) {
        var result=relay.get(projectId,taskId);
        return RelayUploadResponse.from(result.task(),result.photo(),false);
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

    @PatchMapping("/{photoId}")
    public PhotoResponse rename(@PathVariable UUID projectId, @PathVariable UUID photoId,
            @Valid @RequestBody RenamePhotoRequest request) {
        return PhotoResponse.from(service.rename(projectId, photoId, request.filename()));
    }

    @GetMapping("/{photoId}/analysis")
    public PhotoAssetService.AnalysisResult analysis(@PathVariable UUID projectId,
            @PathVariable UUID photoId) {
        return service.analysis(projectId, photoId);
    }

    @PostMapping("/{photoId}/download-url")
    public PresignedResponse download(@PathVariable UUID projectId, @PathVariable UUID photoId) {
        return PresignedResponse.from(service.download(projectId, photoId));
    }

    @PostMapping("/{photoId}/preview-urls")
    public PhotoPreviewResponse preview(@PathVariable UUID projectId, @PathVariable UUID photoId) {
        var result = service.preview(projectId, photoId);
        return new PhotoPreviewResponse(PresignedResponse.from(result.thumbnail()),
                PresignedResponse.from(result.preview()));
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
    public record RenamePhotoRequest(@NotBlank @Size(max = 255) String filename) {}
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

    public record PhotoPreviewResponse(PresignedResponse thumbnail, PresignedResponse preview) {}

    public record RelayUploadResponse(UUID taskId, String taskStatus, int attemptCount, int maxAttempts,
            String failureCode, String failureMessage, boolean duplicate, PhotoResponse photo) {
        static RelayUploadResponse from(PhotoRelayUploadService.Result r) {
            return r.duplicate()?new RelayUploadResponse(null,"SUCCEEDED",0,0,null,null,true,PhotoResponse.from(r.photo()))
                    :from(r.task(),r.photo(),false);
        }
        static RelayUploadResponse from(PhotoRelayUploadTaskEntity t,PhotoAssetEntity p,boolean duplicate) {
            return new RelayUploadResponse(t.getId(),t.getStatus(),t.getAttemptCount(),t.getMaxAttempts(),
                    t.getFailureCode(),t.getFailureMessage(),duplicate,PhotoResponse.from(p));
        }
    }

    public record PhotoResponse(UUID id, UUID projectId, UUID uploaderId, String originalFilename,
            String declaredContentType, String detectedContentType, long byteSize, Integer width,
            Integer height, String sha256, String authorizationStatus,
            String status, String analysisStatus, String uploadMode, UUID duplicateOf, String failureCode,
            String failureMessage, String analysisErrorCode, String analysisErrorMessage,
            Instant retentionUntil, Instant availableAt, Instant createdAt, Instant updatedAt) {
        static PhotoResponse from(PhotoAssetEntity asset) {
            return new PhotoResponse(asset.getId(), asset.getProjectId(), asset.getUploaderId(),
                    asset.getOriginalFilename(), asset.getDeclaredContentType(), asset.getDetectedContentType(),
                    asset.getByteSize(), asset.getWidth(), asset.getHeight(), asset.getSha256(),
                    asset.getAuthorizationStatus(), asset.getStatus(),
                    asset.getAnalysisStatus(), asset.getUploadMode(), asset.getDuplicateOf(),
                    asset.getFailureCode(), asset.getFailureMessage(), asset.getAnalysisErrorCode(),
                    asset.getAnalysisErrorMessage(), asset.getRetentionUntil(), asset.getAvailableAt(),
                    asset.getCreatedAt(), asset.getUpdatedAt());
        }
    }
}
