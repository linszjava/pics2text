package com.pics2text.layout.domain;

public record FontAsset(String id, String family, String version, String license,
        String source, String sha256, int minimumWeight, int maximumWeight) {
}
