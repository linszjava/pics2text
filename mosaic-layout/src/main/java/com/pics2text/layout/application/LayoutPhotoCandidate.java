package com.pics2text.layout.application;

import java.util.UUID;

public record LayoutPhotoCandidate(UUID photoId, double sharpness, double brightness, double contrast,
        double labL, double labA, double labB, String perceptualHash, UUID duplicateGroupId,
        double cropX, double cropY, double cropWidth, double cropHeight) {
    public LayoutPhotoCandidate {
        if (photoId == null) throw new IllegalArgumentException("照片ID不能为空");
        if (perceptualHash == null || !perceptualHash.matches("[0-9a-fA-F]{16}")) {
            throw new IllegalArgumentException("照片感知哈希格式无效");
        }
        if (cropWidth <= 0 || cropHeight <= 0 || cropWidth > 1 || cropHeight > 1) {
            throw new IllegalArgumentException("照片裁切区域无效");
        }
    }

    double cropLoss() {
        return 1d - cropWidth * cropHeight;
    }
}
