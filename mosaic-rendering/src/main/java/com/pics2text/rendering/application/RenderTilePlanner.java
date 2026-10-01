package com.pics2text.rendering.application;

import com.pics2text.rendering.domain.PixelRect;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RenderTilePlanner {
    public static final int MAX_TILE_COUNT = 100_000;

    public List<RenderTile> plan(int outputWidth, int outputHeight, int tileSize,
            List<RenderPlacement> placements) {
        if (outputWidth <= 0 || outputHeight <= 0 || tileSize < 128 || tileSize > 8192) {
            throw new IllegalArgumentException("渲染画布或瓦片尺寸无效");
        }
        int columns = divideRoundUp(outputWidth, tileSize); int rows = divideRoundUp(outputHeight, tileSize);
        long tileCount = (long) columns * rows;
        if (tileCount > MAX_TILE_COUNT) {
            throw new IllegalArgumentException("渲染瓦片数量超出上限：" + tileCount);
        }
        Map<Long, List<Integer>> index = new HashMap<>();
        for (RenderPlacement placement : placements) {
            PixelRect bounds = placement.bounds();
            if (bounds.maxX() > outputWidth || bounds.maxY() > outputHeight) {
                throw new IllegalArgumentException("槽位超出渲染画布：" + placement.slotIndex());
            }
            int minColumn = bounds.x() / tileSize;
            int maxColumn = (bounds.maxX() - 1) / tileSize;
            int minRow = bounds.y() / tileSize;
            int maxRow = (bounds.maxY() - 1) / tileSize;
            for (int row = minRow; row <= maxRow; row++) for (int column = minColumn; column <= maxColumn; column++) {
                index.computeIfAbsent(key(column, row), ignored -> new ArrayList<>()).add(placement.slotIndex());
            }
        }
        List<RenderTile> tiles = new ArrayList<>((int) tileCount);
        for (int row = 0; row < rows; row++) for (int column = 0; column < columns; column++) {
            int x = column * tileSize; int y = row * tileSize;
            PixelRect bounds = new PixelRect(x, y, Math.min(tileSize, outputWidth - x),
                    Math.min(tileSize, outputHeight - y));
            List<Integer> slots = index.getOrDefault(key(column, row), List.of()).stream()
                    .sorted(Comparator.naturalOrder()).toList();
            tiles.add(new RenderTile(column, row, bounds, slots));
        }
        return List.copyOf(tiles);
    }

    private int divideRoundUp(int value, int divisor) { return (value + divisor - 1) / divisor; }
    private long key(int column, int row) { return ((long) row << 32) | (column & 0xffffffffL); }
}
