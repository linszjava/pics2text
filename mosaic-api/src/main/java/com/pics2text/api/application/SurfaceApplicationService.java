package com.pics2text.api.application;

import com.pics2text.api.common.ApiException;
import com.pics2text.api.infrastructure.storage.ObjectStorageGateway;
import com.pics2text.identity.domain.User;
import com.pics2text.layout.infrastructure.persistence.EditableLayoutEntity;
import com.pics2text.layout.infrastructure.persistence.LayoutEditorMapper;
import com.pics2text.project.domain.ProjectPermission;
import com.pics2text.surface.application.SurfaceLayoutValidator;
import com.pics2text.surface.application.SurfaceMapping;
import com.pics2text.surface.application.SurfaceRegion;
import com.pics2text.surface.application.SurfaceSlot;
import com.pics2text.surface.application.SurfaceValidationResult;
import com.pics2text.surface.domain.CylinderGeometry;
import com.pics2text.surface.domain.CylinderSurfaceMapper;
import com.pics2text.surface.domain.SurfaceRect;
import com.pics2text.surface.domain.SurfaceRegionType;
import com.pics2text.surface.domain.SurfaceType;
import com.pics2text.surface.domain.SurfaceUnit;
import com.pics2text.surface.infrastructure.persistence.SurfaceBindingEntity;
import com.pics2text.surface.infrastructure.persistence.SurfaceMapper;
import com.pics2text.surface.infrastructure.persistence.SurfacePreviewAssetEntity;
import com.pics2text.surface.infrastructure.persistence.SurfaceRegionEntity;
import com.pics2text.surface.infrastructure.persistence.SurfaceTemplateEntity;
import java.net.URI;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class SurfaceApplicationService {
    public static final String MANIFEST_SCHEMA = "surface-coordinate-manifest-v1";
    private final SurfaceMapper mapper;
    private final LayoutEditorMapper layoutMapper;
    private final ProjectAccessService access;
    private final ObjectMapper objectMapper;
    private final ObjectStorageGateway storage;
    private final AuditService audit;
    private final SurfaceLayoutValidator validator = new SurfaceLayoutValidator();

    public SurfaceApplicationService(SurfaceMapper mapper, LayoutEditorMapper layoutMapper,
            ProjectAccessService access, ObjectMapper objectMapper, ObjectStorageGateway storage,
            AuditService audit) {
        this.mapper = mapper; this.layoutMapper = layoutMapper; this.access = access;
        this.objectMapper = objectMapper; this.storage = storage; this.audit = audit;
    }

    @Transactional
    public SurfaceTemplateView createTemplate(UUID projectId, CreateTemplate command) {
        User actor = access.require(projectId, ProjectPermission.MANAGE_PROJECT);
        if (command.name() == null || command.name().isBlank() || command.name().length() > 120) {
            throw bad("SURFACE_NAME_INVALID", "曲面模板名称不能为空且不能超过120个字符");
        }
        CylinderGeometry geometry;
        try {
            geometry = new CylinderGeometry(command.diameterMm(), command.axialLengthMm(), command.seamXmm());
        } catch (IllegalArgumentException exception) {
            throw bad("SURFACE_GEOMETRY_INVALID", exception.getMessage());
        }
        validateMargin(command.seamSafetyMm()); validateMargin(command.safeTopMm());
        validateMargin(command.safeRightMm()); validateMargin(command.safeBottomMm());
        validateMargin(command.safeLeftMm());
        if (command.regions() == null || command.regions().size() > 500) {
            throw bad("SURFACE_REGIONS_INVALID", "曲面区域数量不能超过500");
        }
        for (RegionInput region : command.regions()) validateRegion(region, geometry);
        Instant now = Instant.now();
        SurfaceTemplateEntity entity = new SurfaceTemplateEntity();
        entity.setId(UUID.randomUUID()); entity.setProjectId(projectId); entity.setName(command.name().trim());
        entity.setSurfaceType(SurfaceType.CYLINDER.name()); entity.setUnit(SurfaceUnit.MILLIMETER.name());
        entity.setDiameterMm(geometry.diameterMm()); entity.setCircumferenceMm(geometry.circumferenceMm());
        entity.setAxialLengthMm(geometry.axialLengthMm()); entity.setSeamXmm(geometry.seamXmm());
        entity.setSeamSafetyMm(command.seamSafetyMm()); entity.setSafeTopMm(command.safeTopMm());
        entity.setSafeRightMm(command.safeRightMm()); entity.setSafeBottomMm(command.safeBottomMm());
        entity.setSafeLeftMm(command.safeLeftMm()); entity.setTemplateVersion(1); entity.setStatus("ACTIVE");
        entity.setGeometryHash(hash(templateHashSource(entity, command.regions())));
        entity.setCreatedBy(actor.id()); entity.setCreatedAt(now);
        try {
            mapper.insertTemplate(entity);
            for (int index = 0; index < command.regions().size(); index++) {
                RegionInput region = command.regions().get(index);
                mapper.insertRegion(new SurfaceRegionEntity(entity.getId(), index, region.type().name(),
                        region.name().trim(), region.xMm(), region.yMm(), region.widthMm(), region.heightMm(),
                        region.type() == SurfaceRegionType.NO_PRINT, json(region.metadata() == null
                                ? Map.of() : region.metadata())));
            }
        } catch (DuplicateKeyException exception) {
            throw conflict("SURFACE_NAME_EXISTS", "项目内已存在同名曲面模板");
        }
        audit.record(actor.id(), "SURFACE_TEMPLATE_CREATE", "SURFACE_TEMPLATE", entity.getId().toString(), true);
        return view(mapper.findTemplate(entity.getId()));
    }

    public List<SurfaceTemplateView> listTemplates(UUID projectId, int page, int size) {
        access.require(projectId, ProjectPermission.VIEW);
        int safeSize = Math.min(100, Math.max(1, size));
        return mapper.listTemplates(projectId, safeSize, (long) Math.max(0, page) * safeSize)
                .stream().map(this::view).toList();
    }

    public SurfaceTemplateView getTemplate(UUID projectId, UUID templateId) {
        access.require(projectId, ProjectPermission.VIEW);
        return view(requireTemplate(projectId, templateId));
    }

    @Transactional
    public BindingView bind(UUID projectId, UUID layoutId, BindCommand command) {
        User actor = access.require(projectId, ProjectPermission.EDIT_LAYOUT);
        EditableLayoutEntity layout = requireLayout(projectId, layoutId);
        if (!"LOCKED".equals(layout.getState())) {
            throw conflict("LAYOUT_NOT_LOCKED", "布局必须锁定后才能进行曲面映射校验");
        }
        SurfaceTemplateEntity template = requireTemplate(projectId, command.templateId());
        if (!"ACTIVE".equals(template.getStatus())) throw conflict("SURFACE_NOT_ACTIVE", "曲面模板不可用");
        if (!layout.getCurrentVersionId().equals(command.layoutVersionId())) {
            throw conflict("LAYOUT_VERSION_STALE", "只能绑定布局当前锁定版本");
        }
        if (command.originXmm() < 0 || command.originYmm() < 0
                || command.printWidthMm() <= 0 || command.printHeightMm() <= 0) {
            throw bad("SURFACE_MAPPING_INVALID", "印刷区域坐标必须非负且尺寸必须大于0");
        }
        SurfaceMapping mapping;
        try {
            mapping = new SurfaceMapping(layout.getCanvasWidth(), layout.getCanvasHeight(),
                    new SurfaceRect(command.originXmm(), command.originYmm(),
                            command.printWidthMm(), command.printHeightMm()));
        } catch (IllegalArgumentException exception) {
            throw bad("SURFACE_MAPPING_INVALID", exception.getMessage());
        }
        CylinderGeometry geometry = geometry(template);
        var slotEntities = mapper.listVersionSlots(command.layoutVersionId());
        if (slotEntities.isEmpty()) throw conflict("LAYOUT_VERSION_EMPTY", "布局版本没有槽位数据");
        List<SurfaceSlot> slots = slotEntities.stream().map(value -> new SurfaceSlot(value.slotIndex(),
                value.x(), value.y(), value.width(), value.height())).toList();
        List<SurfaceRegion> regions = mapper.listRegions(template.getId()).stream().map(this::domainRegion).toList();
        SurfaceValidationResult result;
        try {
            result = validator.validate(geometry, mapping, template.getSeamSafetyMm(),
                    template.getSafeTopMm(), template.getSafeRightMm(), template.getSafeBottomMm(),
                    template.getSafeLeftMm(), regions, slots);
        } catch (IllegalArgumentException exception) {
            throw bad("SURFACE_MAPPING_INVALID", exception.getMessage());
        }
        String manifest = manifest(layout, template, mapping, regions, slotEntities);
        String violations = json(result.violations());
        Instant now = Instant.now();
        SurfaceBindingEntity previous = mapper.findBinding(layoutId);
        SurfaceBindingEntity binding = new SurfaceBindingEntity(previous == null ? UUID.randomUUID() : previous.id(),
                projectId, layoutId, command.layoutVersionId(), template.getId(), command.originXmm(),
                command.originYmm(), command.printWidthMm(), command.printHeightMm(),
                mapping.scaleXmmPerPixel(), mapping.scaleYmmPerPixel(), result.valid() ? "VALID" : "INVALID",
                result.violations().size(), violations, manifest,
                coordinateHash(layout, template, mapping, slotEntities), actor.id(), now,
                previous == null ? now : previous.createdAt(), now);
        mapper.upsertBinding(binding);
        audit.record(actor.id(), "SURFACE_LAYOUT_VALIDATE", "EDITABLE_LAYOUT", layoutId.toString(), result.valid());
        return bindingView(mapper.findBinding(layoutId));
    }

    public BindingView getBinding(UUID projectId, UUID layoutId) {
        access.require(projectId, ProjectPermission.VIEW); requireLayout(projectId, layoutId);
        SurfaceBindingEntity binding = mapper.findBinding(layoutId);
        if (binding == null) throw new ApiException("SURFACE_BINDING_NOT_FOUND", "布局尚未绑定曲面", HttpStatus.NOT_FOUND);
        return bindingView(binding);
    }

    public PreviewData previewData(UUID projectId, UUID layoutId) {
        access.require(projectId, ProjectPermission.VIEW); requireLayout(projectId, layoutId);
        SurfaceBindingEntity binding = mapper.findBinding(layoutId);
        if (binding == null) throw new ApiException("SURFACE_BINDING_NOT_FOUND", "布局尚未绑定曲面", HttpStatus.NOT_FOUND);
        SurfacePreviewAssetEntity asset = mapper.findPreviewAsset(binding.layoutVersionId());
        URI textureUrl = null;
        if (asset != null && "READY".equals(asset.status()) && asset.objectKey() != null) {
            textureUrl = storage.presignGet(asset.objectKey(), "surface-preview.png").url();
        }
        try {
            return new PreviewData(objectMapper.readTree(binding.coordinateManifestJson()),
                    binding.manifestHash(), binding.validationStatus(), binding.violationCount(),
                    asset == null ? null : asset.previewId(), textureUrl);
        } catch (JacksonException exception) {
            throw new IllegalStateException("曲面坐标清单损坏", exception);
        }
    }

    public void requirePublishable(UUID projectId, UUID layoutId, UUID versionId) {
        SurfaceBindingEntity binding = mapper.findBinding(layoutId);
        if (binding == null) throw conflict("SURFACE_BINDING_REQUIRED", "发布前必须绑定曲面并完成合法性校验");
        if (!projectId.equals(binding.projectId()) || !versionId.equals(binding.layoutVersionId())) {
            throw conflict("SURFACE_BINDING_STALE", "曲面校验对应的不是当前锁定版本，请重新校验");
        }
        if (!"VALID".equals(binding.validationStatus())) {
            throw conflict("SURFACE_VALIDATION_FAILED", "布局与曲面存在接缝、安全边距或禁印区域冲突");
        }
    }

    public void promotePublishedVersion(UUID projectId, UUID layoutId, UUID sourceVersionId,
            UUID publishedVersionId, UUID actorId, Instant now) {
        SurfaceBindingEntity binding = mapper.findBinding(layoutId);
        if (binding == null || !projectId.equals(binding.projectId())
                || !sourceVersionId.equals(binding.layoutVersionId())
                || !"VALID".equals(binding.validationStatus())) {
            throw conflict("SURFACE_BINDING_STALE", "发布版本的曲面坐标清单已失效");
        }
        EditableLayoutEntity layout = requireLayout(projectId, layoutId);
        SurfaceTemplateEntity template = requireTemplate(projectId, binding.templateId());
        var slots = mapper.listVersionSlots(publishedVersionId);
        if (slots.isEmpty()) throw new IllegalStateException("发布版本没有坐标数据");
        SurfaceMapping mapping = new SurfaceMapping(layout.getCanvasWidth(), layout.getCanvasHeight(),
                new SurfaceRect(binding.originXmm(), binding.originYmm(), binding.printWidthMm(),
                        binding.printHeightMm()));
        try {
            JsonNode parsed = objectMapper.readTree(binding.coordinateManifestJson());
            if (!(parsed instanceof ObjectNode root)) throw new IllegalStateException("曲面坐标清单格式无效");
            root.put("layoutVersionId", publishedVersionId.toString());
            String manifest = objectMapper.writeValueAsString(root);
            SurfaceBindingEntity promoted = new SurfaceBindingEntity(binding.id(), projectId, layoutId,
                    publishedVersionId, binding.templateId(), binding.originXmm(), binding.originYmm(),
                    binding.printWidthMm(), binding.printHeightMm(), binding.scaleXmmPerPx(),
                    binding.scaleYmmPerPx(), binding.validationStatus(), binding.violationCount(),
                    binding.violationsJson(), manifest, coordinateHash(layout, template, mapping, slots),
                    actorId, now, binding.createdAt(), now);
            mapper.upsertBinding(promoted);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法固化发布版本的曲面坐标清单", exception);
        }
    }

    private String manifest(EditableLayoutEntity layout, SurfaceTemplateEntity template,
            SurfaceMapping mapping, List<SurfaceRegion> regions,
            List<com.pics2text.surface.infrastructure.persistence.SurfaceVersionSlotEntity> slots) {
        CylinderSurfaceMapper cylinder = new CylinderSurfaceMapper(geometry(template));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", MANIFEST_SCHEMA); root.put("unit", SurfaceUnit.MILLIMETER.name());
        root.put("layoutId", layout.getId()); root.put("layoutVersionId", layout.getCurrentVersionId());
        root.put("templateId", template.getId()); root.put("templateVersion", template.getTemplateVersion());
        root.put("geometryHash", template.getGeometryHash());
        root.put("surface", Map.of("type", template.getSurfaceType(), "diameterMm", template.getDiameterMm(),
                "radiusMm", template.getDiameterMm() / 2, "circumferenceMm", template.getCircumferenceMm(),
                "axialLengthMm", template.getAxialLengthMm(), "seamXmm", template.getSeamXmm(),
                "seamSafetyMm", template.getSeamSafetyMm()));
        root.put("canvas", Map.of("widthPx", layout.getCanvasWidth(), "heightPx", layout.getCanvasHeight()));
        root.put("mapping", Map.of("originXmm", mapping.printAreaMm().x(), "originYmm", mapping.printAreaMm().y(),
                "printWidthMm", mapping.printAreaMm().width(), "printHeightMm", mapping.printAreaMm().height(),
                "scaleXmmPerPx", mapping.scaleXmmPerPixel(), "scaleYmmPerPx", mapping.scaleYmmPerPixel()));
        root.put("regions", regions.stream().map(region -> Map.of("regionIndex", region.regionIndex(),
                "type", region.type().name(), "name", region.name(), "blocking", region.blocking(),
                "xMm", region.bounds().x(), "yMm", region.bounds().y(), "widthMm", region.bounds().width(),
                "heightMm", region.bounds().height())).toList());
        root.put("slots", slots.stream().map(slot -> {
            SurfaceRect physical = mapping.map(slot.x(), slot.y(), slot.width(), slot.height());
            return Map.of("slotIndex", slot.slotIndex(), "photoId", slot.photoId(),
                    "physical", physical, "uvTopLeft", cylinder.map(physical.x(), physical.y()),
                    "uvBottomRight", cylinder.map(physical.maxX(), physical.maxY()),
                    "crop", Map.of("x", slot.cropX(), "y", slot.cropY(), "width", slot.cropWidth(),
                            "height", slot.cropHeight()));
        }).toList());
        return json(root);
    }

    private SurfaceTemplateView view(SurfaceTemplateEntity entity) {
        return new SurfaceTemplateView(entity, mapper.listRegions(entity.getId()));
    }
    private SurfaceTemplateEntity requireTemplate(UUID projectId, UUID templateId) {
        SurfaceTemplateEntity entity = mapper.findTemplate(templateId);
        if (entity == null || !projectId.equals(entity.getProjectId())) {
            throw new ApiException("SURFACE_TEMPLATE_NOT_FOUND", "曲面模板不存在", HttpStatus.NOT_FOUND);
        }
        return entity;
    }
    private EditableLayoutEntity requireLayout(UUID projectId, UUID layoutId) {
        EditableLayoutEntity layout = layoutMapper.findLayout(layoutId);
        if (layout == null || !projectId.equals(layout.getProjectId())) {
            throw new ApiException("EDITABLE_LAYOUT_NOT_FOUND", "可编辑布局不存在", HttpStatus.NOT_FOUND);
        }
        return layout;
    }
    private CylinderGeometry geometry(SurfaceTemplateEntity value) {
        return new CylinderGeometry(value.getDiameterMm(), value.getAxialLengthMm(), value.getSeamXmm());
    }
    private SurfaceRegion domainRegion(SurfaceRegionEntity value) {
        return new SurfaceRegion(value.regionIndex(), SurfaceRegionType.valueOf(value.regionType()), value.name(),
                new SurfaceRect(value.xMm(), value.yMm(), value.widthMm(), value.heightMm()), value.blocking());
    }
    private BindingView bindingView(SurfaceBindingEntity value) {
        try {
            return new BindingView(value, objectMapper.readTree(value.violationsJson()));
        } catch (JacksonException exception) { throw new IllegalStateException("曲面校验结果损坏", exception); }
    }
    private void validateRegion(RegionInput value, CylinderGeometry geometry) {
        if (value == null || value.type() == null || value.name() == null || value.name().isBlank()
                || value.name().length() > 120) throw bad("SURFACE_REGION_INVALID", "曲面区域名称或类型无效");
        try {
            SurfaceRect rect = new SurfaceRect(value.xMm(), value.yMm(), value.widthMm(), value.heightMm());
            if (rect.maxX() > geometry.circumferenceMm() + 1e-6
                    || rect.maxY() > geometry.axialLengthMm() + 1e-6) {
                throw new IllegalArgumentException("曲面区域超出物理范围");
            }
        } catch (IllegalArgumentException exception) { throw bad("SURFACE_REGION_INVALID", exception.getMessage()); }
    }
    private void validateMargin(double value) {
        if (!Double.isFinite(value) || value < 0) throw bad("SURFACE_MARGIN_INVALID", "安全边距不能为负数");
    }
    private String templateHashSource(SurfaceTemplateEntity e, List<RegionInput> regions) {
        StringBuilder value = new StringBuilder().append(e.getSurfaceType()).append('|').append(e.getUnit())
                .append('|').append(Double.toHexString(e.getDiameterMm()))
                .append('|').append(Double.toHexString(e.getAxialLengthMm()))
                .append('|').append(Double.toHexString(e.getSeamXmm()))
                .append('|').append(Double.toHexString(e.getSeamSafetyMm()))
                .append('|').append(Double.toHexString(e.getSafeTopMm()))
                .append('|').append(Double.toHexString(e.getSafeRightMm()))
                .append('|').append(Double.toHexString(e.getSafeBottomMm()))
                .append('|').append(Double.toHexString(e.getSafeLeftMm()));
        for (int index = 0; index < regions.size(); index++) {
            RegionInput region = regions.get(index);
            value.append('\n').append(index).append('|').append(region.type()).append('|').append(region.name())
                    .append('|').append(Double.toHexString(region.xMm()))
                    .append('|').append(Double.toHexString(region.yMm()))
                    .append('|').append(Double.toHexString(region.widthMm()))
                    .append('|').append(Double.toHexString(region.heightMm()));
        }
        return value.toString();
    }
    private String coordinateHash(EditableLayoutEntity layout, SurfaceTemplateEntity template,
            SurfaceMapping mapping,
            List<com.pics2text.surface.infrastructure.persistence.SurfaceVersionSlotEntity> slots) {
        StringBuilder value = new StringBuilder(MANIFEST_SCHEMA).append('|').append(layout.getCurrentVersionId())
                .append('|').append(template.getGeometryHash()).append('|')
                .append(Double.toHexString(mapping.printAreaMm().x())).append('|')
                .append(Double.toHexString(mapping.printAreaMm().y())).append('|')
                .append(Double.toHexString(mapping.printAreaMm().width())).append('|')
                .append(Double.toHexString(mapping.printAreaMm().height()));
        for (var slot : slots) {
            value.append('\n').append(slot.slotIndex()).append('|').append(slot.photoId())
                    .append('|').append(slot.x()).append('|').append(slot.y()).append('|')
                    .append(slot.width()).append('|').append(slot.height())
                    .append('|').append(Double.toHexString(slot.cropX()))
                    .append('|').append(Double.toHexString(slot.cropY()))
                    .append('|').append(Double.toHexString(slot.cropWidth()))
                    .append('|').append(Double.toHexString(slot.cropHeight()));
        }
        return hash(value.toString());
    }
    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JacksonException exception) { throw new IllegalStateException("无法序列化曲面数据", exception); }
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private ApiException bad(String code, String message) { return new ApiException(code, message, HttpStatus.BAD_REQUEST); }
    private ApiException conflict(String code, String message) { return new ApiException(code, message, HttpStatus.CONFLICT); }

    public record RegionInput(SurfaceRegionType type, String name, double xMm, double yMm,
            double widthMm, double heightMm, Map<String, Object> metadata) { }
    public record CreateTemplate(String name, double diameterMm, double axialLengthMm, double seamXmm,
            double seamSafetyMm, double safeTopMm, double safeRightMm, double safeBottomMm,
            double safeLeftMm, List<RegionInput> regions) { }
    public record BindCommand(UUID templateId, UUID layoutVersionId, double originXmm,
            double originYmm, double printWidthMm, double printHeightMm) { }
    public record SurfaceTemplateView(SurfaceTemplateEntity template, List<SurfaceRegionEntity> regions) { }
    public record BindingView(SurfaceBindingEntity binding, JsonNode violations) { }
    public record PreviewData(JsonNode manifest, String manifestHash, String validationStatus,
            int violationCount, UUID layoutPreviewId, URI textureUrl) { }
}
