package com.pics2text.layout.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

public record LayoutEditOperationEntity(UUID id, UUID layoutId, long revision, UUID clientBatchId,
        int commandIndex, String commandType, Integer slotIndex, Integer secondarySlotIndex,
        String beforeJson, String afterJson, long baseLockVersion, long resultingLockVersion,
        UUID actorId, Instant createdAt) { }
