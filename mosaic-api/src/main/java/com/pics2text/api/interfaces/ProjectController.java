package com.pics2text.api.interfaces;

import com.pics2text.api.application.ProjectService;
import com.pics2text.api.infrastructure.persistence.ProjectEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@Valid @RequestBody CreateProjectRequest request) {
        return ProjectResponse.from(projectService.create(request.name()));
    }

    @GetMapping("/{projectId}")
    public ProjectResponse get(@PathVariable UUID projectId) {
        return ProjectResponse.from(projectService.get(projectId));
    }

    @GetMapping
    public List<ProjectResponse> list() {
        return projectService.list().stream().map(ProjectResponse::from).toList();
    }

    @PatchMapping("/{projectId}")
    public ProjectResponse update(@PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectRequest request) {
        return ProjectResponse.from(projectService.update(projectId, request.name(), request.description(),
                request.configurationJson(), request.retentionDays(), request.maxPhotoBytes()));
    }

    @PostMapping("/{projectId}/archive")
    public ProjectResponse archive(@PathVariable UUID projectId) {
        return ProjectResponse.from(projectService.setArchived(projectId, true));
    }

    @PostMapping("/{projectId}/activate")
    public ProjectResponse activate(@PathVariable UUID projectId) {
        return ProjectResponse.from(projectService.setArchived(projectId, false));
    }

    @PutMapping("/{projectId}/active-text-mask")
    public ProjectResponse selectTextMask(@PathVariable UUID projectId,
            @Valid @RequestBody SelectTextMaskRequest request) {
        return ProjectResponse.from(projectService.selectTextMask(projectId, request.maskId()));
    }

    public record CreateProjectRequest(@NotBlank @Size(max = 120) String name) {
    }

    public record SelectTextMaskRequest(@jakarta.validation.constraints.NotNull UUID maskId) { }

    public record UpdateProjectRequest(
            @Size(max = 120) String name,
            @Size(max = 1000) String description,
            @Size(max = 20000) String configurationJson,
            @Min(1) @Max(3650) Integer retentionDays,
            @Min(1024) @Max(5368709120L) Long maxPhotoBytes) {
    }

    public record ProjectResponse(
            UUID id,
            String name,
            String status,
            String description,
            String configurationJson,
            Integer retentionDays,
            Long maxPhotoBytes,
            UUID activeLayoutId,
            UUID activeTextMaskId,
            Instant archivedAt,
            UUID createdBy,
            Instant createdAt,
            Instant updatedAt) {

        static ProjectResponse from(ProjectEntity project) {
            return new ProjectResponse(
                    project.getId(),
                    project.getName(),
                    project.getStatus(),
                    project.getDescription(),
                    project.getConfigurationJson(),
                    project.getRetentionDays(),
                    project.getMaxPhotoBytes(),
                    project.getActiveLayoutId(),
                    project.getActiveTextMaskId(),
                    project.getArchivedAt(),
                    project.getCreatedBy(),
                    project.getCreatedAt(),
                    project.getUpdatedAt());
        }
    }

}
