package com.pics2text.layout.domain;

public record LayoutWeights(double quality, double brightness, double color, double crop,
        double duplicate, double adjacent) {
    public LayoutWeights {
        validate("quality", quality);
        validate("brightness", brightness);
        validate("color", color);
        validate("crop", crop);
        validate("duplicate", duplicate);
        validate("adjacent", adjacent);
        if (quality + brightness + color + crop + duplicate + adjacent <= 0) {
            throw new IllegalArgumentException("至少需要一个大于0的评分权重");
        }
    }

    public static LayoutWeights defaults() {
        return new LayoutWeights(1.5, 1.0, 1.0, 1.0, 2.0, 1.5);
    }

    public double sum() {
        return quality + brightness + color + crop + duplicate + adjacent;
    }

    private static void validate(String name, double value) {
        if (!Double.isFinite(value) || value < 0 || value > 10) {
            throw new IllegalArgumentException(name + "评分权重必须在0到10之间");
        }
    }
}
