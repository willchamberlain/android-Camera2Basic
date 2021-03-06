package com.example.android.camera2basic;

import android.graphics.Bitmap;
import android.media.Image;
import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
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
class ImageSaverAsyncTask extends AsyncTask<Void, Void, Long> { //parameter array type, progress type, return type
    private Camera2BasicFragment camera2BasicFragment;
    byte[] luminanceBytes;
    int imageWidth;
    int imageHeight;

    long instantiationTimeMs = -1L;
    long executionThreadId = -1L;
    long executionStartTimeMs = -1L;
    long executionEndTimeMs = -1L;

    public ImageSaverAsyncTask(Camera2BasicFragment camera2BasicFragment, Image image) {  //, Camera2BasicFragment.TaskCompletionTimer taskCompletionTimer_) {
        super();
        this.camera2BasicFragment = camera2BasicFragment;
        Log.i("ImageSaverAsyncTask","ImageSaverAsyncTask(Camera2BasicFragment camera2BasicFragment, Image image)");

            Log.i("ImageSaverAsyncTask","before ImageToOpenCVConverter.convertYuv420ToBitmap(image)");
            Bitmap bitmap = ImageToOpenCVConverter.convertYuv420ToBitmap(image);        // NOTE: I think that this advances the buffer counter so that I have to rewind after this for the conversion.
            Log.i("ImageSaverAsyncTask","after ImageToOpenCVConverter.convertYuv420ToBitmap(image)");
//            Log.i("ImageSaverAsyncTask","bitmap.getWidth() = "+bitmap.getWidth());
//            Log.i("ImageSaverAsyncTask","bitmap.getHeight() = "+bitmap.getHeight());
////            int[] pixels_ = new int[100];
////            for (int ii_=0; ii_<pixels_.length; ii_++) {
////                pixels_[ii_] = bitmap.getPixel(1,ii_);
////            }
////            Log.i("ImageSaverAsyncTask","bitmap first column="+ Arrays.toString(pixels_));

        long startTime = Calendar.getInstance().getTimeInMillis();
        instantiationTimeMs = startTime;
            Log.i("ImageSaverAsyncTask","ImageSaverAsyncTask(Image image): start = "+startTime);
            Log.i("ImageSaverAsyncTask","ImageSaverAsyncTask(Image image): image(w,h)="+image.getWidth()+","+image.getHeight()+" num planes = "+image.getPlanes().length+", format="+image.getFormat());
        for (int ii_=0; ii_< image.getPlanes().length; ii_++) {
            Image.Plane plane = image.getPlanes()[ii_];
            Log.i("ImageSaverAsyncTask","ImageSaverAsyncTask(Image image): plane "+ii_+" getRowStride="+plane.getRowStride()+" getPixelStride= "+plane.getPixelStride()+", plane.getBuffer().remaining()="+plane.getBuffer().remaining()+", format="+image.getFormat());
            plane.getBuffer().rewind();
            Log.i("ImageSaverAsyncTask","ImageSaverAsyncTask(Image image): plane "+ii_+" getRowStride="+plane.getRowStride()+" getPixelStride= "+plane.getPixelStride()+", plane.getBuffer().remaining()="+plane.getBuffer().remaining()+", format="+image.getFormat());
        }
        ByteBuffer luminanceBuffer = /*mImage*/image.getPlanes()[0].getBuffer();
        Log.i("ImageSaverAsyncTask","ImageSaverAsyncTask(Image image): after image.getPlanes()[0].getBuffer() :timeElapsed: "+ AppMonitor.timeElapsed(startTime)+"ms");
        /*byte[]*/ luminanceBytes = new byte[luminanceBuffer.remaining()];  // buffer size: current position is zero, remaining() gives "the number of elements between the current position and the limit"
        Log.i("ImageSaverAsyncTask","ImageSaverAsyncTask(Image image): after luminanceBytes = new byte[luminanceBuffer.remaining()] :timeElapsed: "+ AppMonitor.timeElapsed(startTime)+"ms");
        luminanceBuffer.get(luminanceBytes);                            // copy from buffer to bytes: get() "transfers bytes from this buffer into the given destination array"
        imageWidth = image.getWidth();
        imageHeight = image.getHeight();
        Log.i("ImageSaverAsyncTask","ImageSaverAsyncTask(Image image): image dimensions: "+imageWidth+" pixels wide, "+imageHeight+" pixels high: bytes="+luminanceBytes.length);
//            taskCompletionTimer = taskCompletionTimer_;
        Log.i("ImageSaverAsyncTask","ImageSaverAsyncTask(Image image): end after :timeElapsed: "+ AppMonitor.timeElapsed(startTime)+"ms");
    }

    @Override
    protected Long doInBackground(Void... params) { //(Image...  images) {
        executionThreadId = Thread.currentThread().getId();
        String logTag = "ImgeSv_p="+executionThreadId;
        boolean incremented = false;
        try {
            Log.e(logTag,"doInBackground: incremented thread count from "+camera2BasicFragment.taskCompletionTimer.concurrentThreadsExecuting());
            camera2BasicFragment.taskCompletionTimer.incConcurrentThreadsExecuting();
            Log.e(logTag,"doInBackground: incremented thread count to "+camera2BasicFragment.taskCompletionTimer.concurrentThreadsExecuting());
            incremented = true;
            Long result = doDoInBackground(params);
            return result;
        } catch (Throwable t) {
            Log.e(logTag,"doInBackground: caught t in doDoInBackground, now re-throwing: "+t,t);
            t.printStackTrace();
            throw t;
        } finally {
            if(incremented) {
                Log.e(logTag,"doInBackground: decrementing thread count from "+camera2BasicFragment.taskCompletionTimer.concurrentThreadsExecuting());
                camera2BasicFragment.taskCompletionTimer.decConcurrentThreadsExecuting();
                Log.e(logTag,"doInBackground: decremented thread count to "+camera2BasicFragment.taskCompletionTimer.concurrentThreadsExecuting());
            }
        }
    }

    protected Long doDoInBackground(Void... params) { //(Image...  images) {
        executionThreadId = Thread.currentThread().getId();
        String logTag = "ImgeSv_p="+executionThreadId;
        long procStartTime = Calendar.getInstance().getTimeInMillis();
        Log.i(logTag, "doInBackground(): start = " + procStartTime);
        int numThreads = camera2BasicFragment.taskCompletionTimer.concurrentThreadsExecuting();
        if( Camera2BasicFragment.maxConcurrentThreads < numThreads ) {
                Log.i(logTag, "doInBackground(): stopping without processing: there are too many threads executing : "+Camera2BasicFragment.maxConcurrentThreads+"<"+numThreads );
            return new Long(0L);
        }
            executionStartTimeMs = procStartTime;
            try {
                    Log.i(logTag, "doInBackground() : doing some work in the Try block: concurrentThreadsExecuting = "+ camera2BasicFragment.taskCompletionTimer.concurrentThreadsExecuting());
                    Log.i(logTag, "doInBackground() : doing some work in the Try block: Runtime.getRuntime().availableProcessors() = "+Runtime.getRuntime().availableProcessors());

                Random random = new Random();
                if (1 == random.nextInt()) {
                    Log.i(logTag, "doInBackground() : throwing an IOException at random while doing some work in the Try block");
                    throw new IOException("No particular reason: ImageSaver.doInBackground() : throwing an IOException at random while doing some work in the Try block");
                }
                // dummy image processing code - https://boofcv.org/index.php?title=Android_support
                long algorithmStepStartTime = 0L;
                algorithmStepStartTime = Calendar.getInstance().getTimeInMillis();
                    Log.i(logTag, "doInBackground() : before constructing grayImage: imageWidth="+imageWidth+",imageHeight="+imageHeight+" :timeElapsed on step: " + AppMonitor.timeElapsed(algorithmStepStartTime)  + "ms:  timeElapsed since method start: "+ AppMonitor.timeElapsed(procStartTime) + "ms");
                GrayF32 grayImage = camera2BasicFragment.fetchAGrayImageToUse(imageWidth, imageHeight); //  new GrayF32(imageWidth, imageHeight);

                    Log.i(logTag, "doInBackground() : after constructing grayImage :timeElapsed on step: " + AppMonitor.timeElapsed(algorithmStepStartTime)  + "ms:  timeElapsed since method start: "+ AppMonitor.timeElapsed(procStartTime) + "ms");
                // from NV21 to gray scale
                algorithmStepStartTime = Calendar.getInstance().getTimeInMillis();
                    Log.i(logTag, "doInBackground() : before converting nv21ToGray :timeElapsed on step: " + AppMonitor.timeElapsed(algorithmStepStartTime)  + "ms:  timeElapsed since method start: "+ AppMonitor.timeElapsed(procStartTime) + "ms");
                ConvertNV21.nv21ToGray(luminanceBytes, imageWidth, imageHeight, grayImage);
                    Log.i(logTag, "doInBackground() : after converting nv21ToGray :timeElapsed on step: " + AppMonitor.timeElapsed(algorithmStepStartTime) + "ms:  timeElapsed since method start: "+ AppMonitor.timeElapsed(procStartTime) + "ms");

                // start try detecting tags in the frame
                double BOOFCV_TAG_WIDTH = 0.14;
                int imageWidthInt = grayImage.getHeight(); // new Double(matGray.size().width).intValue();
                int imageHeightInt = grayImage.getWidth(); //new Double(matGray.size().height).intValue();
                    Log.i(logTag,"doInBackground() : image dimensions: "+imageWidthInt+" pixels wide, "+imageHeightInt+" pixels high");
                float imageWidthFloat = (float) imageWidthInt; // new Double(matGray.size().width).intValue();
                float imageHeightFloat = (float) imageHeightInt; //new Double(matGray.size().height).intValue();
                float focal_midpoint_pixels_x = imageWidthFloat / 2.0f;
                float focal_midpoint_pixels_y = imageHeightFloat / 2.0f;

                double skew = 0.0;

                // TODO - 640 is now a magic number : it is the image width in pixels at the time of calibration of focal length
                float focal_length_in_pixels_x = 519.902859f * (imageWidthFloat / 640.0f);  // TODO - for Samsung Galaxy S3s from /mnt/nixbig/ownCloud/project_AA1__1_1/results/2016_12_04_callibrate_in_ROS/calibrationdata_grey/ost.txt
                float focal_length_in_pixels_y = 518.952669f * (imageHeightFloat / 480.0f);  // TODO - for Samsung Galaxy S3s from /mnt/nixbig/ownCloud/project_AA1__1_1/results/2016_12_04_callibrate_in_ROS/calibrationdata_grey/ost.txt


                    Log.i(logTag, "doInBackground() : config FactoryFiducial.squareBinary");
                FiducialDetector<GrayF32> detector = FactoryFiducial.squareBinary(
                        new ConfigFiducialBinary(BOOFCV_TAG_WIDTH),
                        ConfigThreshold.local(ThresholdType.LOCAL_SQUARE, 10),          // TODO - evaluate parameter - ?'radius'?
                        GrayF32.class);  // tag size,  type,  ?'radius'?
                //        detector.setLensDistortion(lensDistortion);
                Log.i(logTag, "doInBackground() : config CameraPinhole pinholeModel");
                CameraPinhole pinholeModel = new CameraPinhole(
                        focal_length_in_pixels_x, focal_length_in_pixels_y,
                        skew,
                        focal_midpoint_pixels_x, focal_midpoint_pixels_y,
                        imageWidthInt, imageHeightInt);
                Log.i(logTag, "doInBackground() : config LensDistortionNarrowFOV pinholeDistort");
                LensDistortionNarrowFOV pinholeDistort = new LensDistortionPinhole(pinholeModel);
                Log.i(logTag, "doInBackground() : config detector.setLensDistortion(pinholeDistort)");
                detector.setLensDistortion(pinholeDistort);  // TODO - do BoofCV calibration - but assume perfect pinhole camera for now

                // TODO - timing here  c[camera_num]-f[frameprocessed]
                long timeNow = Calendar.getInstance().getTimeInMillis();
                    Log.i(logTag, "doInBackground() : start detector.detect(grayImage) at " + timeNow);
                    Log.i(logTag, "doInBackground() : before detector.detect(grayImage) :timeElapsed: " + AppMonitor.timeElapsed(timeNow) + "ms: time since start = " + AppMonitor.timeElapsed(procStartTime) + "ms");
                try { detector.detect(grayImage); } catch (Throwable e) {
                    Log.e(logTag,"Exception error in detector.detect(grayImage): "+e.getMessage(), e);
                    e.printStackTrace();
                    return new Long(0L);
                }
                    Log.i(logTag, "doInBackground() : after detector.detect(grayImage) :timeElapsed: " + AppMonitor.timeElapsed(timeNow) + "ms: time since start = " + AppMonitor.timeElapsed(procStartTime) + "ms");
                for (int i = 0; i < detector.totalFound(); i++) {
                    timeNow = Calendar.getInstance().getTimeInMillis();
                    int tag_id = -1;
                    MarkerIdValidator isTagIdValid = new MarkerIdValidator(detector, i, tag_id).invoke();
                    if (!isTagIdValid.isValid()) {
//// todo - might want to do this processing and image processing in a background thread but then push the image into a short queue that the UI thread can pull the latest from
//// todo (cont) e.g. see https://stackoverflow.com/questions/19216893/android-camera-asynctask-with-preview-callback
//// todo (cont) note that BoofCV draws to Swing windows, which is nice because it's cross-platform
//// todo (cont) Processing or OpenGL or Unity might be better cross-platform choices
////   todo - Processing - http://blog.blprnt.com/blog/blprnt/processing-android-mobile-app-development-made-very-easy  then  http://android.processing.org/  then https://www.mobileprocessing.org/cameras.html
////
//// todo - for threaded, have a look at why this wouldn't work: https://stackoverflow.com/questions/14963773/android-asynctask-to-process-live-video-frames
////    https://stackoverflow.com/questions/18183016/android-camera-frame-processing-with-multithreading?rq=1
////    https://stackoverflow.com/questions/12215702/how-to-use-blocking-queue-to-process-camera-feed-in-background?noredirect=1&lq=1
////    https://stuff.mit.edu/afs/sipb/project/android/docs/training/displaying-bitmaps/process-bitmap.html
////    https://stuff.mit.edu/afs/sipb/project/android/docs/training/multiple-threads/index.html
////    https://stuff.mit.edu/afs/sipb/project/android/docs/training/graphics/opengl/index.html
////                          drawMarkerLocationOnDisplay_BoofCV(detector, i, FeatureModel.FEATURE_WITHOUT_3D_LOCATION);
                        continue;
                    }
                    tag_id = isTagIdValid.getTag_id();

                    camera2BasicFragment.finishedUsingGrayImage(grayImage); grayImage = null;  // finished using the image, return it to the queue : cannot use it after this point

                    if( detector.hasUniqueID() ) {
                        long tag_id_long = detector.getId(i);
                            Log.i(logTag, "doInBackground() : tag detection "+i+" after detector.getId("+i+") = "+tag_id_long+" :timeElapsed: " + AppMonitor.timeElapsed(timeNow) + "ms : :timeElapsed: " + AppMonitor.timeElapsed(procStartTime) + "ms from start");
                        // if is for a current task, track it

                        // if is for a current task, report it
                    } else {
                            Log.i(logTag, "doInBackground() : tag detection "+i+" has no id; detector.hasUniqueID() == false ");
                        continue;
                    }
                }
            } catch (RuntimeException e) {
                Log.e(logTag, "doInBackground() : RuntimeException "+e, e);
                e.printStackTrace();
            } catch (IOException e) {
                Log.e(logTag, "doInBackground() : IOException "+e, e);
                e.printStackTrace();
            } finally {
                long timeNow = Calendar.getInstance().getTimeInMillis();
                long timeElapsed = timeNow - procStartTime;
                int fps = (int) (1.0/(timeElapsed/1000.0));
                recordPerformance(procStartTime, fps);
            }
        executionEndTimeMs = Calendar.getInstance().getTimeInMillis();
        return new Long(0L);
    }

    private void recordPerformance(long startTime, int fps) {
        camera2BasicFragment.countOfFpsAndThreads.put("p="+executionThreadId+",t="+startTime, new PerformanceMetric(fps, camera2BasicFragment.taskCompletionTimer.concurrentThreadsExecuting()));
        camera2BasicFragment.logOfFpsAndThreads.put("p="+executionThreadId+",t="+startTime, new PerformanceMetric(fps, camera2BasicFragment.taskCompletionTimer.concurrentThreadsExecuting()));
    }


    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected void onProgressUpdate(Void... values) {
        super.onProgressUpdate(values);
    }

    @Override
    protected void onPostExecute(Long result) {
        super.onPostExecute(result);
        camera2BasicFragment.taskCompletionTimer.completedTask(executionThreadId, instantiationTimeMs, executionStartTimeMs, executionEndTimeMs);
            Log.i("ImageSaverAsyncTask", "onPostExecute(): there are now "+camera2BasicFragment.taskCompletionTimer.concurrentThreadsExecuting.get()+"threads executing");
    }

}
