package com.pics2text.api.interfaces;

import com.pics2text.api.application.AutoLayoutService;
import com.pics2text.api.common.ApiException;
import com.pics2text.layout.domain.AutoLayoutRequest;
import com.pics2text.layout.domain.LayoutWeights;
import com.pics2text.layout.infrastructure.persistence.AutoLayoutAssignmentEntity;
import com.pics2text.layout.infrastructure.persistence.AutoLayoutEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
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
@RequestMapping("/api/v1/projects/{projectId}/layouts")
public class AutoLayoutController {
    private final AutoLayoutService service;

    public AutoLayoutController(AutoLayoutService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedLayoutResponse create(@PathVariable UUID projectId,
            @Valid @RequestBody CreateAutoLayoutRequest request) {
        try {
            var created = service.create(projectId, request.maskId(), request.name(), request.toDomain());
            return new CreatedLayoutResponse(LayoutResponse.from(created.layout()), created.jobId());
        } catch (IllegalArgumentException exception) {
            throw new ApiException("AUTO_LAYOUT_PARAMETERS_INVALID", exception.getMessage(), HttpStatus.BAD_REQUEST);
        }
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

    @GetMapping("/{layoutId}/assignments")
    public List<AutoLayoutAssignmentEntity> assignments(@PathVariable UUID projectId,
            @PathVariable UUID layoutId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size) {
        return service.assignments(projectId, layoutId, page, size);
    }

    public record CreateAutoLayoutRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull UUID maskId,
            long randomSeed,
            boolean allowPhotoReuse,
            @Min(1) @Max(100) int maxPhotoReuse,
            @DecimalMin("0") @DecimalMax("255") double targetLabL,
            @DecimalMin("0") @DecimalMax("255") double targetLabA,
            @DecimalMin("0") @DecimalMax("255") double targetLabB,
            @Min(8) @Max(512) int candidateWindow,
            @Min(0) @Max(200000) int optimizationIterations,
            @NotNull @Valid WeightRequest weights) {
        AutoLayoutRequest toDomain() {
            return new AutoLayoutRequest(randomSeed, allowPhotoReuse, maxPhotoReuse,
                    targetLabL, targetLabA, targetLabB, candidateWindow, optimizationIterations,
                    weights.toDomain());
        }
    }

    public record WeightRequest(
            @DecimalMin("0") @DecimalMax("10") double quality,
            @DecimalMin("0") @DecimalMax("10") double brightness,
            @DecimalMin("0") @DecimalMax("10") double color,
            @DecimalMin("0") @DecimalMax("10") double crop,
            @DecimalMin("0") @DecimalMax("10") double duplicate,
            @DecimalMin("0") @DecimalMax("10") double adjacent) {
        LayoutWeights toDomain() {
            return new LayoutWeights(quality, brightness, color, crop, duplicate, adjacent);
        }
    }

    public record CreatedLayoutResponse(LayoutResponse layout, UUID jobId) { }

    public record LayoutResponse(UUID id, UUID projectId, UUID maskId, String name, String status,
            String algorithmVersion, String scoringVersion, String configurationHash,
            String maskContentHash, String fontSha256, String photoSetHash, long randomSeed,
            boolean allowPhotoReuse, int maxPhotoReuse, double targetLabL, double targetLabA,
            double targetLabB, int candidateWindow, int optimizationIterations,
            double weightQuality, double weightBrightness, double weightColor, double weightCrop,
            double weightDuplicate, double weightAdjacent, int candidateCount, int slotCount,
            int assignedCount, Double initialAverageScore, Double finalAverageScore,
            Double averageBrightnessScore, Double averageColorScore, Double averageCropScore,
            Double averageAdjacentScore, Integer uniquePhotoCount, Integer duplicateGroupReuseCount,
            Long candidateEvaluations, Long estimatedMemoryBytes, Long runtimeMillis,
            String errorCode, String errorMessage, UUID createdBy, Instant createdAt,
            Instant updatedAt, Instant completedAt) {
        static LayoutResponse from(AutoLayoutEntity value) {
            return new LayoutResponse(value.getId(), value.getProjectId(), value.getMaskId(), value.getName(),
                    value.getStatus(), value.getAlgorithmVersion(), value.getScoringVersion(),
                    value.getConfigurationHash(), value.getMaskContentHash(), value.getFontSha256(),
                    value.getPhotoSetHash(), value.getRandomSeed(), value.getAllowPhotoReuse(),
                    value.getMaxPhotoReuse(), value.getTargetLabL(), value.getTargetLabA(),
                    value.getTargetLabB(), value.getCandidateWindow(), value.getOptimizationIterations(),
                    value.getWeightQuality(), value.getWeightBrightness(), value.getWeightColor(),
                    value.getWeightCrop(), value.getWeightDuplicate(), value.getWeightAdjacent(),
                    value.getCandidateCount(), value.getSlotCount(), value.getAssignedCount(),
                    value.getInitialAverageScore(), value.getFinalAverageScore(),
                    value.getAverageBrightnessScore(), value.getAverageColorScore(),
                    value.getAverageCropScore(), value.getAverageAdjacentScore(), value.getUniquePhotoCount(),
                    value.getDuplicateGroupReuseCount(), value.getCandidateEvaluations(),
                    value.getEstimatedMemoryBytes(), value.getRuntimeMillis(), value.getErrorCode(),
                    value.getErrorMessage(), value.getCreatedBy(), value.getCreatedAt(), value.getUpdatedAt(),
                    value.getCompletedAt());
        }
    }
}
