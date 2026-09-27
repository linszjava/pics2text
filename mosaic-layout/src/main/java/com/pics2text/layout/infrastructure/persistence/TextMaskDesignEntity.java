package com.pics2text.layout.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.UUID;

@TableName("text_mask_design")
public class TextMaskDesignEntity {
    @TableId
    private UUID id;
    private UUID projectId;
    private String name;
    private String textContent;
    private String direction;
    private String fontId;
    private String fontVersion;
    private String fontSha256;
    private Integer fontSize;
    private Integer fontWeight;
    private Double letterSpacing;
    private Double lineSpacing;
    private Integer canvasWidth;
    private Integer canvasHeight;
    private Integer padding;
    private String gridStrategy;
    private String edgePolicy;
    private Integer cellWidth;
    private Integer cellHeight;
    private Integer gap;
    private Double coverageThreshold;
    private String status;
    private String algorithmVersion;
    private String contentHash;
    private String maskKey;
    private String debugKey;
    private Integer boundsX;
    private Integer boundsY;
    private Integer boundsWidth;
    private Integer boundsHeight;
    private Double estimatedStrokeWidth;
    private Double averageCoverage;
    private Integer slotCount;
    private String warningsJson;
    private String errorCode;
    private String errorMessage;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTextContent() { return textContent; }
    public void setTextContent(String textContent) { this.textContent = textContent; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getFontId() { return fontId; }
    public void setFontId(String fontId) { this.fontId = fontId; }
    public String getFontVersion() { return fontVersion; }
    public void setFontVersion(String fontVersion) { this.fontVersion = fontVersion; }
    public String getFontSha256() { return fontSha256; }
    public void setFontSha256(String fontSha256) { this.fontSha256 = fontSha256; }
    public Integer getFontSize() { return fontSize; }
    public void setFontSize(Integer fontSize) { this.fontSize = fontSize; }
    public Integer getFontWeight() { return fontWeight; }
    public void setFontWeight(Integer fontWeight) { this.fontWeight = fontWeight; }
    public Double getLetterSpacing() { return letterSpacing; }
    public void setLetterSpacing(Double letterSpacing) { this.letterSpacing = letterSpacing; }
    public Double getLineSpacing() { return lineSpacing; }
    public void setLineSpacing(Double lineSpacing) { this.lineSpacing = lineSpacing; }
    public Integer getCanvasWidth() { return canvasWidth; }
    public void setCanvasWidth(Integer canvasWidth) { this.canvasWidth = canvasWidth; }
    public Integer getCanvasHeight() { return canvasHeight; }
    public void setCanvasHeight(Integer canvasHeight) { this.canvasHeight = canvasHeight; }
    public Integer getPadding() { return padding; }
    public void setPadding(Integer padding) { this.padding = padding; }
    public String getGridStrategy() { return gridStrategy; }
    public void setGridStrategy(String gridStrategy) { this.gridStrategy = gridStrategy; }
    public String getEdgePolicy() { return edgePolicy; }
    public void setEdgePolicy(String edgePolicy) { this.edgePolicy = edgePolicy; }
    public Integer getCellWidth() { return cellWidth; }
    public void setCellWidth(Integer cellWidth) { this.cellWidth = cellWidth; }
    public Integer getCellHeight() { return cellHeight; }
    public void setCellHeight(Integer cellHeight) { this.cellHeight = cellHeight; }
    public Integer getGap() { return gap; }
    public void setGap(Integer gap) { this.gap = gap; }
    public Double getCoverageThreshold() { return coverageThreshold; }
    public void setCoverageThreshold(Double coverageThreshold) { this.coverageThreshold = coverageThreshold; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAlgorithmVersion() { return algorithmVersion; }
    public void setAlgorithmVersion(String algorithmVersion) { this.algorithmVersion = algorithmVersion; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public String getMaskKey() { return maskKey; }
    public void setMaskKey(String maskKey) { this.maskKey = maskKey; }
    public String getDebugKey() { return debugKey; }
    public void setDebugKey(String debugKey) { this.debugKey = debugKey; }
    public Integer getBoundsX() { return boundsX; }
    public void setBoundsX(Integer boundsX) { this.boundsX = boundsX; }
    public Integer getBoundsY() { return boundsY; }
    public void setBoundsY(Integer boundsY) { this.boundsY = boundsY; }
    public Integer getBoundsWidth() { return boundsWidth; }
    public void setBoundsWidth(Integer boundsWidth) { this.boundsWidth = boundsWidth; }
    public Integer getBoundsHeight() { return boundsHeight; }
    public void setBoundsHeight(Integer boundsHeight) { this.boundsHeight = boundsHeight; }
    public Double getEstimatedStrokeWidth() { return estimatedStrokeWidth; }
    public void setEstimatedStrokeWidth(Double estimatedStrokeWidth) { this.estimatedStrokeWidth = estimatedStrokeWidth; }
    public Double getAverageCoverage() { return averageCoverage; }
    public void setAverageCoverage(Double averageCoverage) { this.averageCoverage = averageCoverage; }
    public Integer getSlotCount() { return slotCount; }
    public void setSlotCount(Integer slotCount) { this.slotCount = slotCount; }
    public String getWarningsJson() { return warningsJson; }
    public void setWarningsJson(String warningsJson) { this.warningsJson = warningsJson; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
