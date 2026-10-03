package com.pics2text.api.interfaces;

import com.pics2text.api.application.LayoutEditorService;
import com.pics2text.api.common.ApiException;
import com.pics2text.api.infrastructure.storage.ObjectStorageGateway;
import com.pics2text.layout.application.LayoutEditCommand;
import com.pics2text.layout.domain.CropBox;
import com.pics2text.layout.domain.LayoutCommandType;
import com.pics2text.layout.infrastructure.persistence.EditableLayoutEntity;
import com.pics2text.layout.infrastructure.persistence.LayoutEditOperationEntity;
import com.pics2text.layout.infrastructure.persistence.LayoutEditorAssignmentEntity;
import com.pics2text.layout.infrastructure.persistence.LayoutPreviewEntity;
import com.pics2text.layout.infrastructure.persistence.LayoutVersionEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/editable-layouts")
public class LayoutEditorController {
    private final LayoutEditorService service;

    public LayoutEditorController(LayoutEditorService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LayoutResponse create(@PathVariable UUID projectId,
            @Valid @RequestBody CreateLayoutRequest request) {
        return LayoutResponse.from(service.create(projectId, request.sourceAutoLayoutId(), request.name()));
    }

    @GetMapping
    public List<LayoutResponse> list(@PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(projectId, page, size).stream().map(LayoutResponse::from).toList();
    }

    @GetMapping("/{layoutId}")
    public LayoutResponse get(@PathVariable UUID projectId, @PathVariable UUID layoutId) {
        return LayoutResponse.from(service.get(projectId, layoutId));
    }

    @PatchMapping("/{layoutId}")
    public LayoutResponse rename(@PathVariable UUID projectId, @PathVariable UUID layoutId,
            @Valid @RequestBody RenameLayoutRequest request) {
        return LayoutResponse.from(service.rename(projectId, layoutId, request.name(),
                request.expectedLockVersion()));
    }

    @DeleteMapping("/{layoutId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID layoutId) {
        service.delete(projectId, layoutId);
    }

    @PutMapping("/{layoutId}/activate")
    public LayoutResponse activate(@PathVariable UUID projectId, @PathVariable UUID layoutId) {
        return LayoutResponse.from(service.activate(projectId, layoutId));
    }

    @GetMapping("/{layoutId}/assignments")
    public List<LayoutEditorAssignmentEntity> assignments(@PathVariable UUID projectId,
            @PathVariable UUID layoutId, @RequestParam(required = false) UUID photoId,
            @RequestParam(required = false) Integer slotIndex,
            @RequestParam(required = false) Integer minX, @RequestParam(required = false) Integer minY,
            @RequestParam(required = false) Integer maxX, @RequestParam(required = false) Integer maxY,
            @RequestParam(defaultValue = "2000") int limit) {
        return service.assignments(projectId, layoutId, photoId, slotIndex,
                minX, minY, maxX, maxY, limit);
    }

    @PostMapping("/{layoutId}/photo-preview-urls")
    public List<LayoutEditorService.PhotoPreviewUrl> photoPreviewUrls(@PathVariable UUID projectId,
            @PathVariable UUID layoutId, @Valid @RequestBody PhotoPreviewRequest request) {
        return service.photoPreviewUrls(projectId, layoutId, request.photoIds());
    }

    @PostMapping("/{layoutId}/edits")
    public EditResponse edit(@PathVariable UUID projectId, @PathVariable UUID layoutId,
            @Valid @RequestBody EditBatchRequest request) {
        try {
            var result = service.edit(projectId, layoutId, request.expectedLockVersion(),
                    request.clientBatchId(), request.note(), request.commands().stream()
                            .map(EditCommandRequest::toDomain).toList());
            return new EditResponse(LayoutResponse.from(result.layout()), result.version(),
                    result.idempotentReplay());
        } catch (IllegalArgumentException exception) {
            throw new ApiException("LAYOUT_EDIT_INVALID", exception.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/{layoutId}/restore")
    public EditResponse restore(@PathVariable UUID projectId, @PathVariable UUID layoutId,
            @Valid @RequestBody RestoreRequest request) {
        var result = service.restore(projectId, layoutId, request.versionId(),
                request.expectedLockVersion(), request.clientBatchId(), request.note());
        return new EditResponse(LayoutResponse.from(result.layout()), result.version(),
                result.idempotentReplay());
    }

    @PostMapping("/{layoutId}/lock")
    public TransitionResponse lock(@PathVariable UUID projectId, @PathVariable UUID layoutId,
            @Valid @RequestBody TransitionRequest request) {
        var result = service.lock(projectId, layoutId, request.expectedLockVersion(), request.note());
        return new TransitionResponse(LayoutResponse.from(result.layout()), result.version());
    }

    @PostMapping("/{layoutId}/unlock")
    public LayoutResponse unlock(@PathVariable UUID projectId, @PathVariable UUID layoutId,
            @Valid @RequestBody TransitionRequest request) {
        return LayoutResponse.from(service.unlock(projectId, layoutId, request.expectedLockVersion()));
    }

    @PostMapping("/{layoutId}/publish")
    public TransitionResponse publish(@PathVariable UUID projectId, @PathVariable UUID layoutId,
            @Valid @RequestBody TransitionRequest request) {
        var result = service.publish(projectId, layoutId, request.expectedLockVersion(), request.note());
        return new TransitionResponse(LayoutResponse.from(result.layout()), result.version());
    }

    @GetMapping("/{layoutId}/versions")
    public List<LayoutVersionEntity> versions(@PathVariable UUID projectId, @PathVariable UUID layoutId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.versions(projectId, layoutId, page, size);
    }

    @GetMapping("/{layoutId}/operations")
    public List<LayoutEditOperationEntity> operations(@PathVariable UUID projectId,
            @PathVariable UUID layoutId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.operations(projectId, layoutId, page, size);
    }

    @PostMapping("/{layoutId}/previews")
    @ResponseStatus(HttpStatus.CREATED)
    public PreviewCreatedResponse createPreview(@PathVariable UUID projectId, @PathVariable UUID layoutId,
            @RequestBody(required = false) PreviewRequest request) {
        var result = service.createPreview(projectId, layoutId, request == null ? null : request.versionId());
        return new PreviewCreatedResponse(PreviewResponse.from(result.preview()), result.jobId(), result.existing());
    }

    @GetMapping("/{layoutId}/previews/{previewId}")
    public PreviewResponse preview(@PathVariable UUID projectId, @PathVariable UUID layoutId,
            @PathVariable UUID previewId) {
        return PreviewResponse.from(service.getPreview(projectId, layoutId, previewId));
    }

    @PostMapping("/{layoutId}/previews/{previewId}/download-url")
    public PresignedResponse previewUrl(@PathVariable UUID projectId, @PathVariable UUID layoutId,
            @PathVariable UUID previewId) {
        return PresignedResponse.from(service.previewUrl(projectId, layoutId, previewId));
    }

    public record CreateLayoutRequest(@NotNull UUID sourceAutoLayoutId,
            @NotBlank @Size(max = 120) String name) { }
    public record RenameLayoutRequest(@NotBlank @Size(max = 120) String name,
            @Min(0) long expectedLockVersion) { }

    public record EditBatchRequest(@Min(0) long expectedLockVersion, @NotNull UUID clientBatchId,
            @Size(max = 500) String note,
            @NotEmpty @Size(max = 100) List<@Valid EditCommandRequest> commands) { }

    public record EditCommandRequest(@NotNull LayoutCommandType type, @Min(0) int slotIndex,
            Integer secondarySlotIndex, UUID photoId, Double cropX, Double cropY,
            Double cropWidth, Double cropHeight) {
        LayoutEditCommand toDomain() {
            return switch (type) {
                case SWAP -> new LayoutEditCommand.Swap(slotIndex, required(secondarySlotIndex, "secondarySlotIndex"));
                case REPLACE -> new LayoutEditCommand.Replace(slotIndex, required(photoId, "photoId"));
                case CROP -> new LayoutEditCommand.Crop(slotIndex, new CropBox(required(cropX, "cropX"),
                        required(cropY, "cropY"), required(cropWidth, "cropWidth"),
                        required(cropHeight, "cropHeight")));
                case RESTORE -> throw new IllegalArgumentException("RESTORE 必须使用版本恢复接口");
            };
        }
        private <T> T required(T value, String field) {
            if (value == null) throw new IllegalArgumentException(type + " 操作缺少 " + field);
            return value;
        }
    }

    public record RestoreRequest(@NotNull UUID versionId, @Min(0) long expectedLockVersion,
            @NotNull UUID clientBatchId, @Size(max = 500) String note) { }
    public record TransitionRequest(@Min(0) long expectedLockVersion, @Size(max = 500) String note) { }
    public record PreviewRequest(UUID versionId) { }
    public record PhotoPreviewRequest(@NotEmpty @Size(max = 200) List<@NotNull UUID> photoIds) { }

    public record EditResponse(LayoutResponse layout, LayoutVersionEntity version,
            boolean idempotentReplay) { }
    public record TransitionResponse(LayoutResponse layout, LayoutVersionEntity version) { }
    public record PreviewCreatedResponse(PreviewResponse preview, UUID jobId, boolean existing) { }

    public record LayoutResponse(UUID id, UUID projectId, UUID sourceAutoLayoutId, UUID maskId,
            String name, String state, int canvasWidth, int canvasHeight, boolean allowPhotoReuse,
            int maxPhotoReuse, long currentRevision, long lockVersion, int currentVersionNumber,
            UUID currentVersionId, UUID publishedVersionId, UUID lockedBy, Instant lockedAt,
            UUID publishedBy, Instant publishedAt, UUID createdBy, Instant createdAt, Instant updatedAt) {
        static LayoutResponse from(EditableLayoutEntity value) {
            return new LayoutResponse(value.getId(), value.getProjectId(), value.getSourceAutoLayoutId(),
                    value.getMaskId(), value.getName(), value.getState(), value.getCanvasWidth(),
                    value.getCanvasHeight(), value.getAllowPhotoReuse(), value.getMaxPhotoReuse(),
                    value.getCurrentRevision(), value.getLockVersion(), value.getCurrentVersionNumber(),
                    value.getCurrentVersionId(), value.getPublishedVersionId(), value.getLockedBy(),
                    value.getLockedAt(), value.getPublishedBy(), value.getPublishedAt(), value.getCreatedBy(),
                    value.getCreatedAt(), value.getUpdatedAt());
        }
    }

    public record PreviewResponse(UUID id, UUID layoutId, UUID versionId, String status,
            Integer width, Integer height, int assignmentCount, String errorCode,
            String errorMessage, UUID createdBy, Instant createdAt, Instant updatedAt,
            Instant completedAt) {
        static PreviewResponse from(LayoutPreviewEntity value) {
            return new PreviewResponse(value.getId(), value.getLayoutId(), value.getVersionId(),
                    value.getStatus(), value.getWidth(), value.getHeight(), value.getAssignmentCount(),
                    value.getErrorCode(), value.getErrorMessage(), value.getCreatedBy(), value.getCreatedAt(),
                    value.getUpdatedAt(), value.getCompletedAt());
        }
    }

    public record PresignedResponse(URI url, String method, Map<String, List<String>> headers) {
        static PresignedResponse from(ObjectStorageGateway.PresignedRequest request) {
            return new PresignedResponse(request.url(), request.method(), request.headers());
        }
    }
}
