package com.pics2text.rendering.application;

import com.pics2text.rendering.domain.PixelRect;
import java.util.List;

public record RenderTile(int column, int row, PixelRect bounds, List<Integer> slotIndexes) { }
