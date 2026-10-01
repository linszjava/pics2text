package com.pics2text.rendering.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.pics2text.rendering.domain.PixelRect;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RenderTilePlannerTest {
    @Test void indexesOnlyPlacementsIntersectingEachTileInStableOrder() {
        UUID photo = UUID.randomUUID();
        List<RenderTile> tiles = new RenderTilePlanner().plan(1_000, 700, 512, List.of(
                new RenderPlacement(8, photo, new PixelRect(500, 500, 100, 100), 0, 0, 1, 1),
                new RenderPlacement(2, photo, new PixelRect(10, 10, 100, 100), 0, 0, 1, 1)));
        assertEquals(4, tiles.size()); assertEquals(List.of(2, 8), tiles.get(0).slotIndexes());
        assertEquals(List.of(8), tiles.get(1).slotIndexes()); assertEquals(List.of(8), tiles.get(2).slotIndexes());
        assertEquals(List.of(8), tiles.get(3).slotIndexes());
    }

    @Test void rejectsAPlanThatWouldCreateAnUnsafeManifest() {
        assertThrows(IllegalArgumentException.class,
                () -> new RenderTilePlanner().plan(1_000_000_000, 1_000_000_000, 128, List.of()));
    }
}
