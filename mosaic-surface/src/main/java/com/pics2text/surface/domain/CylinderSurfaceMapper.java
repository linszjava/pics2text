package com.pics2text.surface.domain;

public final class CylinderSurfaceMapper {
    private static final double BOUNDARY_TOLERANCE_MM = 1e-6;

    private final CylinderGeometry geometry;

    public CylinderSurfaceMapper(CylinderGeometry geometry) { this.geometry = geometry; }

    public SurfacePoint map(double xMm, double yMm) {
        if (!Double.isFinite(xMm) || !Double.isFinite(yMm) || xMm < 0
                || xMm > geometry.circumferenceMm() + BOUNDARY_TOLERANCE_MM
                || yMm < 0 || yMm > geometry.axialLengthMm() + BOUNDARY_TOLERANCE_MM) {
            throw new IllegalArgumentException("展开面坐标超出圆柱物理范围");
        }
        double boundedXmm = Math.min(xMm, geometry.circumferenceMm());
        double boundedYmm = Math.min(yMm, geometry.axialLengthMm());
        double u = normalize((boundedXmm - geometry.seamXmm()) / geometry.circumferenceMm());
        double v = 1.0 - boundedYmm / geometry.axialLengthMm();
        double angle = u * Math.PI * 2.0;
        return new SurfacePoint(boundedXmm, boundedYmm, u, v,
                geometry.radiusMm() * Math.cos(angle),
                geometry.axialLengthMm() / 2.0 - boundedYmm,
                geometry.radiusMm() * Math.sin(angle));
    }

    public double unfoldedX(double u) {
        if (!Double.isFinite(u) || u < 0 || u > 1) throw new IllegalArgumentException("UV坐标必须位于0到1之间");
        double x = u * geometry.circumferenceMm() + geometry.seamXmm();
        return x >= geometry.circumferenceMm() ? x - geometry.circumferenceMm() : x;
    }

    private static double normalize(double value) {
        double result = value - Math.floor(value);
        return result == 1.0 ? 0.0 : result;
    }
}
