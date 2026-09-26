package com.pics2text.photoasset.domain;

public enum PhotoVariantType {
    THUMBNAIL_256(256),
    PREVIEW_1024(1024),
    ANALYSIS_2048(2048);

    private final int maximumEdge;

    PhotoVariantType(int maximumEdge) {
        this.maximumEdge = maximumEdge;
    }

    public int maximumEdge() {
        return maximumEdge;
    }
}
