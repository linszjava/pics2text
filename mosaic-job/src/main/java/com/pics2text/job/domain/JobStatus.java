package com.pics2text.job.domain;

public enum JobStatus {
    PENDING(false),
    RUNNING(false),
    RETRY_WAIT(false),
    CANCELLATION_REQUESTED(false),
    SUCCEEDED(true),
    FAILED(true),
    DEAD_LETTERED(true),
    CANCELLED(true);

    private final boolean terminal;

    JobStatus(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
