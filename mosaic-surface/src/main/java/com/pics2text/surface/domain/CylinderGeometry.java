package com.pics2text.surface.domain;

public record CylinderGeometry(double diameterMm, double axialLengthMm, double seamXmm) {
    public CylinderGeometry {
        if (!Double.isFinite(diameterMm) || !Double.isFinite(axialLengthMm)
                || !Double.isFinite(seamXmm) || diameterMm <= 0 || axialLengthMm <= 0) {
            throw new IllegalArgumentException("圆柱直径和轴向长度必须为有限正数");
        }
        double circumference = Math.PI * diameterMm;
        if (seamXmm < 0 || seamXmm >= circumference) {
            throw new IllegalArgumentException("接缝位置必须位于圆柱展开宽度内");
        }
    }

    public double radiusMm() { return diameterMm / 2.0; }
    public double circumferenceMm() { return Math.PI * diameterMm; }
}
