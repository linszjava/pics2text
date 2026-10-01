package com.pics2text.surface.application;

import com.pics2text.surface.domain.SurfaceRect;
import com.pics2text.surface.domain.SurfaceRegionType;

public record SurfaceRegion(int regionIndex, SurfaceRegionType type, String name,
        SurfaceRect bounds, boolean blocking) { }
