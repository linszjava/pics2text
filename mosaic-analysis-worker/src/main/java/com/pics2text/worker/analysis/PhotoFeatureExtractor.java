package com.pics2text.worker.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfRect;
import org.opencv.core.MatOfDouble;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

final class PhotoFeatureExtractor {
    private final CascadeClassifier faceClassifier;

    PhotoFeatureExtractor(String faceCascadePath) {
        if (faceCascadePath == null || faceCascadePath.isBlank()) {
            faceClassifier = null;
        } else {
            CascadeClassifier classifier = new CascadeClassifier(faceCascadePath);
            if (classifier.empty()) throw new IllegalArgumentException("无法加载人脸级联模型：" + faceCascadePath);
            faceClassifier = classifier;
        }
    }

    Features extract(Mat image) {
        Mat gray = new Mat();
        Mat laplacian = new Mat();
        Mat lab = new Mat();
        try {
            Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.Laplacian(gray, laplacian, CvType.CV_64F);
            var lapStats = meanStd(laplacian);
            var grayStats = meanStd(gray);
            Imgproc.cvtColor(image, lab, Imgproc.COLOR_BGR2Lab);
            Scalar meanLab = Core.mean(lab);
            List<Box> faces = detectFaces(gray);
            Box subject = faces.isEmpty() ? detectSubject(gray) : union(faces);
            Box cropTarget = faces.isEmpty() ? subject : faces.getFirst().expand(0.08);
            Box crop = recommendedSquareCrop(cropTarget, image.cols(), image.rows());
            return new Features(lapStats.stddev() * lapStats.stddev(), grayStats.mean(), grayStats.stddev(),
                    meanLab.val[0], meanLab.val[1], meanLab.val[2], dHash(gray), pHash(gray),
                    List.copyOf(faces), subject, crop);
        } finally {
            gray.release();
            laplacian.release();
            lab.release();
        }
    }

    static int hammingDistance(String first, String second) {
        return Long.bitCount(Long.parseUnsignedLong(first, 16) ^ Long.parseUnsignedLong(second, 16));
    }

    private List<Box> detectFaces(Mat gray) {
        if (faceClassifier == null) return List.of();
        MatOfRect found = new MatOfRect();
        try {
            faceClassifier.detectMultiScale(gray, found, 1.1, 4, 0,
                    new Size(Math.max(24, gray.cols() / 20d), Math.max(24, gray.rows() / 20d)), new Size());
            return Arrays.stream(found.toArray())
                    .map(rect -> Box.from(rect, gray.cols(), gray.rows()))
                    .sorted(Comparator.comparingDouble(Box::area).reversed()).toList();
        } finally {
            found.release();
        }
    }

    private Box detectSubject(Mat gray) {
        Mat reduced = new Mat();
        Mat blurred = new Mat();
        Mat edges = new Mat();
        Mat points = new Mat();
        try {
            double scale = Math.min(1d, 512d / Math.max(gray.cols(), gray.rows()));
            Imgproc.resize(gray, reduced, new Size(Math.max(1, Math.round(gray.cols() * scale)),
                    Math.max(1, Math.round(gray.rows() * scale))), 0, 0, Imgproc.INTER_AREA);
            Imgproc.GaussianBlur(reduced, blurred, new Size(5, 5), 0);
            Imgproc.Canny(blurred, edges, 50, 150);
            Core.findNonZero(edges, points);
            if (points.empty()) return new Box(0.2, 0.2, 0.6, 0.6);
            Rect bounds = Imgproc.boundingRect(new MatOfPoint(points));
            Box raw = Box.from(bounds, reduced.cols(), reduced.rows());
            if (raw.area() > 0.92 || raw.area() < 0.01) return new Box(0.15, 0.15, 0.7, 0.7);
            return raw.expand(0.08);
        } finally {
            reduced.release();
            blurred.release();
            edges.release();
            points.release();
        }
    }

    private String dHash(Mat gray) {
        Mat tiny = new Mat();
        try {
            Imgproc.resize(gray, tiny, new Size(9, 8), 0, 0, Imgproc.INTER_AREA);
            long bits = 0;
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    bits = (bits << 1) | (tiny.get(y, x)[0] > tiny.get(y, x + 1)[0] ? 1 : 0);
                }
            }
            return String.format("%016x", bits);
        } finally {
            tiny.release();
        }
    }

    private String pHash(Mat gray) {
        Mat tiny = new Mat();
        Mat floating = new Mat();
        Mat dct = new Mat();
        try {
            Imgproc.resize(gray, tiny, new Size(32, 32), 0, 0, Imgproc.INTER_AREA);
            tiny.convertTo(floating, CvType.CV_32F);
            Core.dct(floating, dct);
            double[] coefficients = new double[63];
            int index = 0;
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                if (x != 0 || y != 0) coefficients[index++] = dct.get(y, x)[0];
            }
            double[] sorted = coefficients.clone();
            Arrays.sort(sorted);
            double median = sorted[sorted.length / 2];
            long bits = 0;
            index = 0;
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                double value = x == 0 && y == 0 ? median : coefficients[index++];
                bits = (bits << 1) | (value > median ? 1 : 0);
            }
            return String.format("%016x", bits);
        } finally {
            tiny.release();
            floating.release();
            dct.release();
        }
    }

    private Stats meanStd(Mat matrix) {
        MatOfDouble mean = new MatOfDouble();
        MatOfDouble stddev = new MatOfDouble();
        try {
            Core.meanStdDev(matrix, mean, stddev);
            return new Stats(mean.get(0, 0)[0], stddev.get(0, 0)[0]);
        } finally {
            mean.release();
            stddev.release();
        }
    }

    private Box union(List<Box> boxes) {
        double left = boxes.stream().mapToDouble(Box::x).min().orElse(0.2);
        double top = boxes.stream().mapToDouble(Box::y).min().orElse(0.2);
        double right = boxes.stream().mapToDouble(box -> box.x + box.width).max().orElse(0.8);
        double bottom = boxes.stream().mapToDouble(box -> box.y + box.height).max().orElse(0.8);
        return new Box(left, top, right - left, bottom - top).expand(0.08);
    }

    private Box recommendedSquareCrop(Box target, int imageWidth, int imageHeight) {
        double targetPixelsWidth = target.width * imageWidth;
        double targetPixelsHeight = target.height * imageHeight;
        double side = Math.min(Math.max(targetPixelsWidth, targetPixelsHeight) * 1.35,
                Math.min(imageWidth, imageHeight));
        side = Math.max(side, Math.min(imageWidth, imageHeight) * 0.35);
        double centerX = (target.x + target.width / 2) * imageWidth;
        double centerY = (target.y + target.height / 2) * imageHeight;
        double left = Math.max(0, Math.min(centerX - side / 2, imageWidth - side));
        double top = Math.max(0, Math.min(centerY - side / 2, imageHeight - side));
        return new Box(left / imageWidth, top / imageHeight, side / imageWidth, side / imageHeight);
    }

    record Features(double sharpness, double brightness, double contrast,
            double labL, double labA, double labB, String dHash, String pHash,
            List<Box> faces, Box subject, Box crop) { }
    record Stats(double mean, double stddev) { }
    record Box(double x, double y, double width, double height) {
        static Box from(Rect rect, int width, int height) {
            return new Box(rect.x / (double) width, rect.y / (double) height,
                    rect.width / (double) width, rect.height / (double) height);
        }
        Box expand(double amount) {
            double left = Math.max(0, x - amount);
            double top = Math.max(0, y - amount);
            double right = Math.min(1, x + width + amount);
            double bottom = Math.min(1, y + height + amount);
            return new Box(left, top, right - left, bottom - top);
        }
        double area() { return width * height; }
        boolean contains(Box other) {
            double epsilon = 1e-6;
            return x <= other.x + epsilon && y <= other.y + epsilon
                    && x + width + epsilon >= other.x + other.width
                    && y + height + epsilon >= other.y + other.height;
        }
    }
}
