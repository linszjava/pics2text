package com.pics2text.api.interfaces;

import com.pics2text.api.application.PrintRenderApplicationService;
import com.pics2text.api.infrastructure.storage.ObjectStorageGateway;
import com.pics2text.rendering.infrastructure.persistence.PrintRenderEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/print-renders")
public class PrintRenderController {
    private final PrintRenderApplicationService service;
    public PrintRenderController(PrintRenderApplicationService service) { this.service = service; }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public CreatedResponse create(@PathVariable UUID projectId, @Valid @RequestBody CreateRequest request) {
        var result = service.create(projectId, new PrintRenderApplicationService.CreateCommand(request.layoutId(),
                request.dpi(), request.bleedMm(), request.tileSize(), request.compression(),
                request.cropMarks(), request.registrationMarks()));
        return new CreatedResponse(Response.from(result.render()), result.jobId(), result.existing());
    }
    @GetMapping public List<Response> list(@PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(projectId, page, size).stream().map(Response::from).toList();
    }
    @GetMapping("/{renderId}") public Response get(@PathVariable UUID projectId, @PathVariable UUID renderId) {
        return Response.from(service.get(projectId, renderId));
    }
    @PostMapping("/{renderId}/decision") public Response decide(@PathVariable UUID projectId,
            @PathVariable UUID renderId, @Valid @RequestBody DecisionRequest request) {
        return Response.from(service.decide(projectId, renderId, request.decision(), request.note()));
    }
    @PostMapping("/{renderId}/download-url") public PresignedResponse download(@PathVariable UUID projectId,
            @PathVariable UUID renderId) { return PresignedResponse.from(service.download(projectId, renderId)); }

    public record CreateRequest(@NotNull UUID layoutId, @Min(72) @Max(2400) int dpi,
            @DecimalMin("0") double bleedMm, @Min(128) @Max(8192) int tileSize,
            @NotNull @Pattern(regexp = "ZSTD|LZW|DEFLATE") String compression,
            boolean cropMarks, boolean registrationMarks) { }
    public record DecisionRequest(@Pattern(regexp = "APPROVED|REJECTED") String decision,
            @NotBlank @Size(max = 500) String note) { }
    public record CreatedResponse(Response render, UUID jobId, boolean existing) { }
    public record Response(UUID id, UUID projectId, UUID layoutId, UUID layoutVersionId, UUID jobId,
            String status, String format, int dpi, double bleedMm, int tileSize, String compression,
            String colorProfile, boolean cropMarks, boolean registrationMarks, int contentWidthPx,
            int contentHeightPx, int outputWidthPx, int outputHeightPx, String inputManifestHash,
            String outputSha256, Long outputByteSize, String outputManifestHash, String errorCode,
            String errorMessage, Instant createdAt, Instant updatedAt, Instant completedAt) {
        static Response from(PrintRenderEntity v) { return new Response(v.getId(), v.getProjectId(), v.getLayoutId(),
                v.getLayoutVersionId(), v.getJobId(), v.getStatus(), v.getFormat(), v.getDpi(), v.getBleedMm(),
                v.getTileSize(), v.getCompression(), v.getColorProfile(), v.isCropMarks(), v.isRegistrationMarks(),
                v.getContentWidthPx(), v.getContentHeightPx(), v.getOutputWidthPx(), v.getOutputHeightPx(),
                v.getInputManifestHash(), v.getOutputSha256(), v.getOutputByteSize(), v.getOutputManifestHash(),
                v.getErrorCode(), v.getErrorMessage(), v.getCreatedAt(), v.getUpdatedAt(), v.getCompletedAt()); }
    }
    public record PresignedResponse(URI url, String method, Map<String, List<String>> headers) {
        static PresignedResponse from(ObjectStorageGateway.PresignedRequest value) {
            return new PresignedResponse(value.url(), value.method(), value.headers());
        }
    }
}
