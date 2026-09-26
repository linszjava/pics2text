package com.pics2text.worker.analysis;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.ExifIFD0Directory;
import java.io.ByteArrayInputStream;
import org.opencv.core.Core;
import org.opencv.core.Mat;

final class ImageOrientation {
    private ImageOrientation() { }

    static int read(byte[] bytes) {
        try {
            var metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
            var directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            return directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)
                    ? directory.getInt(ExifIFD0Directory.TAG_ORIENTATION) : 1;
        } catch (Exception ignored) {
            return 1;
        }
    }

    static Mat apply(Mat source, int orientation) {
        if (orientation < 2 || orientation > 8) return source.clone();
        Mat result = new Mat();
        switch (orientation) {
            case 2 -> Core.flip(source, result, 1);
            case 3 -> Core.rotate(source, result, Core.ROTATE_180);
            case 4 -> Core.flip(source, result, 0);
            case 5 -> {
                Core.transpose(source, result);
                Core.flip(result, result, 1);
            }
            case 6 -> Core.rotate(source, result, Core.ROTATE_90_CLOCKWISE);
            case 7 -> {
                Core.transpose(source, result);
                Core.flip(result, result, 0);
            }
            case 8 -> Core.rotate(source, result, Core.ROTATE_90_COUNTERCLOCKWISE);
            default -> source.copyTo(result);
        }
        return result;
    }
}
