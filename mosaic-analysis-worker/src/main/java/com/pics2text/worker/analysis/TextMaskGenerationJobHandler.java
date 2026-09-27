package com.pics2text.worker.analysis;

import com.pics2text.job.application.JobExecutionContext;
import com.pics2text.job.application.JobHandler;
import com.pics2text.job.application.PermanentJobException;
import com.pics2text.job.domain.JobType;
import com.pics2text.layout.application.FontCatalog;
import com.pics2text.layout.application.TextMaskGenerator;
import com.pics2text.layout.domain.EdgePolicy;
import com.pics2text.layout.domain.GridStrategy;
import com.pics2text.layout.domain.TextDirection;
import com.pics2text.layout.domain.TextMaskRequest;
import com.pics2text.layout.infrastructure.persistence.TextMaskDesignEntity;
import com.pics2text.layout.infrastructure.persistence.TextMaskMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class TextMaskGenerationJobHandler implements JobHandler {
    private final TextMaskMapper mapper;
    private final TextMaskPersistenceService persistence;
    private final AnalysisObjectStorage storage;
    private final ObjectMapper objectMapper;
    private final TextMaskGenerator generator = new TextMaskGenerator();

    public TextMaskGenerationJobHandler(TextMaskMapper mapper, TextMaskPersistenceService persistence,
            AnalysisObjectStorage storage, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.persistence = persistence;
        this.storage = storage;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(JobType type) {
        return type == JobType.TEXT_MASK_GENERATION;
    }

    @Override
    public String execute(JobExecutionContext context) throws Exception {
        UUID maskId = UUID.fromString(objectMapper.readTree(context.payloadJson()).path("maskId").asText());
        TextMaskDesignEntity design = mapper.selectById(maskId);
        if (design == null || !context.projectId().equals(design.getProjectId())) {
            throw new PermanentJobException("待生成的文字蒙版不存在或不属于当前项目");
        }
        if ("READY".equals(design.getStatus())) {
            return objectMapper.writeValueAsString(Map.of(
                    "maskId", maskId,
                    "slotCount", design.getSlotCount(),
                    "algorithmVersion", design.getAlgorithmVersion(),
                    "idempotentReplay", true));
        }
        if (mapper.markProcessing(maskId, Instant.now()) == 0) {
            throw new PermanentJobException("文字蒙版状态不允许生成");
        }
        try {
            var fontAsset = FontCatalog.assets().stream()
                    .filter(asset -> asset.id().equals(design.getFontId())).findFirst()
                    .orElseThrow(() -> new FontCatalog.FontAssetException("字体资产不存在：" + design.getFontId()));
            if (!fontAsset.sha256().equals(design.getFontSha256())) {
                throw new FontCatalog.FontAssetException("字体文件校验值与蒙版配置不一致");
            }
            context.heartbeat(10);
            var result = generator.generate(toRequest(design));
            context.heartbeat(65);
            if (context.cancellationRequested()) {
                persistence.markFailed(maskId, "TEXT_MASK_CANCELLED", "文字蒙版生成已取消");
                return objectMapper.writeValueAsString(Map.of("cancelledBeforeUpload", true));
            }
            context.enterNonCancellablePhase();
            String base = "projects/" + design.getProjectId() + "/text-masks/" + maskId;
            String maskKey = base + "/mask.png";
            String debugKey = base + "/grid-debug.png";
            storage.put(maskKey, "image/png", result.maskPng());
            storage.put(debugKey, "image/png", result.debugPng());
            context.heartbeat(85);
            persistence.complete(maskId, result, maskKey, debugKey);
            return objectMapper.writeValueAsString(Map.of(
                    "maskId", maskId,
                    "slotCount", result.slots().size(),
                    "warningCount", result.warnings().size(),
                    "algorithmVersion", TextMaskGenerator.ALGORITHM_VERSION));
        } catch (FontCatalog.FontAssetException | IllegalArgumentException exception) {
            persistence.markFailed(maskId, "INVALID_MASK_CONFIGURATION", exception.getMessage());
            throw new PermanentJobException(exception.getMessage());
        } catch (Exception exception) {
            persistence.markFailed(maskId, "TEXT_MASK_ATTEMPT_FAILED", exception.getMessage());
            throw exception;
        }
    }

    private TextMaskRequest toRequest(TextMaskDesignEntity design) {
        return new TextMaskRequest(design.getTextContent(), TextDirection.valueOf(design.getDirection()),
                design.getFontId(), design.getFontSize(), design.getFontWeight(),
                design.getLetterSpacing(), design.getLineSpacing(), design.getCanvasWidth(),
                design.getCanvasHeight(), design.getPadding(), GridStrategy.valueOf(design.getGridStrategy()),
                EdgePolicy.valueOf(design.getEdgePolicy()), design.getCellWidth(), design.getCellHeight(),
                design.getGap(), design.getCoverageThreshold());
    }
}
