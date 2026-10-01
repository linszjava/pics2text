package com.pics2text.rendering.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class LayoutColorPreviewGeneratorTest {
    private final LayoutColorPreviewGenerator generator = new LayoutColorPreviewGenerator();

    @Test
    void previewIsBoundedAndDeterministic() throws Exception {
        List<LayoutPreviewPixel> pixels = List.of(
                new LayoutPreviewPixel(0, 100, 100, 400, 300, 180, 128, 128),
                new LayoutPreviewPixel(1, 600, 100, 400, 300, 120, 180, 90));
        LayoutPreviewResult first = generator.generate(8000, 4000, pixels);
        LayoutPreviewResult second = generator.generate(8000, 4000, pixels);
        assertThat(first.width()).isEqualTo(2048);
        assertThat(first.height()).isEqualTo(1024);
        assertThat(first.png()).isEqualTo(second.png());
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(first.png()));
        assertThat(image.getWidth()).isEqualTo(2048);
        assertThat(image.getRGB(30, 30)).isNotEqualTo(0xff000000);
    }

    @Test
    void neutralLabProducesNeutralRgb() {
        var color = LayoutColorPreviewGenerator.toSrgb(128, 128, 128);
        assertThat(Math.abs(color.getRed() - color.getGreen())).isLessThanOrEqualTo(2);
        assertThat(Math.abs(color.getGreen() - color.getBlue())).isLessThanOrEqualTo(2);
    }
}
