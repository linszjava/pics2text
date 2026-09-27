package com.pics2text.layout.application;

import java.util.List;

public record AutoLayoutResult(List<LayoutAssignment> assignments, double initialAverageScore,
        double finalAverageScore, double averageBrightnessScore, double averageColorScore,
        double averageCropScore, double averageAdjacentScore, int uniquePhotoCount,
        int duplicateGroupReuseCount, long candidateEvaluations, long estimatedMemoryBytes) {
}
