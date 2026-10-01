package com.pics2text.api.application;

import com.pics2text.api.common.ApiException;
import com.pics2text.api.infrastructure.storage.ObjectStorageGateway;
import com.pics2text.job.application.JobCoordinator;
import com.pics2text.job.domain.JobType;
import com.pics2text.layout.infrastructure.persistence.EditableLayoutEntity;
import com.pics2text.layout.infrastructure.persistence.LayoutEditorMapper;
import com.pics2text.project.domain.ProjectPermission;
import com.pics2text.rendering.application.RenderPlacement;
import com.pics2text.rendering.application.RenderTilePlanner;
import com.pics2text.rendering.domain.PixelRect;
import com.pics2text.rendering.domain.PrintRenderFormat;
import com.pics2text.rendering.domain.RenderDimensions;
import com.pics2text.rendering.infrastructure.persistence.PrintRenderEntity;
import com.pics2text.rendering.infrastructure.persistence.PrintRenderMapper;
import com.pics2text.rendering.infrastructure.persistence.PrintRenderPhotoRow;
import com.pics2text.surface.infrastructure.persistence.SurfaceBindingEntity;
import com.pics2text.surface.infrastructure.persistence.SurfaceMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
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

@Service
public class PrintRenderApplicationService {
    public static final String INPUT_SCHEMA = "print-render-input-v1";
    private final PrintRenderMapper mapper; private final LayoutEditorMapper layouts;
    private final SurfaceMapper surfaces; private final ProjectAccessService access;
    private final JobCoordinator jobs; private final AuditService audit;
    private final ObjectStorageGateway storage; private final ObjectMapper objectMapper;

    public PrintRenderApplicationService(PrintRenderMapper mapper, LayoutEditorMapper layouts,
            SurfaceMapper surfaces, ProjectAccessService access, JobCoordinator jobs,
            AuditService audit, ObjectStorageGateway storage, ObjectMapper objectMapper) {
        this.mapper = mapper; this.layouts = layouts; this.surfaces = surfaces; this.access = access;
        this.jobs = jobs; this.audit = audit; this.storage = storage; this.objectMapper = objectMapper;
    }

    @Transactional
    public Created create(UUID projectId, CreateCommand command) {
        var actor = access.require(projectId, ProjectPermission.MANAGE_PROJECT);
        if (command.compression() == null
                || !List.of("ZSTD", "LZW", "DEFLATE").contains(command.compression())) {
            throw bad("PRINT_RENDER_COMPRESSION_INVALID", "压缩方式必须为ZSTD、LZW或DEFLATE");
        }
        EditableLayoutEntity layout = requireLayout(projectId, command.layoutId());
        if (!"PUBLISHED".equals(layout.getState()) || layout.getPublishedVersionId() == null) {
            throw conflict("LAYOUT_NOT_PUBLISHED", "只能为已发布布局生成印刷成品");
        }
        SurfaceBindingEntity binding = surfaces.findBinding(layout.getId());
        if (binding == null || !"VALID".equals(binding.validationStatus())
                || !layout.getPublishedVersionId().equals(binding.layoutVersionId())) {
            throw conflict("SURFACE_BINDING_STALE", "发布版本缺少有效曲面坐标清单");
        }
        RenderDimensions dimensions;
        try { dimensions = RenderDimensions.of(binding.printWidthMm(), binding.printHeightMm(),
                command.bleedMm(), command.dpi()); }
        catch (IllegalArgumentException exception) { throw bad("PRINT_RENDER_DIMENSIONS_INVALID", exception.getMessage()); }
        if (command.tileSize() < 128 || command.tileSize() > 8192) {
            throw bad("PRINT_RENDER_TILE_INVALID", "瓦片尺寸必须位于128到8192像素之间");
        }
        List<PrintRenderPhotoRow> photos = mapper.listPhotoInputs(layout.getPublishedVersionId());
        if (photos.isEmpty()) throw conflict("PRINT_RENDER_INPUT_EMPTY", "发布版本没有照片槽位");
        for (PrintRenderPhotoRow photo : photos) validatePhoto(photo);
        String manifest = inputManifest(layout, binding, dimensions, command, photos);
        String inputHash = sha256(manifest.getBytes(StandardCharsets.UTF_8));
        PrintRenderEntity existing = mapper.findByInputHash(projectId, inputHash);
        if (existing != null) return new Created(existing, existing.getJobId(), true);
        Instant now = Instant.now(); PrintRenderEntity render = new PrintRenderEntity();
        render.setId(UUID.randomUUID()); render.setProjectId(projectId); render.setLayoutId(layout.getId());
        render.setLayoutVersionId(layout.getPublishedVersionId()); render.setSurfaceBindingId(binding.id());
        render.setStatus("PENDING"); render.setFormat(PrintRenderFormat.BIGTIFF.name()); render.setDpi(command.dpi());
        render.setBleedMm(command.bleedMm()); render.setTileSize(command.tileSize());
        render.setCompression(command.compression()); render.setColorProfile("SRGB_IEC61966_2_1");
        render.setCropMarks(command.cropMarks()); render.setRegistrationMarks(command.registrationMarks());
        render.setContentWidthPx(dimensions.contentWidthPx()); render.setContentHeightPx(dimensions.contentHeightPx());
        render.setOutputWidthPx(dimensions.outputWidthPx()); render.setOutputHeightPx(dimensions.outputHeightPx());
        render.setInputManifestJson(manifest); render.setInputManifestHash(inputHash);
        render.setCreatedBy(actor.id()); render.setCreatedAt(now); render.setUpdatedAt(now);
        try { mapper.insert(render); }
        catch (DuplicateKeyException exception) {
            existing = mapper.findByInputHash(projectId, inputHash);
            if (existing != null) return new Created(existing, existing.getJobId(), true);
            throw exception;
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of("renderId", render.getId()));
            var job = jobs.enqueue(projectId, actor.id(), JobType.PRINT_RENDER,
                    "PRINT_RENDER", render.getId(), payload, 3, true);
            mapper.attachJob(render.getId(), job.getId(), now);
            audit.record(actor.id(), "PRINT_RENDER_CREATE", "PRINT_RENDER", render.getId().toString(), true);
            return new Created(mapper.find(render.getId()), job.getId(), false);
        } catch (JacksonException exception) { throw new IllegalStateException("无法创建印刷渲染作业", exception); }
    }

    public PrintRenderEntity get(UUID projectId, UUID renderId) {
        access.require(projectId, ProjectPermission.VIEW);
        PrintRenderEntity value = mapper.find(renderId);
        if (value == null || !projectId.equals(value.getProjectId())) {
            throw new ApiException("PRINT_RENDER_NOT_FOUND", "印刷渲染不存在", HttpStatus.NOT_FOUND);
        }
        return value;
    }

    public List<PrintRenderEntity> list(UUID projectId, int page, int size) {
        access.require(projectId, ProjectPermission.VIEW); int safeSize = Math.min(100, Math.max(1, size));
        return mapper.list(projectId, safeSize, (long) Math.max(0, page) * safeSize);
    }

    @Transactional
    public PrintRenderEntity decide(UUID projectId, UUID renderId, String decision, String note) {
        var actor = access.require(projectId, ProjectPermission.MANAGE_PROJECT); PrintRenderEntity render = get(projectId, renderId);
        if (!("APPROVED".equals(decision) || "REJECTED".equals(decision))) {
            throw bad("PRINT_RENDER_DECISION_INVALID", "审批结果无效");
        }
        String normalized = note == null ? "" : note.trim();
        if (normalized.isBlank() || normalized.length() > 500) throw bad("PRINT_RENDER_NOTE_INVALID", "审批说明必须为1到500个字符");
        Instant now = Instant.now();
        if (mapper.decide(renderId, decision, now) == 0) throw conflict("PRINT_RENDER_STATE_CONFLICT", "仅待审批成品可执行审批");
        mapper.insertApproval(UUID.randomUUID(), renderId, decision, normalized, actor.id(), now);
        audit.record(actor.id(), "PRINT_RENDER_" + decision, "PRINT_RENDER", renderId.toString(), true);
        return mapper.find(renderId);
    }

    public ObjectStorageGateway.PresignedRequest download(UUID projectId, UUID renderId) {
        var actor = access.require(projectId, ProjectPermission.VIEW);
        PrintRenderEntity render = mapper.find(renderId);
        if (render == null || !projectId.equals(render.getProjectId())) {
            throw new ApiException("PRINT_RENDER_NOT_FOUND", "印刷渲染不存在", HttpStatus.NOT_FOUND);
        }
        if (!"APPROVED".equals(render.getStatus()) || render.getOutputObjectKey() == null) {
            throw conflict("PRINT_RENDER_NOT_APPROVED", "印刷成品审批通过后才可下载");
        }
        ObjectStorageGateway.PresignedRequest request = storage.presignGet(render.getOutputObjectKey(),
                "pics2text-" + renderId + ".tif");
        audit.record(actor.id(), "PRINT_RENDER_DOWNLOAD_URL", "PRINT_RENDER", renderId.toString(), true);
        return request;
    }

    private String inputManifest(EditableLayoutEntity layout, SurfaceBindingEntity binding,
            RenderDimensions dimensions, CreateCommand command, List<PrintRenderPhotoRow> photos) {
        try {
            JsonNode surface = objectMapper.readTree(binding.coordinateManifestJson());
            Map<Integer, JsonNode> surfaceSlots = new LinkedHashMap<>();
            surface.path("slots").forEach(slot -> surfaceSlots.put(slot.path("slotIndex").asInt(), slot));
            List<Map<String, Object>> inputs = new ArrayList<>(); List<RenderPlacement> placements = new ArrayList<>();
            double pxPerMm = dimensions.pixelsPerMm();
            for (PrintRenderPhotoRow photo : photos) {
                JsonNode surfaceSlot = surfaceSlots.get(photo.slotIndex());
                if (surfaceSlot == null || !surfaceSlot.path("physical").isObject()) {
                    throw conflict("SURFACE_MANIFEST_SLOT_MISSING",
                            "曲面坐标清单缺少槽位" + photo.slotIndex() + "的物理坐标");
                }
                JsonNode physical = surfaceSlot.path("physical");
                int x = dimensions.bleedPx() + (int) Math.round((physical.path("x").asDouble() - binding.originXmm()) * pxPerMm);
                int y = dimensions.bleedPx() + (int) Math.round((physical.path("y").asDouble() - binding.originYmm()) * pxPerMm);
                int width = Math.max(1, (int) Math.round(physical.path("width").asDouble() * pxPerMm));
                int height = Math.max(1, (int) Math.round(physical.path("height").asDouble() * pxPerMm));
                PixelRect bounds = new PixelRect(x, y, width, height);
                placements.add(new RenderPlacement(photo.slotIndex(), photo.photoId(), bounds,
                        photo.cropX(), photo.cropY(), photo.cropWidth(), photo.cropHeight()));
                Map<String, Object> item = new LinkedHashMap<>(); item.put("slotIndex", photo.slotIndex());
                item.put("photoId", photo.photoId()); item.put("sourceKey", photo.sourceKey());
                item.put("originalSha256", photo.originalSha256()); item.put("sourceWidth", photo.sourceWidth());
                item.put("sourceHeight", photo.sourceHeight()); item.put("x", x); item.put("y", y);
                item.put("width", width); item.put("height", height);
                item.put("crop", Map.of("x", photo.cropX(), "y", photo.cropY(),
                        "width", photo.cropWidth(), "height", photo.cropHeight())); inputs.add(item);
            }
            var tiles = new RenderTilePlanner().plan(dimensions.outputWidthPx(), dimensions.outputHeightPx(),
                    command.tileSize(), placements);
            Map<String, Object> root = new LinkedHashMap<>(); root.put("schemaVersion", INPUT_SCHEMA);
            root.put("layoutId", layout.getId()); root.put("layoutVersionId", layout.getPublishedVersionId());
            var layoutVersion = layouts.findVersion(layout.getPublishedVersionId());
            if (layoutVersion == null) {
                throw conflict("LAYOUT_VERSION_MISSING", "发布版本不存在，无法固化渲染输入");
            }
            root.put("layoutContentHash", layoutVersion.contentHash());
            root.put("surfaceBindingId", binding.id()); root.put("surfaceManifestHash", binding.manifestHash());
            root.put("format", PrintRenderFormat.BIGTIFF.name()); root.put("dpi", command.dpi());
            root.put("bleedMm", command.bleedMm()); root.put("bleedPx", dimensions.bleedPx());
            root.put("contentWidthPx", dimensions.contentWidthPx()); root.put("contentHeightPx", dimensions.contentHeightPx());
            root.put("outputWidthPx", dimensions.outputWidthPx()); root.put("outputHeightPx", dimensions.outputHeightPx());
            root.put("tileSize", command.tileSize()); root.put("compression", command.compression().toLowerCase());
            root.put("colorProfile", "SRGB_IEC61966_2_1"); root.put("cropMarks", command.cropMarks());
            root.put("registrationMarks", command.registrationMarks()); root.put("photos", inputs);
            root.put("tiles", tiles); return objectMapper.writeValueAsString(root);
        } catch (JacksonException exception) { throw new IllegalStateException("无法固化渲染输入清单", exception); }
        catch (IllegalArgumentException exception) {
            throw bad("PRINT_RENDER_MANIFEST_INVALID", exception.getMessage());
        }
    }

    private void validatePhoto(PrintRenderPhotoRow photo) {
        if (!"APPROVED".equals(photo.reviewStatus()) || !"GRANTED".equals(photo.authorizationStatus())
                || photo.sourceKey() == null || photo.sourceKey().isBlank()
                || photo.originalSha256() == null || !photo.originalSha256().matches("[0-9a-f]{64}")
                || photo.sourceWidth() == null || photo.sourceWidth() <= 0
                || photo.sourceHeight() == null || photo.sourceHeight() <= 0) {
            throw conflict("PRINT_RENDER_PHOTO_INELIGIBLE", "槽位" + photo.slotIndex() + "的照片未通过审核、授权或源文件检查");
        }
    }
    private EditableLayoutEntity requireLayout(UUID projectId, UUID layoutId) {
        EditableLayoutEntity value = layouts.findLayout(layoutId);
        if (value == null || !projectId.equals(value.getProjectId())) throw new ApiException("EDITABLE_LAYOUT_NOT_FOUND", "可编辑布局不存在", HttpStatus.NOT_FOUND);
        return value;
    }
    private String sha256(byte[] value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); } }
    private ApiException bad(String code, String message) { return new ApiException(code, message, HttpStatus.BAD_REQUEST); }
    private ApiException conflict(String code, String message) { return new ApiException(code, message, HttpStatus.CONFLICT); }

    public record CreateCommand(UUID layoutId, int dpi, double bleedMm, int tileSize,
            String compression, boolean cropMarks, boolean registrationMarks) { }
    public record Created(PrintRenderEntity render, UUID jobId, boolean existing) { }
}
