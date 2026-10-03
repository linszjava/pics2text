package com.pics2text.layout.domain;

public record AutoLayoutRequest(long randomSeed, boolean allowPhotoReuse, int maxPhotoReuse,
        double targetLabL, double targetLabA, double targetLabB, int candidateWindow,
        int optimizationIterations, LayoutWeights weights) {
    public AutoLayoutRequest {
        if (maxPhotoReuse < 1 || maxPhotoReuse > 10_000) {
            throw new IllegalArgumentException("单张照片最大使用次数必须在1到10000之间");
        }
        if (!allowPhotoReuse && maxPhotoReuse != 1) {
            throw new IllegalArgumentException("禁止重复照片时最大使用次数必须为1");
        }
        validateLab("L", targetLabL);
        validateLab("A", targetLabA);
        validateLab("B", targetLabB);
        if (candidateWindow < 8 || candidateWindow > 512) {
            throw new IllegalArgumentException("候选窗口必须在8到512之间");
        }
        if (optimizationIterations < 0 || optimizationIterations > 200_000) {
            throw new IllegalArgumentException("优化迭代次数必须在0到200000之间");
        }
        if (weights == null) throw new IllegalArgumentException("评分权重不能为空");
    }

    private static void validateLab(String channel, double value) {
        if (!Double.isFinite(value) || value < 0 || value > 255) {
            throw new IllegalArgumentException("目标Lab " + channel + "通道必须在0到255之间");
        }
    }
}
