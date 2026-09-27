package com.pics2text.layout.infrastructure.persistence;

import java.util.UUID;

public class LayoutPhotoCandidateEntity {
    private UUID photoId; private Double sharpness; private Double brightness; private Double contrast;
    private Double labL; private Double labA; private Double labB; private String pHash;
    private UUID duplicateGroupId; private Double cropX; private Double cropY;
    private Double cropWidth; private Double cropHeight;
    public UUID getPhotoId() { return photoId; } public void setPhotoId(UUID value) { photoId = value; }
    public Double getSharpness() { return sharpness; } public void setSharpness(Double value) { sharpness = value; }
    public Double getBrightness() { return brightness; } public void setBrightness(Double value) { brightness = value; }
    public Double getContrast() { return contrast; } public void setContrast(Double value) { contrast = value; }
    public Double getLabL() { return labL; } public void setLabL(Double value) { labL = value; }
    public Double getLabA() { return labA; } public void setLabA(Double value) { labA = value; }
    public Double getLabB() { return labB; } public void setLabB(Double value) { labB = value; }
    public String getPHash() { return pHash; } public void setPHash(String value) { pHash = value; }
    public UUID getDuplicateGroupId() { return duplicateGroupId; } public void setDuplicateGroupId(UUID value) { duplicateGroupId = value; }
    public Double getCropX() { return cropX; } public void setCropX(Double value) { cropX = value; }
    public Double getCropY() { return cropY; } public void setCropY(Double value) { cropY = value; }
    public Double getCropWidth() { return cropWidth; } public void setCropWidth(Double value) { cropWidth = value; }
    public Double getCropHeight() { return cropHeight; } public void setCropHeight(Double value) { cropHeight = value; }
}
