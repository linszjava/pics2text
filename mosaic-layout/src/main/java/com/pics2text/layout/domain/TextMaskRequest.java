package com.pics2text.layout.domain;

public record TextMaskRequest(String text, TextDirection direction, String fontId,
        int fontSize, int fontWeight, double letterSpacing, double lineSpacing,
        int canvasWidth, int canvasHeight, int padding,
        GridStrategy gridStrategy, EdgePolicy edgePolicy,
        int cellWidth, int cellHeight, int gap, double coverageThreshold) {

    public TextMaskRequest {
        if (text == null || text.isBlank() || text.codePointCount(0, text.length()) > 200) {
            throw new IllegalArgumentException("文字不能为空且不能超过200个字符");
        }
        if (direction == null || gridStrategy == null || edgePolicy == null) {
            throw new IllegalArgumentException("文字方向、网格策略和边缘策略不能为空");
        }
        if (fontId == null || fontId.isBlank()) throw new IllegalArgumentException("字体不能为空");
        if (fontSize < 16 || fontSize > 2048) throw new IllegalArgumentException("字号必须在16到2048之间");
        if (fontWeight < 100 || fontWeight > 900) throw new IllegalArgumentException("字重必须在100到900之间");
        if (letterSpacing < -20 || letterSpacing > 200) throw new IllegalArgumentException("字距必须在-20到200之间");
        if (lineSpacing < 0.5 || lineSpacing > 3) throw new IllegalArgumentException("行距倍率必须在0.5到3之间");
        if (fontSize * lineSpacing + letterSpacing < 1) {
            throw new IllegalArgumentException("字号、字距和行距组合会导致字符推进距离无效");
        }
        if (canvasWidth < 256 || canvasWidth > 4096 || canvasHeight < 256 || canvasHeight > 4096) {
            throw new IllegalArgumentException("画布宽高必须在256到4096之间");
        }
        if (padding < 0 || padding > 2048 || 2L * padding >= Math.min(canvasWidth, canvasHeight)) {
            throw new IllegalArgumentException("画布留白无效");
        }
        if (cellWidth < 4 || cellWidth > 512 || cellHeight < 4 || cellHeight > 512) {
            throw new IllegalArgumentException("网格单元宽高必须在4到512之间");
        }
        if (gap < 0 || gap > 128) throw new IllegalArgumentException("网格间距必须在0到128之间");
        if (coverageThreshold < 0.5 || coverageThreshold > 0.98) {
            throw new IllegalArgumentException("覆盖率阈值必须在0.5到0.98之间");
        }
        long columns = Math.max(0, canvasWidth - 2L * padding) / (cellWidth + gap);
        long rows = Math.max(0, canvasHeight - 2L * padding) / (cellHeight + gap);
        if (columns * rows > 250_000) {
            throw new IllegalArgumentException("网格候选数量超过250000个，请增大单元或间距");
        }
    }
}
