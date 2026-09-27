package com.pics2text.api.interfaces;

import com.pics2text.api.application.TextMaskService;
import com.pics2text.api.common.ApiException;
import com.pics2text.api.infrastructure.storage.ObjectStorageGateway;
import com.pics2text.layout.application.FontCatalog;
import com.pics2text.layout.domain.EdgePolicy;
import com.pics2text.layout.domain.FontAsset;
import com.pics2text.layout.domain.GridStrategy;
import com.pics2text.layout.domain.TextDirection;
import com.pics2text.layout.domain.TextMaskRequest;
import com.pics2text.layout.infrastructure.persistence.TextMaskDesignEntity;
import com.pics2text.layout.infrastructure.persistence.TextMaskSlotEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@RequestMapping("/api/v1")
public class TextMaskController {
    private final TextMaskService service;

    public TextMaskController(TextMaskService service) {
        this.service = service;
    }

    @GetMapping("/layout/fonts")
    public List<FontAsset> fonts() {
        return FontCatalog.assets();
    }

    @PostMapping("/projects/{projectId}/text-masks")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedMaskResponse create(@PathVariable UUID projectId,
            @Valid @RequestBody CreateTextMaskRequest request) {
        try {
            TextMaskRequest domain = request.toDomain();
            var created = service.create(projectId, request.name(), domain);
            return new CreatedMaskResponse(TextMaskResponse.from(created.mask()), created.jobId());
        } catch (IllegalArgumentException exception) {
            throw new ApiException("TEXT_MASK_PARAMETERS_INVALID", exception.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/projects/{projectId}/text-masks")
    public List<TextMaskResponse> list(@PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(projectId, page, size).stream().map(TextMaskResponse::from).toList();
    }

    @GetMapping("/projects/{projectId}/text-masks/{maskId}")
    public TextMaskResponse get(@PathVariable UUID projectId, @PathVariable UUID maskId) {
        return TextMaskResponse.from(service.get(projectId, maskId));
    }

    @GetMapping("/projects/{projectId}/text-masks/{maskId}/slots")
    public List<TextMaskSlotEntity> slots(@PathVariable UUID projectId, @PathVariable UUID maskId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "200") int size) {
        return service.slots(projectId, maskId, page, size);
    }

    @PostMapping("/projects/{projectId}/text-masks/{maskId}/preview-url")
    public PresignedResponse preview(@PathVariable UUID projectId, @PathVariable UUID maskId,
            @RequestParam(defaultValue = "false") boolean debug) {
        return PresignedResponse.from(service.preview(projectId, maskId, debug));
    }

    public record CreateTextMaskRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 400) String text,
            @NotNull TextDirection direction,
            @NotBlank @Size(max = 80) String fontId,
            @Min(16) @Max(2048) int fontSize,
            @Min(100) @Max(900) int fontWeight,
            @DecimalMin("-20") @DecimalMax("200") double letterSpacing,
            @DecimalMin("0.5") @DecimalMax("3.0") double lineSpacing,
            @Min(256) @Max(4096) int canvasWidth,
            @Min(256) @Max(4096) int canvasHeight,
            @Min(0) @Max(2048) int padding,
            @NotNull GridStrategy gridStrategy,
            @NotNull EdgePolicy edgePolicy,
            @Min(4) @Max(512) int cellWidth,
            @Min(4) @Max(512) int cellHeight,
            @Min(0) @Max(128) int gap,
            @DecimalMin("0.5") @DecimalMax("0.98") double coverageThreshold) {
        TextMaskRequest toDomain() {
            return new TextMaskRequest(text, direction, fontId, fontSize, fontWeight,
                    letterSpacing, lineSpacing, canvasWidth, canvasHeight, padding,
                    gridStrategy, edgePolicy, cellWidth, cellHeight, gap, coverageThreshold);
        }
    }

    public record CreatedMaskResponse(TextMaskResponse mask, UUID jobId) { }

    public record TextMaskResponse(UUID id, UUID projectId, String name, String text,
            String direction, String fontId, String fontVersion, String fontSha256,
            int fontSize, int fontWeight,
            double letterSpacing, double lineSpacing, int canvasWidth, int canvasHeight,
            int padding, String gridStrategy, String edgePolicy, int cellWidth, int cellHeight,
            int gap, double coverageThreshold, String status, String algorithmVersion,
            String contentHash, Integer boundsX, Integer boundsY, Integer boundsWidth,
            Integer boundsHeight, Double estimatedStrokeWidth, Double averageCoverage,
            int slotCount, String warningsJson, String errorCode, String errorMessage,
            UUID createdBy, Instant createdAt, Instant updatedAt, Instant completedAt) {
        static TextMaskResponse from(TextMaskDesignEntity mask) {
            return new TextMaskResponse(mask.getId(), mask.getProjectId(), mask.getName(),
                    mask.getTextContent(), mask.getDirection(), mask.getFontId(), mask.getFontVersion(),
                    mask.getFontSha256(), mask.getFontSize(),
                    mask.getFontWeight(), mask.getLetterSpacing(), mask.getLineSpacing(),
                    mask.getCanvasWidth(), mask.getCanvasHeight(), mask.getPadding(),
                    mask.getGridStrategy(), mask.getEdgePolicy(), mask.getCellWidth(),
                    mask.getCellHeight(), mask.getGap(), mask.getCoverageThreshold(), mask.getStatus(),
                    mask.getAlgorithmVersion(), mask.getContentHash(), mask.getBoundsX(), mask.getBoundsY(),
                    mask.getBoundsWidth(), mask.getBoundsHeight(), mask.getEstimatedStrokeWidth(),
                    mask.getAverageCoverage(), mask.getSlotCount(), mask.getWarningsJson(),
                    mask.getErrorCode(), mask.getErrorMessage(), mask.getCreatedBy(), mask.getCreatedAt(),
                    mask.getUpdatedAt(), mask.getCompletedAt());
        }
    }

    public record PresignedResponse(URI url, String method, Map<String, List<String>> headers) {
        static PresignedResponse from(ObjectStorageGateway.PresignedRequest request) {
            return new PresignedResponse(request.url(), request.method(), request.headers());
        }
    }
}
