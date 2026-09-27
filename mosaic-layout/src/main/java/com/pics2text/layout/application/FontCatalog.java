package com.pics2text.layout.application;

import com.pics2text.layout.domain.FontAsset;
import java.awt.Font;
import java.awt.font.TextAttribute;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class FontCatalog {
    public static final String DEFAULT_FONT_ID = "noto-sans-sc";
    private static final String FONT_PATH = "/fonts/noto-sans-sc/NotoSansSC-wght.ttf";
    private static final Font BASE_FONT = loadFont();
    private static final FontAsset ASSET = new FontAsset(DEFAULT_FONT_ID, "Noto Sans SC",
            "google-fonts-main-2026-09-27", "SIL Open Font License 1.1",
            "https://github.com/google/fonts/tree/main/ofl/notosanssc",
            "a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da", 100, 900);

    private FontCatalog() { }

    public static List<FontAsset> assets() {
        return List.of(ASSET);
    }

    public static boolean supports(String id) {
        return DEFAULT_FONT_ID.equals(id);
    }

    public static Font font(String id, int size, int weight) {
        if (!supports(id)) throw new FontAssetException("字体资产不存在：" + id);
        float awtWeight = weight <= 100 ? 0.5f : weight >= 900 ? 2.75f
                : 0.5f + (weight - 100) * (2.25f / 800f);
        return BASE_FONT.deriveFont(Map.of(
                TextAttribute.SIZE, (float) size,
                TextAttribute.WEIGHT, awtWeight,
                TextAttribute.KERNING, TextAttribute.KERNING_ON));
    }

    private static Font loadFont() {
        try (var input = FontCatalog.class.getResourceAsStream(FONT_PATH)) {
            if (input == null) throw new FontAssetException("内置字体文件缺失：" + FONT_PATH);
            return Font.createFont(Font.TRUETYPE_FONT, input);
        } catch (IOException | java.awt.FontFormatException exception) {
            throw new FontAssetException("无法加载内置字体：" + FONT_PATH, exception);
        }
    }

    public static final class FontAssetException extends RuntimeException {
        public FontAssetException(String message) { super(message); }
        public FontAssetException(String message, Throwable cause) { super(message, cause); }
    }
}
