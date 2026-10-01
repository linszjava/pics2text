package com.pics2text.rendering.application;

import com.pics2text.rendering.domain.PixelRect;
import java.util.UUID;

public record RenderPlacement(int slotIndex, UUID photoId, PixelRect bounds,
        double cropX, double cropY, double cropWidth, double cropHeight) { }
