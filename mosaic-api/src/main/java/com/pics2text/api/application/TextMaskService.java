package com.pics2text.api.application;

import com.pics2text.api.common.ApiException;
import com.pics2text.api.infrastructure.persistence.ProjectEntity;
import com.pics2text.api.infrastructure.storage.ObjectStorageGateway;
import com.pics2text.identity.domain.User;
import com.pics2text.job.application.JobCoordinator;
import com.pics2text.job.domain.JobType;
import com.pics2text.layout.application.FontCatalog;
import com.pics2text.layout.domain.TextMaskRequest;
import com.pics2text.layout.domain.TextMaskStatus;
import com.pics2text.layout.infrastructure.persistence.TextMaskDesignEntity;
import com.pics2text.layout.infrastructure.persistence.TextMaskMapper;
import com.pics2text.layout.infrastructure.persistence.TextMaskSlotEntity;
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
public class TextMaskService {
    private final TextMaskMapper mapper;
    private final ProjectAccessService access;
    private final JobCoordinator jobs;
    private final ObjectMapper objectMapper;
    private final ObjectStorageGateway storage;
    private final AuditService audit;

    public TextMaskService(TextMaskMapper mapper, ProjectAccessService access, JobCoordinator jobs,
            ObjectMapper objectMapper, ObjectStorageGateway storage, AuditService audit) {
        this.mapper = mapper;
        this.access = access;
        this.jobs = jobs;
        this.objectMapper = objectMapper;
        this.storage = storage;
        this.audit = audit;
    }

    @Transactional
    public CreatedMask create(UUID projectId, String name, TextMaskRequest request) {
        User actor = access.require(projectId, ProjectPermission.EDIT_LAYOUT);
        ProjectEntity project = access.requireProject(projectId);
        if (!"ACTIVE".equals(project.getStatus())) {
            throw new ApiException("PROJECT_ARCHIVED", "已归档项目不能创建文字蒙版", HttpStatus.CONFLICT);
        }
        if (name == null || name.isBlank() || name.length() > 120) {
            throw bad("MASK_NAME_INVALID", "蒙版名称不能为空且不能超过120个字符");
        }
        if (!FontCatalog.supports(request.fontId())) {
            throw bad("FONT_NOT_FOUND", "字体资产不存在：" + request.fontId());
        }
        var fontAsset = FontCatalog.assets().stream()
                .filter(asset -> asset.id().equals(request.fontId())).findFirst().orElseThrow();
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        TextMaskDesignEntity design = new TextMaskDesignEntity();
        design.setId(id);
        design.setProjectId(projectId);
        design.setName(name.trim());
        mapRequest(design, request);
        design.setFontVersion(fontAsset.version());
        design.setFontSha256(fontAsset.sha256());
        design.setStatus(TextMaskStatus.PENDING.name());
        design.setContentHash(hash(request, fontAsset.sha256()));
        design.setSlotCount(0);
        design.setCreatedBy(actor.id());
        design.setCreatedAt(now);
        design.setUpdatedAt(now);
        mapper.insert(design);
        design.setWarningsJson("[]");
        try {
            String payload = objectMapper.writeValueAsString(Map.of("maskId", id));
            var job = jobs.enqueue(projectId, actor.id(), JobType.TEXT_MASK_GENERATION,
                    "TEXT_MASK", id, payload, 3, true);
            audit.record(actor.id(), "TEXT_MASK_CREATE", "TEXT_MASK", id.toString(), true);
            return new CreatedMask(design, job.getId());
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法创建文字蒙版任务", exception);
        }
    }

    public TextMaskDesignEntity get(UUID projectId, UUID maskId) {
        access.require(projectId, ProjectPermission.VIEW);
        return require(projectId, maskId);
    }

    public List<TextMaskDesignEntity> list(UUID projectId, int page, int size) {
        access.require(projectId, ProjectPermission.VIEW);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return mapper.listByProject(projectId, safeSize, (long) safePage * safeSize);
    }

    public List<TextMaskSlotEntity> slots(UUID projectId, UUID maskId, int page, int size) {
        access.require(projectId, ProjectPermission.VIEW);
        TextMaskDesignEntity mask = require(projectId, maskId);
        if (!TextMaskStatus.READY.name().equals(mask.getStatus())) {
            throw new ApiException("TEXT_MASK_NOT_READY", "文字蒙版尚未生成完成", HttpStatus.CONFLICT);
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 1000);
        return mapper.listSlots(maskId, safeSize, (long) safePage * safeSize);
    }

    public ObjectStorageGateway.PresignedRequest preview(UUID projectId, UUID maskId, boolean debug) {
        access.require(projectId, ProjectPermission.VIEW);
        TextMaskDesignEntity mask = require(projectId, maskId);
        if (!TextMaskStatus.READY.name().equals(mask.getStatus())) {
            throw new ApiException("TEXT_MASK_NOT_READY", "文字蒙版尚未生成完成", HttpStatus.CONFLICT);
        }
        String key = debug ? mask.getDebugKey() : mask.getMaskKey();
        String filename = debug ? "text-mask-debug.png" : "text-mask.png";
        return storage.presignGet(key, filename);
    }

    @Transactional
    public void delete(UUID projectId, UUID maskId) {
        User actor = access.require(projectId, ProjectPermission.EDIT_LAYOUT);
        TextMaskDesignEntity mask = require(projectId, maskId);
        deleteObject(mask.getMaskKey());
        deleteObject(mask.getDebugKey());
        mapper.deleteById(maskId);
        audit.record(actor.id(), "TEXT_MASK_DELETE", "TEXT_MASK", maskId.toString(), true);
    }

    private void deleteObject(String key) {
        if (key != null && !key.isBlank()) {
            try { storage.delete(key); } catch (RuntimeException ignored) { }
        }
    }

    private TextMaskDesignEntity require(UUID projectId, UUID maskId) {
        TextMaskDesignEntity design = mapper.selectById(maskId);
        if (design == null || !projectId.equals(design.getProjectId())) {
            throw new ApiException("TEXT_MASK_NOT_FOUND", "文字蒙版不存在", HttpStatus.NOT_FOUND);
        }
        return design;
    }

    private void mapRequest(TextMaskDesignEntity target, TextMaskRequest source) {
        target.setTextContent(source.text()); target.setDirection(source.direction().name());
        target.setFontId(source.fontId()); target.setFontSize(source.fontSize());
        target.setFontWeight(source.fontWeight()); target.setLetterSpacing(source.letterSpacing());
        target.setLineSpacing(source.lineSpacing()); target.setCanvasWidth(source.canvasWidth());
        target.setCanvasHeight(source.canvasHeight()); target.setPadding(source.padding());
        target.setGridStrategy(source.gridStrategy().name()); target.setEdgePolicy(source.edgePolicy().name());
        target.setCellWidth(source.cellWidth()); target.setCellHeight(source.cellHeight());
        target.setGap(source.gap()); target.setCoverageThreshold(source.coverageThreshold());
    }

    private String hash(TextMaskRequest request, String fontSha256) {
        try {
            String value = com.pics2text.layout.application.TextMaskGenerator.ALGORITHM_VERSION
                    + "|" + fontSha256 + "|" + objectMapper.writeValueAsString(request);
            byte[] canonical = value.getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JacksonException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法计算蒙版配置摘要", exception);
        }
    }

    private ApiException bad(String code, String message) {
        return new ApiException(code, message, HttpStatus.BAD_REQUEST);
    }

    public record CreatedMask(TextMaskDesignEntity mask, UUID jobId) { }
}
