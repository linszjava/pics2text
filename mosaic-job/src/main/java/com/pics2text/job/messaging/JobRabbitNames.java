package com.pics2text.job.messaging;

public final class JobRabbitNames {
    public static final String MAIN_EXCHANGE = "mosaic.jobs";
    public static final String RETRY_EXCHANGE = "mosaic.jobs.retry";
    public static final String DEAD_LETTER_EXCHANGE = "mosaic.jobs.dlx";
    public static final String ANALYSIS_QUEUE = "mosaic.jobs.analysis.q";
    public static final String RENDER_QUEUE = "mosaic.jobs.render.q";
    public static final String ANALYSIS_RETRY_QUEUE = "mosaic.jobs.analysis.retry.q";
    public static final String RENDER_RETRY_QUEUE = "mosaic.jobs.render.retry.q";
    public static final String DEAD_LETTER_QUEUE = "mosaic.jobs.dead.q";

    private JobRabbitNames() {
    }
}
