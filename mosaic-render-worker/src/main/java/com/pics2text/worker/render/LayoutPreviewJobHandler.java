package com.pics2text.worker.render;

import com.pics2text.job.application.JobExecutionContext;
import com.pics2text.job.application.JobHandler;
import com.pics2text.job.application.PermanentJobException;
import com.pics2text.job.domain.JobType;
import com.pics2text.layout.infrastructure.persistence.LayoutEditorMapper;
import com.pics2text.rendering.application.LayoutColorPreviewGenerator;
import com.pics2text.rendering.application.LayoutPreviewPixel;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class LayoutPreviewJobHandler implements JobHandler {
    private final LayoutEditorMapper mapper;
    private final LayoutPreviewPersistenceService persistence;
    private final RenderObjectStorage storage;
    private final ObjectMapper objectMapper;
    private final LayoutColorPreviewGenerator generator = new LayoutColorPreviewGenerator();

    public LayoutPreviewJobHandler(LayoutEditorMapper mapper,
            LayoutPreviewPersistenceService persistence, RenderObjectStorage storage,
            ObjectMapper objectMapper) {
        this.mapper = mapper; this.persistence = persistence; this.storage = storage;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(JobType type) { return type == JobType.LAYOUT_PREVIEW; }

    @Override
    public String execute(JobExecutionContext context) throws Exception {
        UUID previewId = UUID.fromString(objectMapper.readTree(context.payloadJson()).path("previewId").asText());
        var preview = mapper.findPreviewContext(previewId);
        if (preview == null || !context.projectId().equals(preview.projectId())) {
            throw new PermanentJobException("待生成的布局预览不存在或不属于当前项目");
        }
        if ("READY".equals(preview.status())) {
            var ready = mapper.findPreview(previewId);
            return objectMapper.writeValueAsString(Map.of("previewId", previewId,
                    "width", ready.getWidth(), "height", ready.getHeight(), "idempotentReplay", true));
        }
        if (mapper.markPreviewProcessing(previewId, Instant.now()) == 0) {
            throw new PermanentJobException("布局预览状态不允许生成");
        }
        try {
            context.heartbeat(15);
            var pixels = mapper.listPreviewPixels(preview.versionId()).stream()
                    .map(value -> new LayoutPreviewPixel(value.slotIndex(), value.x(), value.y(),
                            value.width(), value.height(), value.labL(), value.labA(), value.labB()))
                    .toList();
            var result = generator.generate(preview.canvasWidth(), preview.canvasHeight(), pixels);
            context.heartbeat(75);
            if (context.cancellationRequested()) {
                persistence.markFailed(previewId, "LAYOUT_PREVIEW_CANCELLED", "布局预览生成已取消");
                return objectMapper.writeValueAsString(Map.of("cancelledBeforeUpload", true));
            }
            context.enterNonCancellablePhase();
            String key = "projects/" + preview.projectId() + "/editable-layouts/" + preview.layoutId()
                    + "/previews/" + preview.versionId() + ".png";
            storage.put(key, "image/png", result.png());
            persistence.complete(previewId, key, result.width(), result.height(), result.assignmentCount());
            return objectMapper.writeValueAsString(Map.of("previewId", previewId,
                    "width", result.width(), "height", result.height(),
                    "assignmentCount", result.assignmentCount(),
                    "algorithmVersion", LayoutColorPreviewGenerator.ALGORITHM_VERSION));
        } catch (IllegalArgumentException exception) {
            persistence.markFailed(previewId, "LAYOUT_PREVIEW_INPUT_INVALID", exception.getMessage());
            throw new PermanentJobException(exception.getMessage());
        } catch (Exception exception) {
            persistence.markFailed(previewId, "LAYOUT_PREVIEW_ATTEMPT_FAILED", exception.getMessage());
            throw exception;
        }
    }
}
