package com.pics2text.job.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class JobStateMachine {
    private static final Map<JobStatus, Set<JobStatus>> TRANSITIONS = new EnumMap<>(JobStatus.class);

    static {
        TRANSITIONS.put(JobStatus.PENDING, EnumSet.of(JobStatus.RUNNING, JobStatus.CANCELLED));
        TRANSITIONS.put(JobStatus.RUNNING, EnumSet.of(JobStatus.SUCCEEDED, JobStatus.RETRY_WAIT,
                JobStatus.FAILED, JobStatus.DEAD_LETTERED, JobStatus.CANCELLATION_REQUESTED));
        TRANSITIONS.put(JobStatus.RETRY_WAIT, EnumSet.of(JobStatus.RUNNING, JobStatus.CANCELLED,
                JobStatus.DEAD_LETTERED));
        TRANSITIONS.put(JobStatus.CANCELLATION_REQUESTED, EnumSet.of(JobStatus.CANCELLED,
                JobStatus.SUCCEEDED, JobStatus.RETRY_WAIT, JobStatus.DEAD_LETTERED));
        TRANSITIONS.put(JobStatus.SUCCEEDED, EnumSet.noneOf(JobStatus.class));
        TRANSITIONS.put(JobStatus.FAILED, EnumSet.of(JobStatus.PENDING));
        TRANSITIONS.put(JobStatus.DEAD_LETTERED, EnumSet.of(JobStatus.PENDING));
        TRANSITIONS.put(JobStatus.CANCELLED, EnumSet.of(JobStatus.PENDING));
    }

    private JobStateMachine() {
    }

    public static boolean canTransition(JobStatus from, JobStatus to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireTransition(JobStatus from, JobStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("不允许的作业状态变更：" + from + " -> " + to);
        }
    }
}
