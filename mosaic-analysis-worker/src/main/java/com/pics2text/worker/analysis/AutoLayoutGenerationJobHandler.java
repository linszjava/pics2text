package com.pics2text.worker.analysis;

import com.pics2text.job.application.JobExecutionContext;
import com.pics2text.job.application.JobHandler;
import com.pics2text.job.application.PermanentJobException;
import com.pics2text.job.domain.JobType;
import com.pics2text.layout.application.AutoLayoutGenerator;
import com.pics2text.layout.application.LayoutPhotoCandidate;
import com.pics2text.layout.application.LayoutTargetSlot;
import com.pics2text.layout.domain.AutoLayoutRequest;
import com.pics2text.layout.domain.LayoutWeights;
import com.pics2text.layout.infrastructure.persistence.AutoLayoutEntity;
import com.pics2text.layout.infrastructure.persistence.AutoLayoutMapper;
import com.pics2text.layout.infrastructure.persistence.LayoutPhotoCandidateEntity;
import com.pics2text.layout.infrastructure.persistence.TextMaskDesignEntity;
import com.pics2text.layout.infrastructure.persistence.TextMaskMapper;
import com.pics2text.layout.infrastructure.persistence.TextMaskSlotEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class AutoLayoutGenerationJobHandler implements JobHandler {
    private final AutoLayoutMapper mapper;
    private final TextMaskMapper maskMapper;
    private final AutoLayoutPersistenceService persistence;
    private final ObjectMapper objectMapper;
    private final AutoLayoutGenerator generator = new AutoLayoutGenerator();

    public AutoLayoutGenerationJobHandler(AutoLayoutMapper mapper, TextMaskMapper maskMapper,
            AutoLayoutPersistenceService persistence, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.maskMapper = maskMapper;
        this.persistence = persistence;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(JobType type) { return type == JobType.LAYOUT_GENERATION; }

    @Override
    public String execute(JobExecutionContext context) throws Exception {
        UUID layoutId = UUID.fromString(objectMapper.readTree(context.payloadJson()).path("layoutId").asText());
        AutoLayoutEntity layout = mapper.selectById(layoutId);
        if (layout == null || !context.projectId().equals(layout.getProjectId())) {
            throw new PermanentJobException("待生成的自动布局不存在或不属于当前项目");
        }
        if ("READY".equals(layout.getStatus())) {
            return objectMapper.writeValueAsString(Map.of("layoutId", layoutId,
                    "assignedCount", layout.getAssignedCount(), "idempotentReplay", true));
        }
        if (mapper.markProcessing(layoutId, Instant.now()) == 0) {
            throw new PermanentJobException("自动布局状态不允许生成");
        }
        try {
            TextMaskDesignEntity mask = maskMapper.selectById(layout.getMaskId());
            if (mask == null || !"READY".equals(mask.getStatus())
                    || !layout.getMaskContentHash().equals(mask.getContentHash())
                    || !layout.getFontSha256().equals(mask.getFontSha256())) {
                throw new IllegalArgumentException("文字蒙版不存在、未就绪或快照校验不一致");
            }
            List<LayoutPhotoCandidateEntity> candidateEntities = mapper.listEligiblePhotos(layout.getProjectId());
            List<TextMaskSlotEntity> slotEntities = mapper.listMaskSlots(layout.getMaskId());
            if (slotEntities.size() != layout.getSlotCount()) {
                throw new IllegalArgumentException("文字蒙版槽位数量与布局快照不一致");
            }
            context.heartbeat(10);
            List<LayoutPhotoCandidate> candidates = candidateEntities.stream().map(this::candidate).toList();
            List<LayoutTargetSlot> slots = slotEntities.stream().map(this::slot).toList();
            String photoSetHash = photoSetHash(candidateEntities);
            long started = System.nanoTime();
            var result = generator.generate(slots, candidates, request(layout));
            long runtimeMillis = Math.max(0, (System.nanoTime() - started) / 1_000_000);
            context.heartbeat(85);
            if (context.cancellationRequested()) {
                persistence.markFailed(layoutId, "AUTO_LAYOUT_CANCELLED", "自动布局生成已取消");
                return objectMapper.writeValueAsString(Map.of("cancelledBeforePersistence", true));
            }
            context.enterNonCancellablePhase();
            persistence.complete(layoutId, candidateEntities.size(), photoSetHash, runtimeMillis, result);
            return objectMapper.writeValueAsString(Map.of("layoutId", layoutId,
                    "assignedCount", result.assignments().size(),
                    "uniquePhotoCount", result.uniquePhotoCount(),
                    "finalAverageScore", result.finalAverageScore(),
                    "algorithmVersion", AutoLayoutGenerator.ALGORITHM_VERSION));
        } catch (IllegalArgumentException exception) {
            persistence.markFailed(layoutId, "AUTO_LAYOUT_INPUT_INVALID", exception.getMessage());
            throw new PermanentJobException(exception.getMessage());
        } catch (Exception exception) {
            persistence.markFailed(layoutId, "AUTO_LAYOUT_ATTEMPT_FAILED", exception.getMessage());
            throw exception;
        }
    }

    private AutoLayoutRequest request(AutoLayoutEntity value) {
        return new AutoLayoutRequest(value.getRandomSeed(), value.getAllowPhotoReuse(),
                value.getMaxPhotoReuse(), value.getTargetLabL(), value.getTargetLabA(),
                value.getTargetLabB(), value.getCandidateWindow(), value.getOptimizationIterations(),
                new LayoutWeights(value.getWeightQuality(), value.getWeightBrightness(),
                        value.getWeightColor(), value.getWeightCrop(), value.getWeightDuplicate(),
                        value.getWeightAdjacent()));
    }

    private LayoutPhotoCandidate candidate(LayoutPhotoCandidateEntity value) {
        return new LayoutPhotoCandidate(value.getPhotoId(), value.getSharpness(), value.getBrightness(),
                value.getContrast(), value.getLabL(), value.getLabA(), value.getLabB(), value.getPHash(),
                value.getDuplicateGroupId(), value.getCropX(), value.getCropY(),
                value.getCropWidth(), value.getCropHeight());
    }

    private LayoutTargetSlot slot(TextMaskSlotEntity value) {
        return new LayoutTargetSlot(value.slotIndex(), value.rowIndex(), value.columnIndex(), value.x(),
                value.y(), value.width(), value.height(), value.coverage(), value.edge());
    }

    private String photoSetHash(List<LayoutPhotoCandidateEntity> values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (LayoutPhotoCandidateEntity value : values) {
                String line = value.getPhotoId() + "|" + Double.toHexString(value.getSharpness()) + "|"
                        + Double.toHexString(value.getBrightness()) + "|" + Double.toHexString(value.getContrast())
                        + "|" + Double.toHexString(value.getLabL()) + "|" + Double.toHexString(value.getLabA())
                        + "|" + Double.toHexString(value.getLabB()) + "|" + value.getPHash() + "|"
                        + value.getDuplicateGroupId() + "|" + Double.toHexString(value.getCropX()) + "|"
                        + Double.toHexString(value.getCropY()) + "|" + Double.toHexString(value.getCropWidth())
                        + "|" + Double.toHexString(value.getCropHeight()) + "\n";
                digest.update(line.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法计算照片集合摘要", exception);
        }
    }
}
