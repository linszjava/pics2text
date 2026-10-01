package com.pics2text.surface.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pics2text.surface.domain.CylinderGeometry;
import com.pics2text.surface.domain.SurfaceRect;
import com.pics2text.surface.domain.SurfaceRegionType;
import java.util.List;
import org.junit.jupiter.api.Test;

class SurfaceLayoutValidatorTest {
    private final SurfaceLayoutValidator validator = new SurfaceLayoutValidator();

    @Test
    void keepsPixelToMillimeterScaleExact() {
        SurfaceMapping mapping = new SurfaceMapping(2_000, 1_000,
                new SurfaceRect(100, 50, 1_000, 500));
        assertEquals(0.5, mapping.scaleXmmPerPixel(), 1e-12);
        assertEquals(0.5, mapping.scaleYmmPerPixel(), 1e-12);
        assertEquals(new SurfaceRect(150, 100, 100, 50), mapping.map(100, 100, 200, 100));
    }

    @Test
    void reportsSeamMarginAndNoPrintConflictsBeforePublication() {
        CylinderGeometry geometry = new CylinderGeometry(1_000, 1_000, 0);
        SurfaceMapping mapping = new SurfaceMapping(1_000, 1_000,
                new SurfaceRect(0, 0, geometry.circumferenceMm(), 1_000));
        List<SurfaceRegion> regions = List.of(new SurfaceRegion(0, SurfaceRegionType.NO_PRINT,
                "检修口", new SurfaceRect(1_500, 400, 300, 200), true));
        var result = validator.validate(geometry, mapping, 20, 10, 10, 10, 10,
                regions, List.of(new SurfaceSlot(1, 0, 200, 20, 20),
                        new SurfaceSlot(2, 490, 450, 80, 80)));
        assertFalse(result.valid());
        assertTrue(result.violations().stream().anyMatch(v -> v.code().equals("SEAM_CONFLICT")));
        assertTrue(result.violations().stream().anyMatch(v -> v.code().equals("NO_PRINT_CONFLICT")));
    }

    @Test
    void acceptsSlotsInsideThePrintableArea() {
        CylinderGeometry geometry = new CylinderGeometry(1_000, 1_000, 0);
        SurfaceMapping mapping = new SurfaceMapping(1_000, 1_000,
                new SurfaceRect(0, 0, geometry.circumferenceMm(), 1_000));
        var result = validator.validate(geometry, mapping, 20, 10, 10, 10, 10,
                List.of(), List.of(new SurfaceSlot(1, 100, 100, 50, 50)));
        assertTrue(result.valid());
        assertEquals(0, result.violations().size());
    }
}
