package com.pics2text.api.application;

import com.pics2text.api.common.ApiException;
import com.pics2text.identity.domain.User;
import com.pics2text.job.application.JobCoordinator;
import com.pics2text.job.domain.JobType;
import com.pics2text.layout.application.AutoLayoutGenerator;
import com.pics2text.layout.domain.AutoLayoutRequest;
import com.pics2text.layout.domain.AutoLayoutStatus;
import com.pics2text.layout.infrastructure.persistence.AutoLayoutAssignmentEntity;
import com.pics2text.layout.infrastructure.persistence.AutoLayoutEntity;
import com.pics2text.layout.infrastructure.persistence.AutoLayoutMapper;
import com.pics2text.layout.infrastructure.persistence.TextMaskDesignEntity;
import com.pics2text.layout.infrastructure.persistence.TextMaskMapper;
import com.pics2text.project.domain.ProjectPermission;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AutoLayoutService {
    private final AutoLayoutMapper mapper;
    private final TextMaskMapper maskMapper;
    private final ProjectAccessService access;
    private final JobCoordinator jobs;
    private final ObjectMapper objectMapper;
    private final AuditService audit;

    public AutoLayoutService(AutoLayoutMapper mapper, TextMaskMapper maskMapper,
            ProjectAccessService access, JobCoordinator jobs, ObjectMapper objectMapper,
            AuditService audit) {
        this.mapper = mapper;
        this.maskMapper = maskMapper;
        this.access = access;
        this.jobs = jobs;
        this.objectMapper = objectMapper;
        this.audit = audit;
    }

    @Transactional
    public CreatedLayout create(UUID projectId, UUID maskId, String name, AutoLayoutRequest request) {
        User actor = access.require(projectId, ProjectPermission.EDIT_LAYOUT);
        if (!"ACTIVE".equals(access.requireProject(projectId).getStatus())) {
            throw new ApiException("PROJECT_ARCHIVED", "已归档项目不能创建自动布局", HttpStatus.CONFLICT);
        }
        if (name == null || name.isBlank() || name.length() > 120) {
            throw bad("LAYOUT_NAME_INVALID", "布局名称不能为空且不能超过120个字符");
        }
        TextMaskDesignEntity mask = maskMapper.selectById(maskId);
        if (mask == null || !projectId.equals(mask.getProjectId())) {
            throw new ApiException("TEXT_MASK_NOT_FOUND", "文字蒙版不存在", HttpStatus.NOT_FOUND);
        }
        if (!"READY".equals(mask.getStatus()) || mask.getSlotCount() <= 0) {
            throw new ApiException("TEXT_MASK_NOT_READY", "文字蒙版尚未生成可用槽位", HttpStatus.CONFLICT);
        }
        Instant now = Instant.now();
        AutoLayoutEntity layout = new AutoLayoutEntity();
        layout.setId(UUID.randomUUID());
        layout.setProjectId(projectId);
        layout.setMaskId(maskId);
        layout.setName(name.trim());
        layout.setStatus(AutoLayoutStatus.PENDING.name());
        layout.setScoringVersion(AutoLayoutGenerator.SCORING_VERSION);
        layout.setMaskContentHash(mask.getContentHash());
        layout.setFontSha256(mask.getFontSha256());
        mapRequest(layout, request);
        layout.setConfigurationHash(hash(mask, request));
        layout.setCandidateCount(0);
        layout.setSlotCount(mask.getSlotCount());
        layout.setAssignedCount(0);
        layout.setCreatedBy(actor.id());
        layout.setCreatedAt(now);
        layout.setUpdatedAt(now);
        mapper.insert(layout);
        try {
            String payload = objectMapper.writeValueAsString(Map.of("layoutId", layout.getId()));
            var job = jobs.enqueue(projectId, actor.id(), JobType.LAYOUT_GENERATION,
                    "AUTO_LAYOUT", layout.getId(), payload, 3, true);
            audit.record(actor.id(), "AUTO_LAYOUT_CREATE", "AUTO_LAYOUT", layout.getId().toString(), true);
            return new CreatedLayout(layout, job.getId());
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法创建自动布局任务", exception);
        }
    }

    public AutoLayoutEntity get(UUID projectId, UUID layoutId) {
        access.require(projectId, ProjectPermission.VIEW);
        return require(projectId, layoutId);
    }

    public List<AutoLayoutEntity> list(UUID projectId, int page, int size) {
        access.require(projectId, ProjectPermission.VIEW);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        return mapper.listByProject(projectId, safeSize, (long) safePage * safeSize);
    }

    public List<AutoLayoutAssignmentEntity> assignments(UUID projectId, UUID layoutId,
            int page, int size) {
        access.require(projectId, ProjectPermission.VIEW);
        AutoLayoutEntity layout = require(projectId, layoutId);
        if (!AutoLayoutStatus.READY.name().equals(layout.getStatus())) {
            throw new ApiException("AUTO_LAYOUT_NOT_READY", "自动布局尚未生成完成", HttpStatus.CONFLICT);
        }
        int safePage = Math.max(0, page);
        int safeSize = Math.min(1000, Math.max(1, size));
        return mapper.listAssignments(layoutId, safeSize, (long) safePage * safeSize);
    }

    @Transactional
    public void delete(UUID projectId, UUID layoutId) {
        User actor = access.require(projectId, ProjectPermission.EDIT_LAYOUT);
        require(projectId, layoutId);
        mapper.deleteById(layoutId);
        audit.record(actor.id(), "AUTO_LAYOUT_DELETE", "AUTO_LAYOUT", layoutId.toString(), true);
    }

    private AutoLayoutEntity require(UUID projectId, UUID layoutId) {
        AutoLayoutEntity layout = mapper.selectById(layoutId);
        if (layout == null || !projectId.equals(layout.getProjectId())) {
            throw new ApiException("AUTO_LAYOUT_NOT_FOUND", "自动布局不存在", HttpStatus.NOT_FOUND);
        }
        return layout;
    }

    private void mapRequest(AutoLayoutEntity target, AutoLayoutRequest request) {
        target.setRandomSeed(request.randomSeed());
        target.setAllowPhotoReuse(request.allowPhotoReuse());
        target.setMaxPhotoReuse(request.maxPhotoReuse());
        target.setTargetLabL(request.targetLabL()); target.setTargetLabA(request.targetLabA());
        target.setTargetLabB(request.targetLabB()); target.setCandidateWindow(request.candidateWindow());
        target.setOptimizationIterations(request.optimizationIterations());
        target.setWeightQuality(request.weights().quality());
        target.setWeightBrightness(request.weights().brightness());
        target.setWeightColor(request.weights().color()); target.setWeightCrop(request.weights().crop());
        target.setWeightDuplicate(request.weights().duplicate());
        target.setWeightAdjacent(request.weights().adjacent());
    }

    private String hash(TextMaskDesignEntity mask, AutoLayoutRequest request) {
        try {
            String canonical = AutoLayoutGenerator.ALGORITHM_VERSION + "|"
                    + AutoLayoutGenerator.SCORING_VERSION + "|" + mask.getContentHash() + "|"
                    + mask.getFontSha256() + "|" + objectMapper.writeValueAsString(request);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (JacksonException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法计算布局配置摘要", exception);
        }
    }

    private ApiException bad(String code, String message) {
        return new ApiException(code, message, HttpStatus.BAD_REQUEST);
    }

    public record CreatedLayout(AutoLayoutEntity layout, UUID jobId) { }
}
