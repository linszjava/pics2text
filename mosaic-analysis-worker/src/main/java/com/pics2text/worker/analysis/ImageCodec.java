package com.pics2text.worker.analysis;

import java.util.ArrayList;
import java.util.List;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

final class ImageCodec {
    private ImageCodec() { }

    static Mat decode(byte[] bytes, long maxPixels) {
        MatOfByte buffer = new MatOfByte(bytes);
        try {
            Mat decoded = Imgcodecs.imdecode(buffer, Imgcodecs.IMREAD_COLOR);
            if (decoded.empty()) {
                decoded.release();
                throw new UnsafeImageException("图片无法解码或格式已损坏");
            }
            long pixels = Math.multiplyExact((long) decoded.cols(), decoded.rows());
            if (pixels > maxPixels) {
                decoded.release();
                throw new UnsafeImageException("图片像素数量超出分析限制：" + pixels);
            }
            if (decoded.type() != CvType.CV_8UC3) {
                Mat converted = new Mat();
                decoded.convertTo(converted, CvType.CV_8UC3);
                decoded.release();
                return converted;
            }
            return decoded;
        } catch (ArithmeticException exception) {
            throw new UnsafeImageException("图片尺寸非法");
        } finally {
            buffer.release();
        }
    }

    static EncodedImage encodeJpeg(Mat image, int quality) {
        return encode(image, ".jpg", new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, quality), "image/jpeg");
    }

    static EncodedImage resizeWebp(Mat source, int maxEdge, int quality) {
        double scale = Math.min(1d, maxEdge / (double) Math.max(source.cols(), source.rows()));
        int width = Math.max(1, (int) Math.round(source.cols() * scale));
        int height = Math.max(1, (int) Math.round(source.rows() * scale));
        Mat resized = new Mat();
        try {
            Imgproc.resize(source, resized, new Size(width, height), 0, 0,
                    scale < 1 ? Imgproc.INTER_AREA : Imgproc.INTER_LINEAR);
            return encode(resized, ".webp", new MatOfInt(Imgcodecs.IMWRITE_WEBP_QUALITY, quality), "image/webp");
        } finally {
            resized.release();
        }
    }

    private static EncodedImage encode(Mat image, String extension, MatOfInt parameters, String contentType) {
        MatOfByte output = new MatOfByte();
        try {
            if (!Imgcodecs.imencode(extension, image, output, parameters)) {
                throw new IllegalStateException("OpenCV 无法编码 " + extension + " 图片");
            }
            return new EncodedImage(output.toArray(), image.cols(), image.rows(), contentType);
        } finally {
            output.release();
            parameters.release();
        }
    }

    record EncodedImage(byte[] bytes, int width, int height, String contentType) { }
}
