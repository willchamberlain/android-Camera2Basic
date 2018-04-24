package com.example.android.camera2basic;

import android.media.Image;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.Random;

import boofcv.abst.fiducial.FiducialDetector;
import boofcv.alg.distort.LensDistortionNarrowFOV;
import boofcv.alg.distort.pinhole.LensDistortionPinhole;
import boofcv.core.encoding.ConvertNV21;
import boofcv.factory.fiducial.ConfigFiducialBinary;
import boofcv.factory.fiducial.FactoryFiducial;
import boofcv.factory.filter.binary.ConfigThreshold;
import boofcv.factory.filter.binary.ThresholdType;
import boofcv.struct.calib.CameraPinhole;
import boofcv.struct.image.GrayF32;

/**
 * Saves a JPEG {@link Image} into the specified {@link File}.
 */
class ImageSaver implements Runnable {
    byte[] luminanceBytes;
    int imageWidth;
    int imageHeight;

    ImageSaver(Image image /*, File file*/) {
            Log.i("ImageSaver","ImageSaver(Image image)");
        long startTime = Calendar.getInstance().getTimeInMillis();
            Log.i("ImageSaver","ImageSaver(Image image): start = "+startTime);
        Log.i("ImageSaver","ImageSaver(Image image): before image.getPlanes()[0].getBuffer() :timeElapsed:  "+ AppMonitor.timeElapsed(startTime)+"ms");
        ByteBuffer luminanceBuffer = /*mImage*/image.getPlanes()[0].getBuffer();
            Log.i("ImageSaver","ImageSaver(Image image): after image.getPlanes()[0].getBuffer() :timeElapsed:  "+ AppMonitor.timeElapsed(startTime)+"ms");
        /*byte[]*/ luminanceBytes = new byte[luminanceBuffer.remaining()];  // buffer size: current position is zero, remaining() gives "the number of elements between the current position and the limit"
            Log.i("ImageSaver","ImageSaver(Image image): after luminanceBytes = new byte[luminanceBuffer.remaining()] :timeElapsed: "+ AppMonitor.timeElapsed(startTime)+"ms");
            Log.i("ImageSaver","ImageSaver(Image image): after luminanceBuffer.get(luminanceBytes) :timeElapsed:  "+ AppMonitor.timeElapsed(startTime)+"ms");
        luminanceBuffer.get(luminanceBytes);                            // copy from buffer to bytes: get() "transfers bytes from this buffer into the given destination array"
            Log.i("ImageSaver","ImageSaver(Image image): after luminanceBuffer.get(luminanceBytes) :timeElapsed:  "+ AppMonitor.timeElapsed(startTime)+"ms");
        imageWidth = image.getWidth();
        imageHeight = image.getHeight();
            Log.i("ImageSaver","ImageSaver(Image image): end after "+ AppMonitor.timeElapsed(startTime)+"ms");
    }

    @Override
    public void run() {
            Log.i("ImageSaver","run()");
        long startTime = Calendar.getInstance().getTimeInMillis();
        Log.i("ImageSaver","run(): start = "+startTime);
        try {
                Log.i("ImageSaver","run() : doing some work in the Try block");
            Random random = new Random();
            if ( 1== random.nextInt() ) {
                    Log.i("ImageSaver","run() : throwing an IOException at random while doing some work in the Try block");
                throw new IOException("No particular reason: ImageSaver.run() : throwing an IOException at random while doing some work in the Try block");
            }
            // dummy image processing code - https://boofcv.org/index.php?title=Android_support
            long algorithmStepStartTime = Calendar.getInstance().getTimeInMillis();
                Log.i("ImageSaver","run() : before new GrayF32(imageWidth,imageHeight) :timeElapsed: "+ AppMonitor.timeElapsed(algorithmStepStartTime)+"ms");
            GrayF32 grayImage = new GrayF32(imageWidth,imageHeight);
                Log.i("ImageSaver","run() : after new GrayF32(imageWidth,imageHeight) :timeElapsed: "+ AppMonitor.timeElapsed(algorithmStepStartTime)+"ms");
            // from NV21 to gray scale
            algorithmStepStartTime = Calendar.getInstance().getTimeInMillis();
                Log.i("ImageSaver","run() : before converting nv21ToGray :timeElapsed: "+ AppMonitor.timeElapsed(algorithmStepStartTime)+"ms");
            ConvertNV21.nv21ToGray(luminanceBytes,imageWidth,imageHeight, grayImage);
                Log.i("ImageSaver","run() : after converting nv21ToGray :timeElapsed: "+ AppMonitor.timeElapsed(algorithmStepStartTime)+"ms");

            // start try detecting tags in the frame
            double BOOFCV_TAG_WIDTH=0.14; // TODO - tag size is a parameter
            int imageWidthInt = grayImage.getHeight(); // new Double(matGray.size().width).intValue();
            int imageHeightInt = grayImage.getWidth(); //new Double(matGray.size().height).intValue();detect
                Log.i("ImageSaver","run() : image dimensions: "+imageWidthInt+" pixels wide, "+imageHeightInt+" pixels high");
            float imageWidthFloat =  (float)imageWidthInt; // new Double(matGray.size().width).intValue();
            float imageHeightFloat = (float)imageHeightInt; //new Double(matGray.size().height).intValue();
            float  focal_midpoint_pixels_x = imageWidthFloat/2.0f;
            float  focal_midpoint_pixels_y = imageHeightFloat/2.0f;

            double skew = 0.0;

            // TODO - 640 is now a magic number : it is the image width in pixels at the time of calibration of focal length
            // TODO - per-camera calibration using BoofCV calibration process
            float focal_length_in_pixels_x = 519.902859f * (imageWidthFloat/640.0f);  // TODO - for Samsung Galaxy S3s from /mnt/nixbig/ownCloud/project_AA1__1_1/results/2016_12_04_callibrate_in_ROS/calibrationdata_grey/ost.txt
            float focal_length_in_pixels_y = 518.952669f * (imageHeightFloat/480.0f);  // TODO - for Samsung Galaxy S3s from /mnt/nixbig/ownCloud/project_AA1__1_1/results/2016_12_04_callibrate_in_ROS/calibrationdata_grey/ost.txt


                Log.i("ImageSaver","run() : config FactoryFiducial.squareBinary");
                Log.i("ImageSaver","run() : before creating detector :timeElapsed: "+ AppMonitor.timeElapsed(algorithmStepStartTime)+"ms");
            FiducialDetector<GrayF32> detector = FactoryFiducial.squareBinary(
                    new ConfigFiducialBinary(BOOFCV_TAG_WIDTH),
                    ConfigThreshold.local(ThresholdType.LOCAL_SQUARE, 10),          // TODO - evaluate parameter - ?'radius'?
                    GrayF32.class);  // tag size,  type,  ?'radius'?
            //        detector.setLensDistortion(lensDistortion);
                Log.i("ImageSaver","run() : after creating detector :timeElapsed: "+ AppMonitor.timeElapsed(algorithmStepStartTime)+"ms");
                Log.i("ImageSaver","run() : config CameraPinhole pinholeModel");
            CameraPinhole pinholeModel = new CameraPinhole(
                    focal_length_in_pixels_x, focal_length_in_pixels_y,
                    skew,
                    focal_midpoint_pixels_x,focal_midpoint_pixels_y,
                    imageWidthInt,imageHeightInt);
                Log.i("ImageSaver","run() : config LensDistortionNarrowFOV pinholeDistort");
            LensDistortionNarrowFOV pinholeDistort = new LensDistortionPinhole(pinholeModel);
                Log.i("ImageSaver","run() : config detector.setLensDistortion(pinholeDistort)");
            detector.setLensDistortion(pinholeDistort);  // TODO - do BoofCV calibration - but assume perfect pinhole camera for now
                Log.i("ImageSaver","run() : after detector.setLensDistortion(pinholeDistort) in "+ AppMonitor.timeElapsed(startTime)+"ms");

            // TODO - timing here  c[camera_num]-f[frameprocessed]
            long timeNow;
                Log.i("ImageSaver","run() : start detector.detect(grayImage) at "+Calendar.getInstance().getTimeInMillis());
            Log.i("ImageSaver","run() : before detector.detect(grayImage) :timeElapsed: "+ AppMonitor.timeElapsed(startTime)+"ms");
            detector.detect(grayImage);
                Log.i("ImageSaver","run() : after detector.detect(grayImage) :timeElapsed: "+ AppMonitor.timeElapsed(startTime)+"ms");
                Log.i("ImageSaver","run() : finished detector.detect(grayImage);");
            String logTag = "ImageSaver";
            for (int i = 0; i < detector.totalFound(); i++) {
                timeNow = Calendar.getInstance().getTimeInMillis();
                if( detector.hasUniqueID() ) {
                    long tag_id_long = detector.getId(i);
                        Log.i(logTag, "run() : tag detection "+i+" after detector.getId("+i+") = "+tag_id_long+" :timeElapsed:timeNow: " + AppMonitor.timeElapsed(timeNow) + "ms");
                        Log.i(logTag, "run() : tag detection "+i+" after detector.getId("+i+") = "+tag_id_long+" :timeElapsed:startTime: " + AppMonitor.timeElapsed(startTime) + "ms");
                } else {
                        Log.i(logTag, "run() : tag detection "+i+" has no id; detector.hasUniqueID() == false ");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
        }
    }

}
