package com.pics2text.photoasset.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

public class PhotoAnalysisEntity {
    private UUID photoId;
    private UUID projectId;
    private String algorithmVersion;
    private Double sharpness;
    private Double brightness;
    private Double contrast;
    private Double labL;
    private Double labA;
    private Double labB;
    private String dHash;
    private String pHash;
    private Integer faceCount;
    private String facesJson;
    private Double subjectX;
    private Double subjectY;
    private Double subjectWidth;
    private Double subjectHeight;
    private Double cropX;
    private Double cropY;
    private Double cropWidth;
    private Double cropHeight;
    private UUID duplicateGroupId;
    private String autoReviewDecision;
    private String reviewReasonsJson;
    private Integer orientationApplied;
    private Instant analyzedAt;
    private Instant updatedAt;

    public UUID getPhotoId() { return photoId; }
    public void setPhotoId(UUID photoId) { this.photoId = photoId; }
    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public String getAlgorithmVersion() { return algorithmVersion; }
    public void setAlgorithmVersion(String algorithmVersion) { this.algorithmVersion = algorithmVersion; }
    public Double getSharpness() { return sharpness; }
    public void setSharpness(Double sharpness) { this.sharpness = sharpness; }
    public Double getBrightness() { return brightness; }
    public void setBrightness(Double brightness) { this.brightness = brightness; }
    public Double getContrast() { return contrast; }
    public void setContrast(Double contrast) { this.contrast = contrast; }
    public Double getLabL() { return labL; }
    public void setLabL(Double labL) { this.labL = labL; }
    public Double getLabA() { return labA; }
    public void setLabA(Double labA) { this.labA = labA; }
    public Double getLabB() { return labB; }
    public void setLabB(Double labB) { this.labB = labB; }
    public String getDHash() { return dHash; }
    public void setDHash(String dHash) { this.dHash = dHash; }
    public String getPHash() { return pHash; }
    public void setPHash(String pHash) { this.pHash = pHash; }
    public Integer getFaceCount() { return faceCount; }
    public void setFaceCount(Integer faceCount) { this.faceCount = faceCount; }
    public String getFacesJson() { return facesJson; }
    public void setFacesJson(String facesJson) { this.facesJson = facesJson; }
    public Double getSubjectX() { return subjectX; }
    public void setSubjectX(Double subjectX) { this.subjectX = subjectX; }
    public Double getSubjectY() { return subjectY; }
    public void setSubjectY(Double subjectY) { this.subjectY = subjectY; }
    public Double getSubjectWidth() { return subjectWidth; }
    public void setSubjectWidth(Double subjectWidth) { this.subjectWidth = subjectWidth; }
    public Double getSubjectHeight() { return subjectHeight; }
    public void setSubjectHeight(Double subjectHeight) { this.subjectHeight = subjectHeight; }
    public Double getCropX() { return cropX; }
    public void setCropX(Double cropX) { this.cropX = cropX; }
    public Double getCropY() { return cropY; }
    public void setCropY(Double cropY) { this.cropY = cropY; }
    public Double getCropWidth() { return cropWidth; }
    public void setCropWidth(Double cropWidth) { this.cropWidth = cropWidth; }
    public Double getCropHeight() { return cropHeight; }
    public void setCropHeight(Double cropHeight) { this.cropHeight = cropHeight; }
    public UUID getDuplicateGroupId() { return duplicateGroupId; }
    public void setDuplicateGroupId(UUID duplicateGroupId) { this.duplicateGroupId = duplicateGroupId; }
    public String getAutoReviewDecision() { return autoReviewDecision; }
    public void setAutoReviewDecision(String autoReviewDecision) { this.autoReviewDecision = autoReviewDecision; }
    public String getReviewReasonsJson() { return reviewReasonsJson; }
    public void setReviewReasonsJson(String reviewReasonsJson) { this.reviewReasonsJson = reviewReasonsJson; }
    public Integer getOrientationApplied() { return orientationApplied; }
    public void setOrientationApplied(Integer orientationApplied) { this.orientationApplied = orientationApplied; }
    public Instant getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(Instant analyzedAt) { this.analyzedAt = analyzedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
