package com.pics2text.job.messaging;

import com.pics2text.job.domain.JobType;

import java.util.UUID;

public record JobMessage(
        UUID eventId,
        UUID jobId,
        UUID projectId,
        JobType type,
        int attempt,
        String payloadJson
) {
}
