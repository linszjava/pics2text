package com.pics2text.worker.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nu.pattern.OpenCV;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

class PhotoFeatureExtractorTest {
    private final PhotoFeatureExtractor extractor = new PhotoFeatureExtractor("");

    @BeforeAll
    static void loadNativeLibrary() {
        OpenCV.loadLocally();
    }

    @Test
    void sharpnessMetricSeparatesDetailedAndBlurredImages() {
        Mat detailed = checkerboard(640, 480);
        Mat blurred = new Mat();
        try {
            Imgproc.GaussianBlur(detailed, blurred, new org.opencv.core.Size(31, 31), 0);
            double sharp = extractor.extract(detailed).sharpness();
            double soft = extractor.extract(blurred).sharpness();
            assertThat(sharp).isGreaterThan(soft * 10);
        } finally {
            detailed.release();
            blurred.release();
        }
    }

    @Test
    void perceptualHashesRemainCloseAfterJpegCompression() {
        Mat source = sampleScene(640, 480);
        Mat compressed = null;
        try {
            byte[] jpeg = ImageCodec.encodeJpeg(source, 55).bytes();
            compressed = ImageCodec.decode(jpeg, 1_000_000);
            var original = extractor.extract(source);
            var recompressed = extractor.extract(compressed);
            assertThat(PhotoFeatureExtractor.hammingDistance(original.dHash(), recompressed.dHash()))
                    .isLessThanOrEqualTo(8);
            assertThat(PhotoFeatureExtractor.hammingDistance(original.pHash(), recompressed.pHash()))
                    .isLessThanOrEqualTo(12);
        } finally {
            source.release();
            if (compressed != null) compressed.release();
        }
    }

    @Test
    void recommendedCropContainsDetectedSubject() {
        Mat scene = Mat.zeros(600, 900, CvType.CV_8UC3);
        try {
            Imgproc.rectangle(scene, new Point(330, 160), new Point(570, 440),
                    new Scalar(255, 255, 255), 8);
            var features = extractor.extract(scene);
            assertThat(features.crop().contains(features.subject())).isTrue();
            assertThat(features.crop().width()).isCloseTo(features.crop().height() * 600d / 900d,
                    org.assertj.core.data.Offset.offset(0.02));
        } finally {
            scene.release();
        }
    }

    @Test
    void decoderRejectsDamagedAndOversizedImages() {
        assertThatThrownBy(() -> ImageCodec.decode(new byte[] {1, 2, 3, 4}, 1_000_000))
                .isInstanceOf(UnsafeImageException.class);
        Mat image = sampleScene(100, 100);
        try {
            byte[] jpeg = ImageCodec.encodeJpeg(image, 90).bytes();
            assertThatThrownBy(() -> ImageCodec.decode(jpeg, 9_999))
                    .isInstanceOf(UnsafeImageException.class);
        } finally {
            image.release();
        }
    }

    private static Mat checkerboard(int width, int height) {
        Mat image = new Mat(height, width, CvType.CV_8UC3, new Scalar(0, 0, 0));
        int cell = 16;
        for (int y = 0; y < height; y += cell) for (int x = 0; x < width; x += cell) {
            if (((x / cell) + (y / cell)) % 2 == 0) {
                Imgproc.rectangle(image, new Point(x, y),
                        new Point(Math.min(width - 1, x + cell), Math.min(height - 1, y + cell)),
                        new Scalar(255, 255, 255), -1);
            }
        }
        return image;
    }

    private static Mat sampleScene(int width, int height) {
        Mat image = new Mat(height, width, CvType.CV_8UC3, new Scalar(35, 80, 140));
        Imgproc.circle(image, new Point(width * 0.35, height * 0.45), height / 5,
                new Scalar(210, 180, 50), -1);
        Imgproc.rectangle(image, new Point(width * 0.55, height * 0.2),
                new Point(width * 0.85, height * 0.8), new Scalar(40, 220, 100), -1);
        Imgproc.putText(image, "PICS2TEXT", new Point(width * 0.1, height * 0.92),
                Imgproc.FONT_HERSHEY_SIMPLEX, 1.2, new Scalar(255, 255, 255), 2);
        return image;
    }
}
