package com.pics2text.job.infrastructure.messaging;

import com.pics2text.job.domain.JobType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobWorkerMetricsTest {

    @Test
    void recordsMessageAndExecutionOutcomesWithBoundedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        JobWorkerMetrics metrics = new JobWorkerMetrics(registry);

        metrics.received(JobType.PHOTO_ANALYSIS);
        metrics.malformed();
        metrics.skipped(JobType.PHOTO_ANALYSIS, "already_completed");
        metrics.completed(JobType.PHOTO_ANALYSIS, "succeeded", System.nanoTime());
        metrics.infrastructureFailure(JobType.PHOTO_ANALYSIS);

        assertThat(registry.get("pics2text.jobs.messages")
                .tags("result", "received", "type", "PHOTO_ANALYSIS")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get("pics2text.jobs.messages")
                .tags("result", "malformed", "type", "unknown")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get("pics2text.jobs.messages")
                .tags("result", "requeued", "type", "PHOTO_ANALYSIS")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get("pics2text.jobs.executions")
                .tags("reason", "already_completed", "result", "skipped", "type", "PHOTO_ANALYSIS")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get("pics2text.jobs.executions")
                .tags("reason", "none", "result", "succeeded", "type", "PHOTO_ANALYSIS")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get("pics2text.jobs.duration")
                .tags("result", "succeeded", "type", "PHOTO_ANALYSIS")
                .timer().count()).isEqualTo(1);
    }
}
