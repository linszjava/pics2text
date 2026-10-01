package com.pics2text.api.interfaces;

import com.pics2text.api.application.SurfaceApplicationService;
import com.pics2text.surface.domain.SurfaceRegionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api/v1/projects/{projectId}")
public class SurfaceController {
    private final SurfaceApplicationService service;

    public SurfaceController(SurfaceApplicationService service) { this.service = service; }

    @PostMapping("/surface-templates")
    @ResponseStatus(HttpStatus.CREATED)
    public SurfaceApplicationService.SurfaceTemplateView create(@PathVariable UUID projectId,
            @Valid @RequestBody CreateSurfaceTemplateRequest request) {
        return service.createTemplate(projectId, request.toCommand());
    }

    @GetMapping("/surface-templates")
    public List<SurfaceApplicationService.SurfaceTemplateView> list(@PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.listTemplates(projectId, page, size);
    }

    @GetMapping("/surface-templates/{templateId}")
    public SurfaceApplicationService.SurfaceTemplateView get(@PathVariable UUID projectId,
            @PathVariable UUID templateId) {
        return service.getTemplate(projectId, templateId);
    }

    @PostMapping("/editable-layouts/{layoutId}/surface-binding")
    public SurfaceApplicationService.BindingView bind(@PathVariable UUID projectId,
            @PathVariable UUID layoutId, @Valid @RequestBody BindSurfaceRequest request) {
        return service.bind(projectId, layoutId, request.toCommand());
    }

    @GetMapping("/editable-layouts/{layoutId}/surface-binding")
    public SurfaceApplicationService.BindingView binding(@PathVariable UUID projectId,
            @PathVariable UUID layoutId) {
        return service.getBinding(projectId, layoutId);
    }

    @GetMapping("/editable-layouts/{layoutId}/surface-preview-data")
    public SurfaceApplicationService.PreviewData previewData(@PathVariable UUID projectId,
            @PathVariable UUID layoutId) {
        return service.previewData(projectId, layoutId);
    }

    public record CreateSurfaceTemplateRequest(@NotBlank @Size(max = 120) String name,
            @DecimalMin(value = "0", inclusive = false) double diameterMm,
            @DecimalMin(value = "0", inclusive = false) double axialLengthMm,
            @DecimalMin("0") double seamXmm, @DecimalMin("0") double seamSafetyMm,
            @DecimalMin("0") double safeTopMm, @DecimalMin("0") double safeRightMm,
            @DecimalMin("0") double safeBottomMm, @DecimalMin("0") double safeLeftMm,
            @NotNull @Size(max = 500) List<@Valid SurfaceRegionRequest> regions) {
        SurfaceApplicationService.CreateTemplate toCommand() {
            return new SurfaceApplicationService.CreateTemplate(name, diameterMm, axialLengthMm,
                    seamXmm, seamSafetyMm, safeTopMm, safeRightMm, safeBottomMm, safeLeftMm,
                    regions.stream().map(SurfaceRegionRequest::toCommand).toList());
        }
    }

    public record SurfaceRegionRequest(@NotNull SurfaceRegionType type,
            @NotBlank @Size(max = 120) String name, @DecimalMin("0") double xMm,
            @DecimalMin("0") double yMm,
            @DecimalMin(value = "0", inclusive = false) double widthMm,
            @DecimalMin(value = "0", inclusive = false) double heightMm,
            Map<String, Object> metadata) {
        SurfaceApplicationService.RegionInput toCommand() {
            return new SurfaceApplicationService.RegionInput(type, name, xMm, yMm,
                    widthMm, heightMm, metadata);
        }
    }

    public record BindSurfaceRequest(@NotNull UUID templateId, @NotNull UUID layoutVersionId,
            @DecimalMin("0") double originXmm, @DecimalMin("0") double originYmm,
            @DecimalMin(value = "0", inclusive = false) double printWidthMm,
            @DecimalMin(value = "0", inclusive = false) double printHeightMm) {
        SurfaceApplicationService.BindCommand toCommand() {
            return new SurfaceApplicationService.BindCommand(templateId, layoutVersionId,
                    originXmm, originYmm, printWidthMm, printHeightMm);
        }
    }
}
