package com.pics2text.surface.application;

import com.pics2text.surface.domain.CylinderGeometry;
import com.pics2text.surface.domain.SurfaceRect;
import com.pics2text.surface.domain.SurfaceRegionType;
import java.util.ArrayList;
import java.util.List;

public final class SurfaceLayoutValidator {
    private static final int MAX_REPORTED_VIOLATIONS = 10_000;
    public SurfaceValidationResult validate(CylinderGeometry geometry, SurfaceMapping mapping,
            double seamSafetyMm, double topSafetyMm, double rightSafetyMm,
            double bottomSafetyMm, double leftSafetyMm, List<SurfaceRegion> regions,
            List<SurfaceSlot> slots) {
        requireMargin(seamSafetyMm); requireMargin(topSafetyMm); requireMargin(rightSafetyMm);
        requireMargin(bottomSafetyMm); requireMargin(leftSafetyMm);
        double circumference = geometry.circumferenceMm();
        SurfaceRect area = mapping.printAreaMm();
        if (area.maxX() > circumference + 1e-6 || area.maxY() > geometry.axialLengthMm() + 1e-6) {
            throw new IllegalArgumentException("布局印刷区域超出曲面物理范围");
        }
        List<SurfaceViolation> violations = new ArrayList<>();
        for (SurfaceSlot slot : slots) {
            SurfaceRect rect = mapping.map(slot.x(), slot.y(), slot.width(), slot.height());
            if (rect.x() < leftSafetyMm || rect.maxX() > circumference - rightSafetyMm
                    || rect.y() < topSafetyMm || rect.maxY() > geometry.axialLengthMm() - bottomSafetyMm) {
                violations.add(new SurfaceViolation("SAFETY_MARGIN_CONFLICT", slot.slotIndex(), null,
                        "槽位进入曲面安全边距"));
            }
            if (crossesSeam(rect, geometry.seamXmm(), seamSafetyMm, circumference)) {
                violations.add(new SurfaceViolation("SEAM_CONFLICT", slot.slotIndex(), null,
                        "槽位与接缝安全区域冲突"));
            }
            for (SurfaceRegion region : regions) {
                if (region.blocking() && region.type() == SurfaceRegionType.NO_PRINT
                        && rect.intersects(region.bounds())) {
                    violations.add(new SurfaceViolation("NO_PRINT_CONFLICT", slot.slotIndex(),
                            region.regionIndex(), "槽位与禁印区域“" + region.name() + "”冲突"));
                }
                if (violations.size() >= MAX_REPORTED_VIOLATIONS) break;
            }
            if (violations.size() >= MAX_REPORTED_VIOLATIONS) {
                violations.add(new SurfaceViolation("VIOLATION_LIMIT_REACHED", slot.slotIndex(), null,
                        "冲突数量过多，结果已截断"));
                break;
            }
        }
        return new SurfaceValidationResult(violations.isEmpty(), violations);
    }

    private boolean crossesSeam(SurfaceRect rect, double seam, double margin, double circumference) {
        if (margin <= 0) return false;
        double start = seam - margin;
        double end = seam + margin;
        if (start >= 0 && end <= circumference) return overlapsX(rect, start, end);
        if (start < 0) return overlapsX(rect, 0, end) || overlapsX(rect, circumference + start, circumference);
        return overlapsX(rect, start, circumference) || overlapsX(rect, 0, end - circumference);
    }

    private boolean overlapsX(SurfaceRect rect, double start, double end) {
        return rect.x() < end - 1e-9 && rect.maxX() > start + 1e-9;
    }

    private void requireMargin(double value) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("安全边距不能为负数");
    }
}
