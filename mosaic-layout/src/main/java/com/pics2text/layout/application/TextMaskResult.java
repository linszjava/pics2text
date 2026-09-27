package com.pics2text.layout.application;

import java.util.List;

public record TextMaskResult(byte[] maskPng, byte[] debugPng,
        int boundsX, int boundsY, int boundsWidth, int boundsHeight,
        double estimatedStrokeWidth, double averageCoverage,
        List<String> warnings, List<TextMaskSlot> slots) {
}
