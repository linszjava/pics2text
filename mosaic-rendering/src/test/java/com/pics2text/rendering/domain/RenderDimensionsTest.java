package com.pics2text.rendering.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class RenderDimensionsTest {
    @Test void convertsMillimetersAndBleedToPixelsWithoutHiddenDpiAssumptions() {
        RenderDimensions value = RenderDimensions.of(254, 127, 2.54, 300);
        assertEquals(3_000, value.contentWidthPx()); assertEquals(1_500, value.contentHeightPx());
        assertEquals(30, value.bleedPx()); assertEquals(3_060, value.outputWidthPx());
        assertEquals(1_560, value.outputHeightPx()); assertEquals(300 / 25.4, value.pixelsPerMm(), 1e-12);
    }
    @Test void rejectsUnsafeDpiAndDimensions() {
        assertThrows(IllegalArgumentException.class, () -> RenderDimensions.of(100, 100, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> RenderDimensions.of(Double.NaN, 100, 0, 300));
    }
}
