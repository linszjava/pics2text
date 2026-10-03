package com.pics2text.api.application;

import com.pics2text.api.common.ApiException;
import com.pics2text.api.infrastructure.storage.ObjectStorageGateway;
import com.pics2text.identity.domain.User;
import com.pics2text.job.application.JobCoordinator;
import com.pics2text.job.domain.JobType;
import com.pics2text.layout.application.LayoutEditCommand;
import com.pics2text.layout.domain.CropBox;
import com.pics2text.layout.domain.EditableLayoutState;
import com.pics2text.layout.domain.LayoutCommandType;
import com.pics2text.layout.domain.LayoutEditorPolicy;
import com.pics2text.layout.domain.LayoutVersionReason;
import com.pics2text.layout.infrastructure.persistence.AutoLayoutEntity;
import com.pics2text.layout.infrastructure.persistence.AutoLayoutMapper;
import com.pics2text.layout.infrastructure.persistence.EditableLayoutEntity;
import com.pics2text.layout.infrastructure.persistence.LayoutEditOperationEntity;
import com.pics2text.layout.infrastructure.persistence.LayoutEditorAssignmentEntity;
import com.pics2text.layout.infrastructure.persistence.LayoutEditorMapper;
import com.pics2text.layout.infrastructure.persistence.LayoutPreviewEntity;
import com.pics2text.layout.infrastructure.persistence.LayoutPhotoPreviewEntity;
import com.pics2text.layout.infrastructure.persistence.LayoutReplacementPhotoEntity;
import com.pics2text.layout.infrastructure.persistence.LayoutVersionEntity;
import com.pics2text.layout.infrastructure.persistence.TextMaskDesignEntity;
import com.pics2text.layout.infrastructure.persistence.TextMaskMapper;
import com.pics2text.project.domain.ProjectPermission;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class LayoutEditorService {
    private static final int MAX_BATCH_COMMANDS = 100;
    private final LayoutEditorMapper mapper;
    private final AutoLayoutMapper autoLayoutMapper;
    private final TextMaskMapper maskMapper;
    private final ProjectAccessService access;
    private final JobCoordinator jobs;
    private final ObjectMapper objectMapper;
    private final ObjectStorageGateway storage;
    private final AuditService audit;
    private final SurfaceApplicationService surfaces;

    public LayoutEditorService(LayoutEditorMapper mapper, AutoLayoutMapper autoLayoutMapper,
            TextMaskMapper maskMapper, ProjectAccessService access, JobCoordinator jobs,
            ObjectMapper objectMapper, ObjectStorageGateway storage, AuditService audit,
            SurfaceApplicationService surfaces) {
        this.mapper = mapper;
        this.autoLayoutMapper = autoLayoutMapper;
        this.maskMapper = maskMapper;
        this.access = access;
        this.jobs = jobs;
        this.objectMapper = objectMapper;
        this.storage = storage;
        this.audit = audit;
        this.surfaces = surfaces;
    }

    @Transactional
    public EditableLayoutEntity create(UUID projectId, UUID sourceAutoLayoutId, String name) {
        User actor = access.require(projectId, ProjectPermission.EDIT_LAYOUT);
        if (!"ACTIVE".equals(access.requireProject(projectId).getStatus())) {
            throw conflict("PROJECT_ARCHIVED", "已归档项目不能创建可编辑布局");
        }
        if (name == null || name.isBlank() || name.length() > 120) {
            throw bad("LAYOUT_NAME_INVALID", "布局名称不能为空且不能超过120个字符");
        }
        AutoLayoutEntity source = autoLayoutMapper.selectById(sourceAutoLayoutId);
        if (source == null || !projectId.equals(source.getProjectId())) {
            throw new ApiException("AUTO_LAYOUT_NOT_FOUND", "自动布局不存在", HttpStatus.NOT_FOUND);
        }
        if (!"READY".equals(source.getStatus()) || source.getAssignedCount() == null
                || source.getAssignedCount() <= 0) {
            throw conflict("AUTO_LAYOUT_NOT_READY", "自动布局尚未生成完成");
        }
        if (mapper.findBySource(sourceAutoLayoutId) != null) {
            throw conflict("EDITABLE_LAYOUT_ALREADY_EXISTS", "该自动布局已经创建过可编辑布局");
        }
        TextMaskDesignEntity mask = maskMapper.selectById(source.getMaskId());
        if (mask == null || !"READY".equals(mask.getStatus())) {
            throw conflict("TEXT_MASK_NOT_READY", "自动布局关联的文字蒙版不可用");
        }
        Instant now = Instant.now();
        EditableLayoutEntity layout = new EditableLayoutEntity();
        layout.setId(UUID.randomUUID()); layout.setProjectId(projectId);
        layout.setSourceAutoLayoutId(sourceAutoLayoutId); layout.setMaskId(source.getMaskId());
        layout.setName(name.trim()); layout.setState(EditableLayoutState.DRAFT.name());
        layout.setCanvasWidth(mask.getCanvasWidth()); layout.setCanvasHeight(mask.getCanvasHeight());
        layout.setAllowPhotoReuse(source.getAllowPhotoReuse());
        layout.setMaxPhotoReuse(source.getMaxPhotoReuse()); layout.setCurrentRevision(0L);
        layout.setLockVersion(0L); layout.setCurrentVersionNumber(0);
        layout.setCreatedBy(actor.id()); layout.setCreatedAt(now); layout.setUpdatedAt(now);
        try {
            mapper.insertLayout(layout);
        } catch (DuplicateKeyException exception) {
            throw conflict("EDITABLE_LAYOUT_ALREADY_EXISTS", "该自动布局已经创建过可编辑布局");
        }
        int copied = mapper.copyAutoAssignments(layout.getId(), sourceAutoLayoutId, source.getMaskId());
        if (copied != source.getAssignedCount()) {
            throw new IllegalStateException("自动布局分配数量与复制结果不一致");
        }
        createVersion(layout, LayoutVersionReason.IMPORT, null, "从自动布局导入", actor.id(), now);
        audit.record(actor.id(), "EDITABLE_LAYOUT_CREATE", "EDITABLE_LAYOUT", layout.getId().toString(), true);
        return mapper.findLayout(layout.getId());
    }

    public EditableLayoutEntity get(UUID projectId, UUID layoutId) {
        access.require(projectId, ProjectPermission.VIEW);
        return requireLayout(projectId, layoutId);
    }

    public List<EditableLayoutEntity> list(UUID projectId, int page, int size) {
        access.require(projectId, ProjectPermission.VIEW);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        return mapper.listLayouts(projectId, safeSize, (long) safePage * safeSize);
    }

    @Transactional
    public EditableLayoutEntity rename(UUID projectId, UUID layoutId, String name,
            long expectedLockVersion) {
        User actor = access.require(projectId, ProjectPermission.EDIT_LAYOUT);
        EditableLayoutEntity layout = requireLayout(projectId, layoutId);
        if (name == null || name.isBlank() || name.length() > 120) {
            throw bad("LAYOUT_NAME_INVALID", "布局名称不能为空且不能超过120个字符");
        }
        if (mapper.renameLayout(layoutId, name.trim(), expectedLockVersion, Instant.now()) == 0) {
            throw conflict("LAYOUT_UPDATE_CONFLICT", "布局已被其他用户修改，请刷新后重试");
        }
        audit.record(actor.id(), "EDITABLE_LAYOUT_RENAME", "EDITABLE_LAYOUT", layoutId.toString(), true);
        return mapper.findLayout(layoutId);
    }

    @Transactional
    public EditableLayoutEntity activate(UUID projectId, UUID layoutId) {
        User actor = access.require(projectId, ProjectPermission.EDIT_LAYOUT);
        EditableLayoutEntity layout = requireLayout(projectId, layoutId);
        if (EditableLayoutState.DRAFT.name().equals(layout.getState())) {
            throw conflict("DRAFT_LAYOUT_ACTIVATE_FORBIDDEN", "草稿布局不能设为当前使用，请先完成并锁定布局");
        }
        mapper.activateLayout(projectId, layoutId, Instant.now());
        audit.record(actor.id(), "EDITABLE_LAYOUT_ACTIVATE", "EDITABLE_LAYOUT", layoutId.toString(), true);
        return layout;
    }

    @Transactional
    public void delete(UUID projectId, UUID layoutId) {
        User actor = access.require(projectId, ProjectPermission.EDIT_LAYOUT);
        EditableLayoutEntity layout = requireLayout(projectId, layoutId);
        if (EditableLayoutState.PUBLISHED.name().equals(layout.getState())) {
            throw conflict("PUBLISHED_LAYOUT_DELETE_FORBIDDEN", "已发布布局不能删除");
        }
        if (mapper.countSurfaceBindings(layoutId) > 0) {
            throw conflict("BOUND_LAYOUT_DELETE_FORBIDDEN", "已绑定三维曲面的布局不能删除，请先解除绑定");
        }
        mapper.clearVersionReferences(layoutId);
        if (mapper.deleteLayout(layoutId) == 0) {
            throw new ApiException("EDITABLE_LAYOUT_NOT_FOUND", "可编辑布局不存在", HttpStatus.NOT_FOUND);
        }
        audit.record(actor.id(), "EDITABLE_LAYOUT_DELETE", "EDITABLE_LAYOUT", layoutId.toString(), true);
    }

    public List<LayoutEditorAssignmentEntity> assignments(UUID projectId, UUID layoutId,
            UUID photoId, Integer slotIndex, Integer minX, Integer minY, Integer maxX,
            Integer maxY, int limit) {
        access.require(projectId, ProjectPermission.VIEW);
        EditableLayoutEntity layout = requireLayout(projectId, layoutId);
        boolean anyBounds = minX != null || minY != null || maxX != null || maxY != null;
        boolean allBounds = minX != null && minY != null && maxX != null && maxY != null;
        if (anyBounds && !allBounds) throw bad("VIEWPORT_INVALID", "可视区域必须同时提供四个边界");
        if (allBounds && (minX < 0 || minY < 0 || maxX <= minX || maxY <= minY
                || maxX > layout.getCanvasWidth() || maxY > layout.getCanvasHeight())) {
            throw bad("VIEWPORT_INVALID", "可视区域超出布局画布");
        }
        if (slotIndex != null && slotIndex < 0) throw bad("SLOT_INVALID", "槽位索引不能为负数");
        return mapper.queryDraft(layoutId, photoId, slotIndex, minX, minY, maxX, maxY,
                Math.min(5000, Math.max(1, limit)));
    }

    public List<PhotoPreviewUrl> photoPreviewUrls(UUID projectId, UUID layoutId, List<UUID> photoIds) {
        access.require(projectId, ProjectPermission.VIEW);
        requireLayout(projectId, layoutId);
        if (photoIds == null || photoIds.isEmpty() || photoIds.size() > 200
                || photoIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw bad("PHOTO_PREVIEW_BATCH_INVALID", "照片预览批次数量必须在1到200之间");
        }
        List<UUID> unique = photoIds.stream().distinct().toList();
        List<LayoutPhotoPreviewEntity> photos = mapper.listPhotoPreviews(layoutId, unique);
        if (photos.size() != unique.size()) {
            throw bad("PHOTO_NOT_IN_LAYOUT", "部分照片不存在或不属于当前布局");
        }
        return photos.stream().map(photo -> {
            var request = storage.presignGet(photo.previewKey(), "layout-photo.webp");
            return new PhotoPreviewUrl(photo.photoId(), request.url(), request.headers());
        }).toList();
    }

    @Transactional
    public EditResult edit(UUID projectId, UUID layoutId, long expectedLockVersion,
            UUID clientBatchId, String note, List<LayoutEditCommand> commands) {
        User actor = access.require(projectId, ProjectPermission.EDIT_LAYOUT);
        if (clientBatchId == null) throw bad("CLIENT_BATCH_REQUIRED", "自动保存批次ID不能为空");
        if (commands == null || commands.isEmpty() || commands.size() > MAX_BATCH_COMMANDS) {
            throw bad("EDIT_BATCH_INVALID", "每批编辑命令数量必须在1到100之间");
        }
        validateNote(note);
        EditableLayoutEntity layout = requireLayout(projectId, layoutId);
        LayoutVersionEntity existing = mapper.findVersionByBatch(layoutId, clientBatchId);
        if (existing != null) return new EditResult(mapper.findLayout(layoutId), existing, true);
        requireEditable(layout);
        Instant now = Instant.now();
        if (mapper.beginEdit(layoutId, expectedLockVersion, commands.size(), now) == 0) {
            throw conflict("LAYOUT_EDIT_CONFLICT", "布局已被其他用户修改，请刷新后重试");
        }
        for (int index = 0; index < commands.size(); index++) {
            long revision = layout.getCurrentRevision() + index + 1;
            apply(layout, commands.get(index), clientBatchId, index, revision,
                    expectedLockVersion, actor.id(), now);
        }
        EditableLayoutEntity advanced = mapper.findLayout(layoutId);
        LayoutVersionEntity version = createVersion(advanced, LayoutVersionReason.AUTO_SAVE,
                clientBatchId, note, actor.id(), now);
        audit.record(actor.id(), "LAYOUT_AUTO_SAVE", "EDITABLE_LAYOUT", layoutId.toString(), true);
        return new EditResult(mapper.findLayout(layoutId), version, false);
    }

    @Transactional
    public EditResult restore(UUID projectId, UUID layoutId, UUID versionId,
            long expectedLockVersion, UUID clientBatchId, String note) {
        User actor = access.require(projectId, ProjectPermission.EDIT_LAYOUT);
        if (clientBatchId == null) throw bad("CLIENT_BATCH_REQUIRED", "恢复批次ID不能为空");
        validateNote(note);
        EditableLayoutEntity layout = requireLayout(projectId, layoutId);
        LayoutVersionEntity existing = mapper.findVersionByBatch(layoutId, clientBatchId);
        if (existing != null) return new EditResult(mapper.findLayout(layoutId), existing, true);
        requireEditable(layout);
        LayoutVersionEntity target = requireVersion(layoutId, versionId);
        Instant now = Instant.now();
        if (mapper.beginEdit(layoutId, expectedLockVersion, 1, now) == 0) {
            throw conflict("LAYOUT_EDIT_CONFLICT", "布局已被其他用户修改，请刷新后重试");
        }
        mapper.deleteDraft(layoutId);
        int restored = mapper.restoreDraft(layoutId, versionId);
        if (restored != target.assignmentCount()) throw new IllegalStateException("历史版本恢复数量不一致");
        Map<String, Object> before = Map.of("versionId", String.valueOf(layout.getCurrentVersionId()),
                "versionNumber", layout.getCurrentVersionNumber());
        Map<String, Object> after = Map.of("versionId", versionId.toString(),
                "versionNumber", target.versionNumber());
        mapper.insertOperation(new LayoutEditOperationEntity(UUID.randomUUID(), layoutId,
                layout.getCurrentRevision() + 1, clientBatchId, 0, LayoutCommandType.RESTORE.name(),
                null, null, json(before), json(after), expectedLockVersion,
                expectedLockVersion + 1, actor.id(), now));
        EditableLayoutEntity advanced = mapper.findLayout(layoutId);
        LayoutVersionEntity version = createVersion(advanced, LayoutVersionReason.RESTORE,
                clientBatchId, note, actor.id(), now);
        audit.record(actor.id(), "LAYOUT_VERSION_RESTORE", "LAYOUT_VERSION", versionId.toString(), true);
        return new EditResult(mapper.findLayout(layoutId), version, false);
    }

    @Transactional
    public TransitionResult lock(UUID projectId, UUID layoutId, long expectedLockVersion, String note) {
        User actor = access.require(projectId, ProjectPermission.EDIT_LAYOUT);
        validateNote(note);
        EditableLayoutEntity layout = requireLayout(projectId, layoutId);
        requireEditable(layout);
        Instant now = Instant.now();
        if (mapper.lock(layoutId, expectedLockVersion, actor.id(), now) == 0) {
            throw conflict("LAYOUT_EDIT_CONFLICT", "布局状态或锁版本已经变化");
        }
        EditableLayoutEntity locked = mapper.findLayout(layoutId);
        LayoutVersionEntity version = createVersion(locked, LayoutVersionReason.LOCK,
                null, note, actor.id(), now);
        audit.record(actor.id(), "LAYOUT_LOCK", "EDITABLE_LAYOUT", layoutId.toString(), true);
        return new TransitionResult(mapper.findLayout(layoutId), version);
    }

    @Transactional
    public EditableLayoutEntity unlock(UUID projectId, UUID layoutId, long expectedLockVersion) {
        User actor = access.require(projectId, ProjectPermission.EDIT_LAYOUT);
        EditableLayoutEntity layout = requireLayout(projectId, layoutId);
        try {
            LayoutEditorPolicy.unlock(EditableLayoutState.valueOf(layout.getState()));
        } catch (IllegalStateException exception) {
            throw conflict("LAYOUT_STATE_INVALID", exception.getMessage());
        }
        if (mapper.unlock(layoutId, expectedLockVersion, Instant.now()) == 0) {
            throw conflict("LAYOUT_EDIT_CONFLICT", "布局状态或锁版本已经变化");
        }
        audit.record(actor.id(), "LAYOUT_UNLOCK", "EDITABLE_LAYOUT", layoutId.toString(), true);
        return mapper.findLayout(layoutId);
    }

    @Transactional
    public TransitionResult publish(UUID projectId, UUID layoutId, long expectedLockVersion, String note) {
        User actor = access.require(projectId, ProjectPermission.MANAGE_PROJECT);
        validateNote(note);
        EditableLayoutEntity layout = requireLayout(projectId, layoutId);
        try {
            LayoutEditorPolicy.publish(EditableLayoutState.valueOf(layout.getState()));
        } catch (IllegalStateException exception) {
            throw conflict("LAYOUT_STATE_INVALID", exception.getMessage());
        }
        surfaces.requirePublishable(projectId, layoutId, layout.getCurrentVersionId());
        Instant now = Instant.now();
        if (mapper.publish(layoutId, expectedLockVersion, actor.id(), now) == 0) {
            throw conflict("LAYOUT_EDIT_CONFLICT", "布局状态或锁版本已经变化");
        }
        EditableLayoutEntity published = mapper.findLayout(layoutId);
        LayoutVersionEntity version = createVersion(published, LayoutVersionReason.PUBLISH,
                null, note, actor.id(), now);
        surfaces.promotePublishedVersion(projectId, layoutId, layout.getCurrentVersionId(),
                version.id(), actor.id(), now);
        mapper.setPublishedVersion(layoutId, version.id());
        audit.record(actor.id(), "LAYOUT_PUBLISH", "LAYOUT_VERSION", version.id().toString(), true);
        return new TransitionResult(mapper.findLayout(layoutId), version);
    }

    public List<LayoutVersionEntity> versions(UUID projectId, UUID layoutId, int page, int size) {
        access.require(projectId, ProjectPermission.VIEW); requireLayout(projectId, layoutId);
        int safeSize = Math.min(100, Math.max(1, size));
        return mapper.listVersions(layoutId, safeSize, (long) Math.max(0, page) * safeSize);
    }

    public List<LayoutEditOperationEntity> operations(UUID projectId, UUID layoutId, int page, int size) {
        access.require(projectId, ProjectPermission.VIEW); requireLayout(projectId, layoutId);
        int safeSize = Math.min(200, Math.max(1, size));
        return mapper.listOperations(layoutId, safeSize, (long) Math.max(0, page) * safeSize);
    }

    @Transactional
    public PreviewCreated createPreview(UUID projectId, UUID layoutId, UUID versionId) {
        User actor = access.require(projectId, ProjectPermission.EDIT_LAYOUT);
        EditableLayoutEntity layout = requireLayout(projectId, layoutId);
        LayoutVersionEntity version = versionId == null
                ? requireVersion(layoutId, layout.getCurrentVersionId()) : requireVersion(layoutId, versionId);
        LayoutPreviewEntity existing = mapper.findPreviewByVersion(version.id());
        if (existing != null && !"FAILED".equals(existing.getStatus())) {
            return new PreviewCreated(existing, null, true);
        }
        Instant now = Instant.now();
        LayoutPreviewEntity preview = existing == null ? new LayoutPreviewEntity() : existing;
        if (existing == null) {
            preview.setId(UUID.randomUUID()); preview.setLayoutId(layoutId); preview.setVersionId(version.id());
            preview.setStatus("PENDING"); preview.setAssignmentCount(version.assignmentCount());
            preview.setCreatedBy(actor.id()); preview.setCreatedAt(now); preview.setUpdatedAt(now);
            if (mapper.insertPreview(preview) == 0) {
                return new PreviewCreated(mapper.findPreviewByVersion(version.id()), null, true);
            }
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of("previewId", preview.getId()));
            var job = jobs.enqueue(projectId, actor.id(), JobType.LAYOUT_PREVIEW,
                    "LAYOUT_PREVIEW", preview.getId(), payload, 3, true);
            audit.record(actor.id(), "LAYOUT_PREVIEW_CREATE", "LAYOUT_PREVIEW",
                    preview.getId().toString(), true);
            return new PreviewCreated(mapper.findPreview(preview.getId()), job.getId(), false);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法创建布局预览任务", exception);
        }
    }

    public LayoutPreviewEntity getPreview(UUID projectId, UUID layoutId, UUID previewId) {
        access.require(projectId, ProjectPermission.VIEW); requireLayout(projectId, layoutId);
        LayoutPreviewEntity preview = mapper.findPreview(previewId);
        if (preview == null || !layoutId.equals(preview.getLayoutId())) {
            throw new ApiException("LAYOUT_PREVIEW_NOT_FOUND", "布局预览不存在", HttpStatus.NOT_FOUND);
        }
        return preview;
    }

    public ObjectStorageGateway.PresignedRequest previewUrl(UUID projectId, UUID layoutId, UUID previewId) {
        LayoutPreviewEntity preview = getPreview(projectId, layoutId, previewId);
        if (!"READY".equals(preview.getStatus())) {
            throw conflict("LAYOUT_PREVIEW_NOT_READY", "布局预览尚未生成完成");
        }
        return storage.presignGet(preview.getObjectKey(), "layout-preview.png");
    }

    private void apply(EditableLayoutEntity layout, LayoutEditCommand command, UUID batchId,
            int commandIndex, long revision, long baseLockVersion, UUID actorId, Instant now) {
        LayoutEditorAssignmentEntity primary = requireAssignment(layout.getId(), command.slotIndex());
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        Integer secondarySlot = null;
        switch (command) {
            case LayoutEditCommand.Swap swap -> {
                LayoutEditorAssignmentEntity secondary = requireAssignment(layout.getId(), swap.secondarySlotIndex());
                secondarySlot = swap.secondarySlotIndex();
                before.put("primary", assignmentMap(primary)); before.put("secondary", assignmentMap(secondary));
                mapper.updateDraft(layout.getId(), primary.slotIndex(), secondary.photoId(), secondary.cropX(),
                        secondary.cropY(), secondary.cropWidth(), secondary.cropHeight());
                mapper.updateDraft(layout.getId(), secondary.slotIndex(), primary.photoId(), primary.cropX(),
                        primary.cropY(), primary.cropWidth(), primary.cropHeight());
                after.put("primary", assignmentMap(requireAssignment(layout.getId(), primary.slotIndex())));
                after.put("secondary", assignmentMap(requireAssignment(layout.getId(), secondary.slotIndex())));
            }
            case LayoutEditCommand.Replace replace -> {
                if (primary.photoId().equals(replace.photoId())) {
                    throw bad("PHOTO_ALREADY_ASSIGNED", "目标槽位已经使用该照片");
                }
                LayoutReplacementPhotoEntity photo = mapper.findEligibleReplacement(layout.getProjectId(), replace.photoId());
                if (photo == null) throw bad("PHOTO_NOT_ELIGIBLE", "替换照片不可用、未通过审核或授权无效");
                if (mapper.countPhotoUsage(layout.getId(), photo.photoId()) >= layout.getMaxPhotoReuse()) {
                    throw conflict("PHOTO_REUSE_LIMIT", "替换照片已达到布局允许的最大使用次数");
                }
                before.put("assignment", assignmentMap(primary));
                mapper.updateDraft(layout.getId(), primary.slotIndex(), photo.photoId(), photo.cropX(), photo.cropY(),
                        photo.cropWidth(), photo.cropHeight());
                after.put("assignment", assignmentMap(requireAssignment(layout.getId(), primary.slotIndex())));
            }
            case LayoutEditCommand.Crop crop -> {
                before.put("assignment", assignmentMap(primary));
                CropBox box = crop.crop();
                mapper.updateDraft(layout.getId(), primary.slotIndex(), primary.photoId(), box.x(), box.y(),
                        box.width(), box.height());
                after.put("assignment", assignmentMap(requireAssignment(layout.getId(), primary.slotIndex())));
            }
        }
        mapper.insertOperation(new LayoutEditOperationEntity(UUID.randomUUID(), layout.getId(), revision,
                batchId, commandIndex, command.type().name(), command.slotIndex(), secondarySlot,
                json(before), json(after), baseLockVersion, baseLockVersion + 1, actorId, now));
    }

    private LayoutVersionEntity createVersion(EditableLayoutEntity layout, LayoutVersionReason reason,
            UUID batchId, String note, UUID actorId, Instant now) {
        List<LayoutEditorAssignmentEntity> assignments = mapper.listAllDraft(layout.getId());
        if (assignments.isEmpty()) throw new IllegalStateException("布局没有可保存的分配结果");
        int number = layout.getCurrentVersionNumber() + 1;
        LayoutVersionEntity version = new LayoutVersionEntity(UUID.randomUUID(), layout.getId(), number,
                layout.getCurrentVersionId(), reason.name(), batchId, layout.getCurrentRevision(),
                contentHash(assignments), assignments.size(), normalizeNote(note), actorId, now);
        mapper.insertVersion(version);
        int copied = mapper.snapshotVersion(layout.getId(), version.id());
        if (copied != assignments.size()) throw new IllegalStateException("布局版本快照数量不一致");
        mapper.setCurrentVersion(layout.getId(), version.id(), number, now);
        return version;
    }

    private String contentHash(List<LayoutEditorAssignmentEntity> assignments) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (LayoutEditorAssignmentEntity value : assignments) {
                String line = value.slotIndex() + "|" + value.photoId() + "|"
                        + Double.toHexString(value.cropX()) + "|" + Double.toHexString(value.cropY()) + "|"
                        + Double.toHexString(value.cropWidth()) + "|" + Double.toHexString(value.cropHeight()) + "\n";
                digest.update(line.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法计算布局版本摘要", exception);
        }
    }

    private EditableLayoutEntity requireLayout(UUID projectId, UUID layoutId) {
        EditableLayoutEntity layout = mapper.findLayout(layoutId);
        if (layout == null || !projectId.equals(layout.getProjectId())) {
            throw new ApiException("EDITABLE_LAYOUT_NOT_FOUND", "可编辑布局不存在", HttpStatus.NOT_FOUND);
        }
        return layout;
    }

    private LayoutVersionEntity requireVersion(UUID layoutId, UUID versionId) {
        if (versionId == null) throw new ApiException("LAYOUT_VERSION_NOT_FOUND", "布局版本不存在", HttpStatus.NOT_FOUND);
        LayoutVersionEntity version = mapper.findVersion(versionId);
        if (version == null || !layoutId.equals(version.layoutId())) {
            throw new ApiException("LAYOUT_VERSION_NOT_FOUND", "布局版本不存在", HttpStatus.NOT_FOUND);
        }
        return version;
    }

    private LayoutEditorAssignmentEntity requireAssignment(UUID layoutId, int slotIndex) {
        LayoutEditorAssignmentEntity value = mapper.findDraftForUpdate(layoutId, slotIndex);
        if (value == null) throw bad("LAYOUT_SLOT_NOT_FOUND", "布局槽位不存在：" + slotIndex);
        return value;
    }

    private void requireEditable(EditableLayoutEntity layout) {
        try {
            LayoutEditorPolicy.requireEditable(EditableLayoutState.valueOf(layout.getState()));
        } catch (IllegalStateException exception) {
            throw conflict("LAYOUT_LOCKED", exception.getMessage());
        }
    }

    private Map<String, Object> assignmentMap(LayoutEditorAssignmentEntity value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("slotIndex", value.slotIndex()); map.put("photoId", value.photoId());
        map.put("cropX", value.cropX()); map.put("cropY", value.cropY());
        map.put("cropWidth", value.cropWidth()); map.put("cropHeight", value.cropHeight());
        return map;
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JacksonException exception) { throw new IllegalStateException("无法序列化布局操作", exception); }
    }

    private void validateNote(String note) {
        if (note != null && note.length() > 500) throw bad("LAYOUT_NOTE_INVALID", "版本说明不能超过500个字符");
    }

    private String normalizeNote(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }

    private ApiException bad(String code, String message) {
        return new ApiException(code, message, HttpStatus.BAD_REQUEST);
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(code, message, HttpStatus.CONFLICT);
    }

    public record EditResult(EditableLayoutEntity layout, LayoutVersionEntity version, boolean idempotentReplay) { }
    public record TransitionResult(EditableLayoutEntity layout, LayoutVersionEntity version) { }
    public record PreviewCreated(LayoutPreviewEntity preview, UUID jobId, boolean existing) { }
    public record PhotoPreviewUrl(UUID photoId, java.net.URI url, Map<String, List<String>> headers) { }
}
