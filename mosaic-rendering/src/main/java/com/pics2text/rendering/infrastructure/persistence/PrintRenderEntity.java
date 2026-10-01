package com.pics2text.rendering.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

public class PrintRenderEntity {
    private UUID id; private UUID projectId; private UUID layoutId; private UUID layoutVersionId;
    private UUID surfaceBindingId; private UUID jobId; private String status; private String format;
    private int dpi; private double bleedMm; private int tileSize; private String compression;
    private String colorProfile; private boolean cropMarks; private boolean registrationMarks;
    private int contentWidthPx; private int contentHeightPx; private int outputWidthPx; private int outputHeightPx;
    private String inputManifestJson; private String inputManifestHash; private String outputObjectKey;
    private String outputSha256; private Long outputByteSize; private String outputManifestJson;
    private String outputManifestHash; private String locationListObjectKey; private String locationListSha256;
    private String errorCode; private String errorMessage; private UUID createdBy; private Instant createdAt;
    private Instant updatedAt; private Instant completedAt;

    public UUID getId() { return id; } public void setId(UUID v) { id = v; }
    public UUID getProjectId() { return projectId; } public void setProjectId(UUID v) { projectId = v; }
    public UUID getLayoutId() { return layoutId; } public void setLayoutId(UUID v) { layoutId = v; }
    public UUID getLayoutVersionId() { return layoutVersionId; } public void setLayoutVersionId(UUID v) { layoutVersionId = v; }
    public UUID getSurfaceBindingId() { return surfaceBindingId; } public void setSurfaceBindingId(UUID v) { surfaceBindingId = v; }
    public UUID getJobId() { return jobId; } public void setJobId(UUID v) { jobId = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public String getFormat() { return format; } public void setFormat(String v) { format = v; }
    public int getDpi() { return dpi; } public void setDpi(int v) { dpi = v; }
    public double getBleedMm() { return bleedMm; } public void setBleedMm(double v) { bleedMm = v; }
    public int getTileSize() { return tileSize; } public void setTileSize(int v) { tileSize = v; }
    public String getCompression() { return compression; } public void setCompression(String v) { compression = v; }
    public String getColorProfile() { return colorProfile; } public void setColorProfile(String v) { colorProfile = v; }
    public boolean isCropMarks() { return cropMarks; } public void setCropMarks(boolean v) { cropMarks = v; }
    public boolean isRegistrationMarks() { return registrationMarks; } public void setRegistrationMarks(boolean v) { registrationMarks = v; }
    public int getContentWidthPx() { return contentWidthPx; } public void setContentWidthPx(int v) { contentWidthPx = v; }
    public int getContentHeightPx() { return contentHeightPx; } public void setContentHeightPx(int v) { contentHeightPx = v; }
    public int getOutputWidthPx() { return outputWidthPx; } public void setOutputWidthPx(int v) { outputWidthPx = v; }
    public int getOutputHeightPx() { return outputHeightPx; } public void setOutputHeightPx(int v) { outputHeightPx = v; }
    public String getInputManifestJson() { return inputManifestJson; } public void setInputManifestJson(String v) { inputManifestJson = v; }
    public String getInputManifestHash() { return inputManifestHash; } public void setInputManifestHash(String v) { inputManifestHash = v; }
    public String getOutputObjectKey() { return outputObjectKey; } public void setOutputObjectKey(String v) { outputObjectKey = v; }
    public String getOutputSha256() { return outputSha256; } public void setOutputSha256(String v) { outputSha256 = v; }
    public Long getOutputByteSize() { return outputByteSize; } public void setOutputByteSize(Long v) { outputByteSize = v; }
    public String getOutputManifestJson() { return outputManifestJson; } public void setOutputManifestJson(String v) { outputManifestJson = v; }
    public String getOutputManifestHash() { return outputManifestHash; } public void setOutputManifestHash(String v) { outputManifestHash = v; }
    public String getLocationListObjectKey() { return locationListObjectKey; } public void setLocationListObjectKey(String v) { locationListObjectKey = v; }
    public String getLocationListSha256() { return locationListSha256; } public void setLocationListSha256(String v) { locationListSha256 = v; }
    public String getErrorCode() { return errorCode; } public void setErrorCode(String v) { errorCode = v; }
    public String getErrorMessage() { return errorMessage; } public void setErrorMessage(String v) { errorMessage = v; }
    public UUID getCreatedBy() { return createdBy; } public void setCreatedBy(UUID v) { createdBy = v; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant v) { createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant v) { updatedAt = v; }
    public Instant getCompletedAt() { return completedAt; } public void setCompletedAt(Instant v) { completedAt = v; }
}
