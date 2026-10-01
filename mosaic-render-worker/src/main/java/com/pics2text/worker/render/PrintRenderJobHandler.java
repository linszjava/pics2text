package com.pics2text.worker.render;

import com.pics2text.job.application.JobExecutionContext;
import com.pics2text.job.application.JobHandler;
import com.pics2text.job.application.PermanentJobException;
import com.pics2text.job.domain.JobType;
import com.pics2text.rendering.infrastructure.persistence.PrintRenderMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class PrintRenderJobHandler implements JobHandler {
    public static final String RENDERER_VERSION = "print-render-worker-v1";
    private final PrintRenderMapper mapper; private final PrintRenderPersistenceService persistence;
    private final RenderObjectStorage storage; private final PrintRenderProperties properties;
    private final LibvipsProcessRenderer renderer; private final ObjectMapper objectMapper;

    public PrintRenderJobHandler(PrintRenderMapper mapper, PrintRenderPersistenceService persistence,
            RenderObjectStorage storage, PrintRenderProperties properties,
            LibvipsProcessRenderer renderer, ObjectMapper objectMapper) {
        this.mapper = mapper; this.persistence = persistence; this.storage = storage;
        this.properties = properties; this.renderer = renderer; this.objectMapper = objectMapper;
    }
    @Override public boolean supports(JobType type) { return type == JobType.PRINT_RENDER; }

    @Override public String execute(JobExecutionContext context) throws Exception {
        UUID renderId = UUID.fromString(objectMapper.readTree(context.payloadJson()).path("renderId").asText());
        var render = mapper.find(renderId);
        if (render == null || !context.projectId().equals(render.getProjectId())) {
            throw new PermanentJobException("待处理的印刷渲染不存在或不属于当前项目");
        }
        if (List.of("READY_FOR_APPROVAL", "APPROVED", "REJECTED").contains(render.getStatus())) {
            return objectMapper.writeValueAsString(Map.of("renderId", renderId, "idempotentReplay", true));
        }
        if (mapper.markProcessing(renderId, Instant.now()) == 0) throw new PermanentJobException("印刷渲染状态不允许执行");
        Path temp = null;
        try {
            Files.createDirectories(properties.tempRoot()); temp = Files.createTempDirectory(properties.tempRoot(), "render-" + renderId + "-");
            ObjectNode manifest = (ObjectNode) objectMapper.readTree(render.getInputManifestJson());
            Path photoDirectory = temp.resolve("photos"); Files.createDirectories(photoDirectory);
            Map<UUID, SourceFile> sources = downloadSources(manifest, photoDirectory, context);
            if (context.cancellationRequested()) { persistence.fail(renderId, "PRINT_RENDER_CANCELLED", "渲染在原图下载后已取消");
                return objectMapper.writeValueAsString(Map.of("cancelledBeforeNativeRender", true)); }
            ObjectNode specification = manifest.deepCopy(); specification.put("nativeMemoryBytes", properties.nativeMemoryBytes());
            specification.put("maxOpenFiles", properties.maxOpenFiles());
            if (properties.srgbProfile() == null || !Files.isRegularFile(properties.srgbProfile())) {
                throw new IOException("配置的sRGB ICC配置文件不存在：" + properties.srgbProfile());
            }
            String colorProfileSha = sha256(properties.srgbProfile());
            specification.put("iccProfilePath", properties.srgbProfile().toString());
            for (JsonNode node : specification.withArray("photos")) {
                ObjectNode photo = (ObjectNode) node; UUID id = UUID.fromString(photo.path("photoId").asText());
                photo.remove("sourceKey"); photo.put("sourcePath", sources.get(id).path().toString());
            }
            Path specPath = temp.resolve("render-spec.json"); Path output = temp.resolve("output.tif");
            Files.writeString(specPath, objectMapper.writeValueAsString(specification), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
            context.heartbeat(40);
            var nativeResult = renderer.render(specPath, output, temp, () -> context.heartbeat(60));
            context.heartbeat(80);
            if (context.cancellationRequested()) {
                persistence.fail(renderId, "PRINT_RENDER_CANCELLED", "渲染在成品上传前已取消");
                return objectMapper.writeValueAsString(Map.of("cancelledBeforeUpload", true));
            }
            verifyBigTiff(output); long outputSize = Files.size(output);
            if (outputSize > properties.maxOutputBytes()) throw new IOException("成品大小超出限制：" + outputSize);
            String outputSha = sha256(output); byte[] locations = locationList(manifest);
            String locationsSha = sha256(locations); String base = "projects/" + render.getProjectId()
                    + "/print-renders/" + renderId + "/";
            String outputKey = base + "artwork-bigtiff.tif"; String locationsKey = base + "photo-locations.json";
            String manifestKey = base + "manifest.json";
            String outputManifest = outputManifest(renderId, render.getInputManifestHash(), outputSha,
                    outputSize, locationsSha, colorProfileSha, nativeResult.libvipsVersion(), sources);
            String outputManifestHash = sha256(outputManifest.getBytes(StandardCharsets.UTF_8));
            context.enterNonCancellablePhase();
            storage.put(outputKey, "image/tiff", output); storage.put(locationsKey, "application/json", locations);
            storage.put(manifestKey, "application/json", outputManifest.getBytes(StandardCharsets.UTF_8));
            persistence.complete(renderId, outputKey, outputSha, outputSize, outputManifest,
                    outputManifestHash, locationsKey, locationsSha);
            return objectMapper.writeValueAsString(Map.of("renderId", renderId, "outputSha256", outputSha,
                    "outputByteSize", outputSize, "manifestHash", outputManifestHash,
                    "rendererVersion", RENDERER_VERSION, "libvipsVersion", nativeResult.libvipsVersion()));
        } catch (IllegalArgumentException exception) {
            persistence.fail(renderId, "PRINT_RENDER_INPUT_INVALID", exception.getMessage());
            throw new PermanentJobException(exception.getMessage());
        } catch (Exception exception) {
            persistence.fail(renderId, "PRINT_RENDER_ATTEMPT_FAILED", exception.getMessage()); throw exception;
        } finally { if (temp != null) deleteTree(temp); }
    }

    private Map<UUID, SourceFile> downloadSources(ObjectNode manifest, Path directory,
            JobExecutionContext context) throws IOException {
        Map<UUID, SourceFile> result = new LinkedHashMap<>(); int total = manifest.withArray("photos").size(); int done = 0;
        for (JsonNode photo : manifest.withArray("photos")) {
            UUID id = UUID.fromString(photo.path("photoId").asText());
            if (!result.containsKey(id)) { Path target = directory.resolve(id + ".jpg");
                storage.download(photo.path("sourceKey").asText(), target, properties.maxSourceBytes());
                String actualSha = sha256(target);
                String expectedSha = photo.path("originalSha256").asText();
                if (!actualSha.equalsIgnoreCase(expectedSha)) {
                    throw new IllegalArgumentException("原图校验和不匹配：" + id);
                }
                result.put(id, new SourceFile(target, actualSha, Files.size(target)));
            } else if (!result.get(id).sha256().equalsIgnoreCase(photo.path("originalSha256").asText())) {
                throw new IllegalArgumentException("同一原图在输入清单中出现不一致的校验和：" + id);
            }
            done++; context.heartbeat(5 + Math.min(30, done * 30 / Math.max(1, total)));
        }
        return result;
    }
    private byte[] locationList(ObjectNode manifest) throws Exception {
        List<Map<String, Object>> locations = new ArrayList<>();
        for (JsonNode photo : manifest.withArray("photos")) {
            Map<String, Object> item = new LinkedHashMap<>();
            for (String field : List.of("slotIndex", "photoId", "originalSha256", "x", "y", "width", "height", "crop")) {
                item.put(field, objectMapper.treeToValue(photo.get(field), Object.class));
            }
            locations.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", "photo-location-list-v1");
        result.put("layoutVersionId", manifest.path("layoutVersionId").asText());
        result.put("surfaceBindingId", manifest.path("surfaceBindingId").asText());
        result.put("coordinateSystem", "OUTPUT_PIXELS_INCLUDING_BLEED");
        result.put("dpi", manifest.path("dpi").asInt());
        result.put("bleedPx", manifest.path("bleedPx").asInt());
        result.put("outputWidthPx", manifest.path("outputWidthPx").asInt());
        result.put("outputHeightPx", manifest.path("outputHeightPx").asInt());
        result.put("locations", locations);
        return objectMapper.writeValueAsBytes(result);
    }
    private String outputManifest(UUID id, String inputHash, String outputSha, long outputSize,
            String locationsSha, String colorProfileSha, String libvipsVersion,
            Map<UUID, SourceFile> sources) throws Exception {
        List<Map<String, Object>> sourceValues = sources.entrySet().stream().map(entry -> Map.<String, Object>of(
                "photoId", entry.getKey(), "sha256", entry.getValue().sha256(), "byteSize", entry.getValue().size()))
                .toList();
        Map<String, Object> value = new LinkedHashMap<>(); value.put("schemaVersion", "print-render-output-v1");
        value.put("renderId", id); value.put("inputManifestHash", inputHash); value.put("rendererVersion", RENDERER_VERSION);
        value.put("libvipsVersion", libvipsVersion); value.put("outputSha256", outputSha);
        value.put("outputByteSize", outputSize); value.put("locationListSha256", locationsSha);
        value.put("colorProfile", "SRGB_IEC61966_2_1");
        value.put("colorProfileSha256", colorProfileSha);
        value.put("sources", sourceValues); return objectMapper.writeValueAsString(value);
    }
    private void verifyBigTiff(Path path) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) < 16) throw new IOException("渲染成品不存在或为空");
        byte[] header;
        try (InputStream input = Files.newInputStream(path)) { header = input.readNBytes(4); }
        boolean little = header.length >= 4 && header[0] == 'I' && header[1] == 'I' && header[2] == 43 && header[3] == 0;
        boolean big = header.length >= 4 && header[0] == 'M' && header[1] == 'M' && header[2] == 0 && header[3] == 43;
        if (!(little || big)) throw new IOException("成品不是BigTIFF文件");
    }
    private String sha256(Path path) throws IOException { try (InputStream raw = Files.newInputStream(path);
            DigestInputStream input = new DigestInputStream(raw, MessageDigest.getInstance("SHA-256"))) {
        input.transferTo(java.io.OutputStream.nullOutputStream()); return HexFormat.of().formatHex(input.getMessageDigest().digest());
    } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); } }
    private String sha256(byte[] value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); } }
    private void deleteTree(Path root) { try (var paths = Files.walk(root)) { paths.sorted(Comparator.reverseOrder()).forEach(path -> {
            try { Files.deleteIfExists(path); } catch (IOException ignored) { }
        }); } catch (IOException ignored) { } }
    private record SourceFile(Path path, String sha256, long size) { }
}
