package com.pics2text.rendering.application;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;

public final class LayoutColorPreviewGenerator {
    public static final String ALGORITHM_VERSION = "lab-color-preview-v1";
    public static final int MAX_EDGE = 2048;

    public LayoutPreviewResult generate(int canvasWidth, int canvasHeight, List<LayoutPreviewPixel> pixels) {
        if (canvasWidth <= 0 || canvasHeight <= 0) throw new IllegalArgumentException("预览画布尺寸无效");
        if (pixels == null || pixels.isEmpty()) throw new IllegalArgumentException("布局版本没有可预览位置");
        double scale = Math.min(1d, MAX_EDGE / (double) Math.max(canvasWidth, canvasHeight));
        int width = Math.max(1, (int) Math.round(canvasWidth * scale));
        int height = Math.max(1, (int) Math.round(canvasHeight * scale));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLACK);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            for (LayoutPreviewPixel pixel : pixels) {
                int x = Math.max(0, (int) Math.floor(pixel.x() * scale));
                int y = Math.max(0, (int) Math.floor(pixel.y() * scale));
                int right = Math.min(width, (int) Math.ceil((pixel.x() + pixel.width()) * scale));
                int bottom = Math.min(height, (int) Math.ceil((pixel.y() + pixel.height()) * scale));
                if (right <= x || bottom <= y) continue;
                graphics.setColor(toSrgb(pixel.labL(), pixel.labA(), pixel.labB()));
                graphics.fillRect(x, y, right - x, bottom - y);
            }
        } finally {
            graphics.dispose();
        }
        return new LayoutPreviewResult(png(image), width, height, pixels.size());
    }

    static Color toSrgb(double openCvL, double openCvA, double openCvB) {
        double l = clamp(openCvL, 0, 255) * 100 / 255d;
        double a = clamp(openCvA, 0, 255) - 128;
        double b = clamp(openCvB, 0, 255) - 128;
        double fy = (l + 16) / 116;
        double fx = fy + a / 500;
        double fz = fy - b / 200;
        double x = 95.047 * inverseLab(fx) / 100;
        double y = 100.000 * inverseLab(fy) / 100;
        double z = 108.883 * inverseLab(fz) / 100;
        double red = 3.2406 * x - 1.5372 * y - 0.4986 * z;
        double green = -0.9689 * x + 1.8758 * y + 0.0415 * z;
        double blue = 0.0557 * x - 0.2040 * y + 1.0570 * z;
        return new Color(channel(red), channel(green), channel(blue));
    }

    private static double inverseLab(double value) {
        double cube = value * value * value;
        return cube > 0.008856 ? cube : (value - 16d / 116) / 7.787;
    }

    private static int channel(double value) {
        double encoded = value <= 0.0031308 ? 12.92 * value
                : 1.055 * Math.pow(Math.max(0, value), 1 / 2.4) - 0.055;
        return (int) Math.round(clamp(encoded, 0, 1) * 255);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private byte[] png(BufferedImage image) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) throw new IllegalStateException("PNG 编码器不可用");
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("无法编码布局预览", exception);
        }
    }
}
