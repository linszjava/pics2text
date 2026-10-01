package com.pics2text.surface.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CylinderSurfaceMapperTest {
    @Test
    void mapsUnfoldedMillimetersToStableUvAndWorldCoordinates() {
        CylinderGeometry geometry = new CylinderGeometry(1_000, 3_000, 0);
        CylinderSurfaceMapper mapper = new CylinderSurfaceMapper(geometry);
        SurfacePoint start = mapper.map(0, 0);
        assertEquals(0, start.u(), 1e-12);
        assertEquals(1, start.v(), 1e-12);
        assertEquals(500, start.worldXmm(), 1e-9);
        assertEquals(1_500, start.worldYmm(), 1e-9);
        SurfacePoint quarter = mapper.map(geometry.circumferenceMm() / 4, 1_500);
        assertEquals(0.25, quarter.u(), 1e-12);
        assertEquals(0.5, quarter.v(), 1e-12);
        assertEquals(0, quarter.worldXmm(), 1e-9);
        assertEquals(500, quarter.worldZmm(), 1e-9);
        assertEquals(geometry.circumferenceMm() / 4, mapper.unfoldedX(0.25), 1e-9);
    }

    @Test
    void appliesAConfiguredSeamOffsetAndRejectsOutOfBoundsPoints() {
        CylinderGeometry geometry = new CylinderGeometry(100, 200, 50);
        CylinderSurfaceMapper mapper = new CylinderSurfaceMapper(geometry);
        assertEquals(0, mapper.map(50, 100).u(), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> mapper.map(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CylinderGeometry(100, 200, 400));
    }

    @Test
    void toleratesDatabaseScaleRoundingAtThePhysicalBoundary() {
        CylinderGeometry geometry = new CylinderGeometry(1_000, 1_000, 0);
        CylinderSurfaceMapper mapper = new CylinderSurfaceMapper(geometry);

        SurfacePoint edge = mapper.map(3_141.592654, 1_000.0000004);

        assertEquals(geometry.circumferenceMm(), edge.xMm(), 1e-12);
        assertEquals(geometry.axialLengthMm(), edge.yMm(), 1e-12);
        assertEquals(0, edge.u(), 1e-12);
        assertEquals(0, edge.v(), 1e-12);
        assertThrows(IllegalArgumentException.class,
                () -> mapper.map(geometry.circumferenceMm() + 0.001, 0));
    }
}
