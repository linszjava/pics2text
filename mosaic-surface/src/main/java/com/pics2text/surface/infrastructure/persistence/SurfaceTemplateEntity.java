package com.pics2text.surface.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

public class SurfaceTemplateEntity {
    private UUID id; private UUID projectId; private String name; private String surfaceType;
    private String unit; private Double diameterMm; private Double circumferenceMm;
    private Double axialLengthMm; private Double seamXmm; private Double seamSafetyMm;
    private Double safeTopMm; private Double safeRightMm; private Double safeBottomMm;
    private Double safeLeftMm; private Integer templateVersion; private String status;
    private String geometryHash; private UUID createdBy; private Instant createdAt;
    public UUID getId() { return id; } public void setId(UUID v) { id = v; }
    public UUID getProjectId() { return projectId; } public void setProjectId(UUID v) { projectId = v; }
    public String getName() { return name; } public void setName(String v) { name = v; }
    public String getSurfaceType() { return surfaceType; } public void setSurfaceType(String v) { surfaceType = v; }
    public String getUnit() { return unit; } public void setUnit(String v) { unit = v; }
    public Double getDiameterMm() { return diameterMm; } public void setDiameterMm(Double v) { diameterMm = v; }
    public Double getCircumferenceMm() { return circumferenceMm; } public void setCircumferenceMm(Double v) { circumferenceMm = v; }
    public Double getAxialLengthMm() { return axialLengthMm; } public void setAxialLengthMm(Double v) { axialLengthMm = v; }
    public Double getSeamXmm() { return seamXmm; } public void setSeamXmm(Double v) { seamXmm = v; }
    public Double getSeamSafetyMm() { return seamSafetyMm; } public void setSeamSafetyMm(Double v) { seamSafetyMm = v; }
    public Double getSafeTopMm() { return safeTopMm; } public void setSafeTopMm(Double v) { safeTopMm = v; }
    public Double getSafeRightMm() { return safeRightMm; } public void setSafeRightMm(Double v) { safeRightMm = v; }
    public Double getSafeBottomMm() { return safeBottomMm; } public void setSafeBottomMm(Double v) { safeBottomMm = v; }
    public Double getSafeLeftMm() { return safeLeftMm; } public void setSafeLeftMm(Double v) { safeLeftMm = v; }
    public Integer getTemplateVersion() { return templateVersion; } public void setTemplateVersion(Integer v) { templateVersion = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public String getGeometryHash() { return geometryHash; } public void setGeometryHash(String v) { geometryHash = v; }
    public UUID getCreatedBy() { return createdBy; } public void setCreatedBy(UUID v) { createdBy = v; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant v) { createdAt = v; }
}
