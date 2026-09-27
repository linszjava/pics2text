package com.pics2text.layout.infrastructure.persistence;

import java.util.UUID;

public record AutoLayoutAssignmentEntity(UUID layoutId, int slotIndex, UUID photoId,
        double totalScore, double qualityScore, double brightnessScore, double colorScore,
        double cropScore, double duplicateScore, double adjacentScore, double cropX,
        double cropY, double cropWidth, double cropHeight) { }
