package com.pics2text.layout.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pics2text.layout.domain.EdgePolicy;
import com.pics2text.layout.domain.GridStrategy;
import com.pics2text.layout.domain.TextDirection;
import com.pics2text.layout.domain.TextMaskRequest;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class TextMaskGeneratorTest {
    private final TextMaskGenerator generator = new TextMaskGenerator();

    @Test
    void sameConfigurationProducesIdenticalMaskAndSlots() {
        TextMaskRequest request = request(TextDirection.HORIZONTAL, GridStrategy.REGULAR, EdgePolicy.STRICT);
        TextMaskResult first = generator.generate(request);
        TextMaskResult second = generator.generate(request);
        assertThat(first.maskPng()).isEqualTo(second.maskPng());
        assertThat(first.slots()).isEqualTo(second.slots());
        assertThat(first.boundsWidth()).isGreaterThan(500);
        assertThat(first.slots()).isNotEmpty();
    }

    @Test
    void strictGridSlotsStayInsideCanvasAndMeetCoverageThreshold() {
        TextMaskRequest request = request(TextDirection.VERTICAL, GridStrategy.STAGGERED, EdgePolicy.STRICT);
        TextMaskResult result = generator.generate(request);
        assertThat(result.slots()).allSatisfy(slot -> {
            assertThat(slot.x()).isGreaterThanOrEqualTo(request.padding());
            assertThat(slot.y()).isGreaterThanOrEqualTo(request.padding());
            assertThat(slot.x() + slot.width()).isLessThanOrEqualTo(request.canvasWidth() - request.padding());
            assertThat(slot.y() + slot.height()).isLessThanOrEqualTo(request.canvasHeight() - request.padding());
            assertThat(slot.coverage()).isGreaterThanOrEqualTo(request.coverageThreshold());
        });
    }

    @Test
    void downscaledPreviewKeepsVisibleTextPixels() throws Exception {
        TextMaskResult result = generator.generate(request(
                TextDirection.HORIZONTAL, GridStrategy.STAGGERED, EdgePolicy.INCLUDE_PARTIAL));
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(result.maskPng()));
        BufferedImage preview = new BufferedImage(160, 100, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = preview.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, preview.getWidth(), preview.getHeight(), null);
        } finally {
            graphics.dispose();
        }
        long visible = 0;
        for (int y = 0; y < preview.getHeight(); y++) for (int x = 0; x < preview.getWidth(); x++) {
            if (preview.getRaster().getSample(x, y, 0) >= 64) visible++;
        }
        double ratio = visible / (double) (preview.getWidth() * preview.getHeight());
        assertThat(ratio).isBetween(0.05, 0.55);
    }

    @Test
    void missingFontFailsWithExplicitMessage() {
        TextMaskRequest invalid = new TextMaskRequest("中国人能飞", TextDirection.HORIZONTAL,
                "missing-font", 180, 700, 4, 1.2, 1200, 600, 40,
                GridStrategy.REGULAR, EdgePolicy.STRICT, 18, 18, 3, 0.55);
        assertThatThrownBy(() -> generator.generate(invalid))
                .isInstanceOf(FontCatalog.FontAssetException.class)
                .hasMessageContaining("字体资产不存在");
    }

    @Test
    void configuredWeightChangesRasterizedStroke() {
        TextMaskRequest light = new TextMaskRequest("中国人能飞", TextDirection.HORIZONTAL,
                FontCatalog.DEFAULT_FONT_ID, 180, 100, 4, 1.2, 1200, 600, 40,
                GridStrategy.REGULAR, EdgePolicy.INCLUDE_PARTIAL, 18, 18, 3, 0.55);
        TextMaskRequest heavy = new TextMaskRequest("中国人能飞", TextDirection.HORIZONTAL,
                FontCatalog.DEFAULT_FONT_ID, 180, 900, 4, 1.2, 1200, 600, 40,
                GridStrategy.REGULAR, EdgePolicy.INCLUDE_PARTIAL, 18, 18, 3, 0.55);
        TextMaskResult lightResult = generator.generate(light);
        TextMaskResult heavyResult = generator.generate(heavy);
        assertThat(heavyResult.maskPng()).isNotEqualTo(lightResult.maskPng());
        assertThat(heavyResult.estimatedStrokeWidth()).isGreaterThan(lightResult.estimatedStrokeWidth());
    }

    @Test
    void excessiveGridCandidateCountIsRejectedBeforeRendering() {
        assertThatThrownBy(() -> new TextMaskRequest("中国人能飞", TextDirection.HORIZONTAL,
                FontCatalog.DEFAULT_FONT_ID, 180, 400, 4, 1.2, 4096, 4096, 0,
                GridStrategy.REGULAR, EdgePolicy.STRICT, 4, 4, 0, 0.75))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("网格候选数量");
    }

    private TextMaskRequest request(TextDirection direction, GridStrategy strategy, EdgePolicy edgePolicy) {
        return new TextMaskRequest("中国人能飞", direction, FontCatalog.DEFAULT_FONT_ID,
                180, 700, 4, 1.2, 1200, 600, 40,
                strategy, edgePolicy, 18, 18, 3, 0.55);
    }
}
