package com.pics2text.layout.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.UUID;

@TableName("auto_layout")
public class AutoLayoutEntity {
    @TableId private UUID id;
    private UUID projectId;
    private UUID maskId;
    private String name;
    private String status;
    private String algorithmVersion;
    private String scoringVersion;
    private String configurationHash;
    private String maskContentHash;
    private String fontSha256;
    private String photoSetHash;
    private Long randomSeed;
    private Boolean allowPhotoReuse;
    private Integer maxPhotoReuse;
    private Double targetLabL;
    private Double targetLabA;
    private Double targetLabB;
    private Integer candidateWindow;
    private Integer optimizationIterations;
    private Double weightQuality;
    private Double weightBrightness;
    private Double weightColor;
    private Double weightCrop;
    private Double weightDuplicate;
    private Double weightAdjacent;
    private Integer candidateCount;
    private Integer slotCount;
    private Integer assignedCount;
    private Double initialAverageScore;
    private Double finalAverageScore;
    private Double averageBrightnessScore;
    private Double averageColorScore;
    private Double averageCropScore;
    private Double averageAdjacentScore;
    private Integer uniquePhotoCount;
    private Integer duplicateGroupReuseCount;
    private Long candidateEvaluations;
    private Long estimatedMemoryBytes;
    private Long runtimeMillis;
    private String errorCode;
    private String errorMessage;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;

    public UUID getId() { return id; } public void setId(UUID value) { id = value; }
    public UUID getProjectId() { return projectId; } public void setProjectId(UUID value) { projectId = value; }
    public UUID getMaskId() { return maskId; } public void setMaskId(UUID value) { maskId = value; }
    public String getName() { return name; } public void setName(String value) { name = value; }
    public String getStatus() { return status; } public void setStatus(String value) { status = value; }
    public String getAlgorithmVersion() { return algorithmVersion; } public void setAlgorithmVersion(String value) { algorithmVersion = value; }
    public String getScoringVersion() { return scoringVersion; } public void setScoringVersion(String value) { scoringVersion = value; }
    public String getConfigurationHash() { return configurationHash; } public void setConfigurationHash(String value) { configurationHash = value; }
    public String getMaskContentHash() { return maskContentHash; } public void setMaskContentHash(String value) { maskContentHash = value; }
    public String getFontSha256() { return fontSha256; } public void setFontSha256(String value) { fontSha256 = value; }
    public String getPhotoSetHash() { return photoSetHash; } public void setPhotoSetHash(String value) { photoSetHash = value; }
    public Long getRandomSeed() { return randomSeed; } public void setRandomSeed(Long value) { randomSeed = value; }
    public Boolean getAllowPhotoReuse() { return allowPhotoReuse; } public void setAllowPhotoReuse(Boolean value) { allowPhotoReuse = value; }
    public Integer getMaxPhotoReuse() { return maxPhotoReuse; } public void setMaxPhotoReuse(Integer value) { maxPhotoReuse = value; }
    public Double getTargetLabL() { return targetLabL; } public void setTargetLabL(Double value) { targetLabL = value; }
    public Double getTargetLabA() { return targetLabA; } public void setTargetLabA(Double value) { targetLabA = value; }
    public Double getTargetLabB() { return targetLabB; } public void setTargetLabB(Double value) { targetLabB = value; }
    public Integer getCandidateWindow() { return candidateWindow; } public void setCandidateWindow(Integer value) { candidateWindow = value; }
    public Integer getOptimizationIterations() { return optimizationIterations; } public void setOptimizationIterations(Integer value) { optimizationIterations = value; }
    public Double getWeightQuality() { return weightQuality; } public void setWeightQuality(Double value) { weightQuality = value; }
    public Double getWeightBrightness() { return weightBrightness; } public void setWeightBrightness(Double value) { weightBrightness = value; }
    public Double getWeightColor() { return weightColor; } public void setWeightColor(Double value) { weightColor = value; }
    public Double getWeightCrop() { return weightCrop; } public void setWeightCrop(Double value) { weightCrop = value; }
    public Double getWeightDuplicate() { return weightDuplicate; } public void setWeightDuplicate(Double value) { weightDuplicate = value; }
    public Double getWeightAdjacent() { return weightAdjacent; } public void setWeightAdjacent(Double value) { weightAdjacent = value; }
    public Integer getCandidateCount() { return candidateCount; } public void setCandidateCount(Integer value) { candidateCount = value; }
    public Integer getSlotCount() { return slotCount; } public void setSlotCount(Integer value) { slotCount = value; }
    public Integer getAssignedCount() { return assignedCount; } public void setAssignedCount(Integer value) { assignedCount = value; }
    public Double getInitialAverageScore() { return initialAverageScore; } public void setInitialAverageScore(Double value) { initialAverageScore = value; }
    public Double getFinalAverageScore() { return finalAverageScore; } public void setFinalAverageScore(Double value) { finalAverageScore = value; }
    public Double getAverageBrightnessScore() { return averageBrightnessScore; } public void setAverageBrightnessScore(Double value) { averageBrightnessScore = value; }
    public Double getAverageColorScore() { return averageColorScore; } public void setAverageColorScore(Double value) { averageColorScore = value; }
    public Double getAverageCropScore() { return averageCropScore; } public void setAverageCropScore(Double value) { averageCropScore = value; }
    public Double getAverageAdjacentScore() { return averageAdjacentScore; } public void setAverageAdjacentScore(Double value) { averageAdjacentScore = value; }
    public Integer getUniquePhotoCount() { return uniquePhotoCount; } public void setUniquePhotoCount(Integer value) { uniquePhotoCount = value; }
    public Integer getDuplicateGroupReuseCount() { return duplicateGroupReuseCount; } public void setDuplicateGroupReuseCount(Integer value) { duplicateGroupReuseCount = value; }
    public Long getCandidateEvaluations() { return candidateEvaluations; } public void setCandidateEvaluations(Long value) { candidateEvaluations = value; }
    public Long getEstimatedMemoryBytes() { return estimatedMemoryBytes; } public void setEstimatedMemoryBytes(Long value) { estimatedMemoryBytes = value; }
    public Long getRuntimeMillis() { return runtimeMillis; } public void setRuntimeMillis(Long value) { runtimeMillis = value; }
    public String getErrorCode() { return errorCode; } public void setErrorCode(String value) { errorCode = value; }
    public String getErrorMessage() { return errorMessage; } public void setErrorMessage(String value) { errorMessage = value; }
    public UUID getCreatedBy() { return createdBy; } public void setCreatedBy(UUID value) { createdBy = value; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant value) { updatedAt = value; }
    public Instant getCompletedAt() { return completedAt; } public void setCompletedAt(Instant value) { completedAt = value; }
}
