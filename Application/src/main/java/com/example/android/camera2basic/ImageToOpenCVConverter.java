package com.example.android.camera2basic;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.media.Image;
import android.support.annotation.NonNull;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;

/**
 * Created by will on 25/04/18.
 *
 * Currently takes the orientation as landscape, e.g. is always width=1024 for 1024x768 irrespective of camera orientation.
 * See https://stackoverflow.com/questions/32927405/converting-yuv-image-to-rgb-results-in-greenish-picture
 * See http://nezarobot.blogspot.com.au/2016/03/android-surfacetexture-camera2-opencv.html
 */

public class ImageToOpenCVConverter {

    public static Bitmap convertYuv420ToBitmap(Image image) {
        Mat rgbMat = convertYuv420ToRgbMat(image);

        Bitmap bitmap = Bitmap.createBitmap(rgbMat.cols(), rgbMat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(rgbMat, bitmap);

        return bitmap;
    }

    @NonNull
    public static Mat convertYuv420ToRgbMat(Image image) {
        Mat yuvMat = convertYuv420ToYuvMat(image);

        Mat rgbMat = new Mat();
        Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV420p2RGBA);
        return rgbMat;
    }

    @NonNull
    public static Mat convertYuv420ToYuvMat(Image image) {
        Image.Plane[] planes = image.getPlanes();

        byte[] imageData = new byte[image.getWidth() * image.getHeight() * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];

        ByteBuffer buffer = planes[0].getBuffer();
        int lastIndex = buffer.remaining();
        buffer.get(imageData, 0, lastIndex);
        int pixelStride = planes[1].getPixelStride();

        for (int i = 1; i < planes.length; i++) {
            buffer = planes[i].getBuffer();
            byte[] planeData = new byte[buffer.remaining()];
            buffer.get(planeData);

            for (int j = 0; j < planeData.length; j += pixelStride) {
                imageData[lastIndex++] = planeData[j];
            }
        }

        Mat yuvMat = new Mat(image.getHeight() + image.getHeight() / 2, image.getWidth(), CvType.CV_8UC1);
        yuvMat.put(0, 0, imageData);
        return yuvMat;
    }
}