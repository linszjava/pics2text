package com.pics2text.layout.application;

import com.pics2text.layout.domain.EdgePolicy;
import com.pics2text.layout.domain.GridStrategy;
import com.pics2text.layout.domain.TextDirection;
import com.pics2text.layout.domain.TextMaskRequest;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public final class TextMaskGenerator {
    public static final String ALGORITHM_VERSION = "java2d-mask-v1";

    public TextMaskResult generate(TextMaskRequest request) {
        Font font = FontCatalog.font(request.fontId(), request.fontSize(), request.fontWeight());
        String displayText = request.text().replace("\r", "").replace("\n", "");
        int unsupported = font.canDisplayUpTo(displayText);
        if (unsupported >= 0) {
            int codePoint = displayText.codePointAt(unsupported);
            throw new FontCatalog.FontAssetException(
                    "字体不包含字符 U+" + Integer.toHexString(codePoint).toUpperCase());
        }
        BufferedImage mask = new BufferedImage(request.canvasWidth(), request.canvasHeight(),
                BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = mask.createGraphics();
        List<String> warnings = new ArrayList<>();
        try {
            configure(graphics);
            graphics.setColor(Color.BLACK);
            graphics.fillRect(0, 0, mask.getWidth(), mask.getHeight());
            graphics.setColor(Color.WHITE);
            if (request.direction() == TextDirection.HORIZONTAL) {
                drawHorizontal(graphics, request, font, warnings);
            } else {
                drawVertical(graphics, request, font, warnings);
            }
        } finally {
            graphics.dispose();
        }

        Raster raster = mask.getRaster();
        Bounds bounds = bounds(raster);
        if (bounds.width == 0 || bounds.height == 0) {
            throw new IllegalArgumentException("文字没有生成可见像素，请检查字号、画布和内容");
        }
        if (bounds.x <= request.padding() || bounds.y <= request.padding()
                || bounds.x + bounds.width >= request.canvasWidth() - request.padding()
                || bounds.y + bounds.height >= request.canvasHeight() - request.padding()) {
            warnings.add("文字接近画布安全边界，可能发生裁切");
        }
        double strokeWidth = estimateStrokeWidth(raster, bounds);
        if (strokeWidth < Math.max(2, Math.min(request.cellWidth(), request.cellHeight()) * 0.28)) {
            warnings.add("相对于网格单元，文字笔画可能过细");
        }
        List<TextMaskSlot> slots = buildSlots(raster, request);
        if (slots.isEmpty()) warnings.add("当前网格参数没有产生可用照片位置");
        if (slots.size() < Math.max(4, request.text().codePointCount(0, request.text().length()) * 2)) {
            warnings.add("可用照片位置较少，缩小预览的文字辨识度可能不足");
        }
        double averageCoverage = slots.stream().mapToDouble(TextMaskSlot::coverage).average().orElse(0);
        return new TextMaskResult(png(mask), png(debugImage(mask, slots)),
                bounds.x, bounds.y, bounds.width, bounds.height, strokeWidth,
                averageCoverage, List.copyOf(warnings), List.copyOf(slots));
    }

    private void drawHorizontal(Graphics2D graphics, TextMaskRequest request, Font font,
            List<String> warnings) {
        String[] lines = request.text().split("\\R", -1);
        FontRenderContext context = graphics.getFontRenderContext();
        double lineHeight = font.getLineMetrics("国Ag", context).getHeight() * request.lineSpacing();
        double blockHeight = Math.max(lineHeight, lines.length * lineHeight);
        double firstBaseline = (request.canvasHeight() - blockHeight) / 2
                + font.getLineMetrics("国Ag", context).getAscent();
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            if (line.isEmpty()) continue;
            AttributedString attributed = new AttributedString(line);
            attributed.addAttribute(TextAttribute.FONT, font);
            attributed.addAttribute(TextAttribute.TRACKING,
                    (float) (request.letterSpacing() / Math.max(1, request.fontSize())));
            TextLayout layout = new TextLayout(attributed.getIterator(), context);
            float x = (float) ((request.canvasWidth() - layout.getAdvance()) / 2);
            float baseline = (float) (firstBaseline + index * lineHeight);
            layout.draw(graphics, x, baseline);
            if (x < request.padding() || x + layout.getAdvance() > request.canvasWidth() - request.padding()) {
                warnings.add("部分横排文字超出安全宽度");
            }
        }
    }

    private void drawVertical(Graphics2D graphics, TextMaskRequest request, Font font,
            List<String> warnings) {
        String[] columns = request.text().split("\\R", -1);
        FontRenderContext context = graphics.getFontRenderContext();
        double advanceY = request.fontSize() * request.lineSpacing() + request.letterSpacing();
        double columnWidth = request.fontSize() * request.lineSpacing();
        double totalWidth = columns.length * columnWidth;
        for (int column = 0; column < columns.length; column++) {
            int[] codePoints = columns[column].codePoints().toArray();
            double totalHeight = codePoints.length == 0 ? 0
                    : request.fontSize() + Math.max(0, codePoints.length - 1) * advanceY;
            double centerX = (request.canvasWidth() + totalWidth) / 2
                    - (column + 0.5) * columnWidth;
            double top = (request.canvasHeight() - totalHeight) / 2;
            for (int row = 0; row < codePoints.length; row++) {
                String character = new String(Character.toChars(codePoints[row]));
                GlyphVector glyph = font.createGlyphVector(context, character);
                Rectangle2D bounds = glyph.getVisualBounds();
                double centerY = top + request.fontSize() / 2d + row * advanceY;
                float x = (float) (centerX - bounds.getCenterX());
                float y = (float) (centerY - bounds.getCenterY());
                graphics.drawGlyphVector(glyph, x, y);
            }
            if (top < request.padding() || top + totalHeight > request.canvasHeight() - request.padding()) {
                warnings.add("部分竖排文字超出安全高度");
            }
        }
    }

    private List<TextMaskSlot> buildSlots(Raster raster, TextMaskRequest request) {
        List<TextMaskSlot> slots = new ArrayList<>();
        int stepX = request.cellWidth() + request.gap();
        int stepY = request.cellHeight() + request.gap();
        int row = 0;
        for (int y = request.padding(); y + request.cellHeight() <= request.canvasHeight() - request.padding();
                y += stepY, row++) {
            int offset = request.gridStrategy() == GridStrategy.STAGGERED && row % 2 == 1 ? stepX / 2 : 0;
            int column = 0;
            for (int x = request.padding() + offset;
                    x + request.cellWidth() <= request.canvasWidth() - request.padding();
                    x += stepX, column++) {
                double coverage = coverage(raster, x, y, request.cellWidth(), request.cellHeight());
                double requiredCoverage = request.edgePolicy() == EdgePolicy.STRICT
                        ? Math.max(0.75, request.coverageThreshold()) : request.coverageThreshold();
                boolean accepted = coverage >= requiredCoverage;
                if (accepted) {
                    slots.add(new TextMaskSlot(slots.size(), row, column, x, y,
                            request.cellWidth(), request.cellHeight(), coverage, coverage < 0.98));
                }
            }
        }
        return slots;
    }

    private double coverage(Raster raster, int x, int y, int width, int height) {
        long sum = 0;
        for (int py = y; py < y + height; py++) for (int px = x; px < x + width; px++) {
            sum += raster.getSample(px, py, 0);
        }
        return sum / (255d * width * height);
    }

    private Bounds bounds(Raster raster) {
        int minX = raster.getWidth();
        int minY = raster.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < raster.getHeight(); y++) for (int x = 0; x < raster.getWidth(); x++) {
            if (raster.getSample(x, y, 0) > 0) {
                minX = Math.min(minX, x); minY = Math.min(minY, y);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
            }
        }
        return maxX < minX ? new Bounds(0, 0, 0, 0)
                : new Bounds(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private double estimateStrokeWidth(Raster raster, Bounds bounds) {
        int width = bounds.width;
        int height = bounds.height;
        int[] distance = new int[width * height];
        int infinity = width + height + 1;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            boolean foreground = raster.getSample(bounds.x + x, bounds.y + y, 0) >= 128;
            distance[y * width + x] = foreground ? infinity : 0;
        }
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int index = y * width + x;
            if (distance[index] == 0) continue;
            if (x == 0 || y == 0) distance[index] = 1;
            if (x > 0) distance[index] = Math.min(distance[index], distance[index - 1] + 1);
            if (y > 0) distance[index] = Math.min(distance[index], distance[index - width] + 1);
        }
        int maximum = 0;
        for (int y = height - 1; y >= 0; y--) for (int x = width - 1; x >= 0; x--) {
            int index = y * width + x;
            if (distance[index] == 0) continue;
            if (x == width - 1 || y == height - 1) distance[index] = Math.min(distance[index], 1);
            if (x + 1 < width) distance[index] = Math.min(distance[index], distance[index + 1] + 1);
            if (y + 1 < height) distance[index] = Math.min(distance[index], distance[index + width] + 1);
            maximum = Math.max(maximum, distance[index]);
        }
        return maximum * 2d;
    }

    private BufferedImage debugImage(BufferedImage mask, List<TextMaskSlot> slots) {
        BufferedImage debug = new BufferedImage(mask.getWidth(), mask.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = debug.createGraphics();
        try {
            graphics.drawImage(mask, 0, 0, null);
            graphics.setStroke(new BasicStroke(Math.max(1, Math.min(mask.getWidth(), mask.getHeight()) / 800f)));
            for (TextMaskSlot slot : slots) {
                graphics.setColor(slot.edge() ? new Color(255, 160, 0) : new Color(0, 220, 100));
                graphics.drawRect(slot.x(), slot.y(), slot.width() - 1, slot.height() - 1);
            }
        } finally {
            graphics.dispose();
        }
        return debug;
    }

    private byte[] png(BufferedImage image) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) throw new IllegalStateException("PNG 编码器不可用");
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("无法编码文字蒙版", exception);
        }
    }

    private void configure(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private record Bounds(int x, int y, int width, int height) { }
}
