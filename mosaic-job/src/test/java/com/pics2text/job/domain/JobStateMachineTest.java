package com.pics2text.job.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobStateMachineTest {
    @Test
    void supportsNormalRetryAndCancellationPaths() {
        assertThat(JobStateMachine.canTransition(JobStatus.PENDING, JobStatus.RUNNING)).isTrue();
        assertThat(JobStateMachine.canTransition(JobStatus.RUNNING, JobStatus.RETRY_WAIT)).isTrue();
        assertThat(JobStateMachine.canTransition(JobStatus.RUNNING, JobStatus.CANCELLATION_REQUESTED)).isTrue();
        assertThat(JobStateMachine.canTransition(JobStatus.CANCELLATION_REQUESTED, JobStatus.CANCELLED)).isTrue();
        assertThat(JobStateMachine.canTransition(JobStatus.DEAD_LETTERED, JobStatus.PENDING)).isTrue();
    }

    @Test
    void rejectsTerminalStateMutation() {
        assertThatThrownBy(() -> JobStateMachine.requireTransition(JobStatus.SUCCEEDED, JobStatus.RUNNING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUCCEEDED -> RUNNING");
    }
}
