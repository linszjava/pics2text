package com.pics2text.job.domain;

public enum JobType {
    PHOTO_ANALYSIS("analysis"),
    TEXT_MASK_GENERATION("analysis"),
    LAYOUT_GENERATION("analysis"),
    LAYOUT_PREVIEW("render"),
    PRINT_RENDER("render"),
    INFRASTRUCTURE_PROBE("analysis");

    private final String routingKey;

    JobType(String routingKey) {
        this.routingKey = routingKey;
    }

    public String routingKey() {
        return routingKey;
    }
}
