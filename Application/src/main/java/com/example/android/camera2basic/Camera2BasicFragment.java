/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.camera2basic;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.v13.app.FragmentCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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


public class Camera2BasicFragment extends Fragment
        implements View.OnClickListener, FragmentCompat.OnRequestPermissionsResultCallback {

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final String FRAGMENT_DIALOG = "dialog";

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "Camera2BasicFragment";

    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_SHOWING_CAMERA_PREVIEW = 0;

    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_FOR_FOCUS_LOCK = 1;

    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_FOR_EXPOSURE_TO_BE_PRECAPTURE = 2;

    /**
     * Camera state: Waiting for the exposure state to be something other than precapture.
     */
    private static final int STATE_WAITING_FOR_EXPOSURE_TO_NOT_BE_PRECAPTURE = 3;

    /**
     * Camera state: Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 640; //1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 480; //1080;

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };

    /**
     * ID of the current {@link CameraDevice}.
     */
    private String cameraId;

    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView mTextureView;

    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * The {@link android.util.Size} of camera preview.
     */
    private Size imageSize;


    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }

    };

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;
    private HandlerThread mBackgroundThread2;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler backgroundHandler;
    private Handler mBackgroundHandler2;

    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader mImageReader;

    /**
     * This is the output file for our picture.
     */
    private File mFile;

    private int skipRate = 10; // initialise to be over 10
    long frameNum = 0;
    long skipRateReducedOnFrameNum = 0;
    TaskCompletionTimer taskCompletionTimer = TaskCompletionTimer.instance();


    /**
     * TODO Do the image processing - this a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener onImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {
        final int RUN_ASYNC_BACKGROUNDHANDLER =  10;
        final int RUN_ASYNC_ASYNCTASK =         20;
        final int RUN_ASYNC_METHOD =            RUN_ASYNC_ASYNCTASK;

        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image =  reader.acquireNextImage();       // TODO see https://stackoverflow.com/a/43564630/1200764
            frameNum++;  if (frameNum == Long.MAX_VALUE) { frameNum=1; }
            switch (RUN_ASYNC_METHOD) {
                case RUN_ASYNC_BACKGROUNDHANDLER: {
                    if (null != backgroundHandler) {
                        backgroundHandler.post(
                                new ImageSaver(
                                        image /*,                          // TODO see https://stackoverflow.com/a/43564630/1200764
                                mFile */ ));  // push ImageSaver runnable/task onto the backgroundthread's message queue to be executed on the backgroundthread
                        if (image != null) {             // ?? causes an exception because !when the app starts! closed before can save                // TODO see https://stackoverflow.com/a/43564630/1200764
                            image.close();               // ?? causes an exception because !when the app starts! closed before can save                // TODO see https://stackoverflow.com/a/43564630/1200764
                        }
                    }
                    break;
                }
                case RUN_ASYNC_ASYNCTASK: {
                    if (skipRate <= 10 && skipRate>0 && frameNum%skipRate == 0 ) { // skip this image - e.g. if skipRate == 8 and frameNum == 16
                        Log.i("Camera2BasicFragment","skipping frame "+frameNum+" on skipRate "+skipRate);
                        if (image != null) {             // ?? causes an exception because !when the app starts! closed before can save                // TODO see https://stackoverflow.com/a/43564630/1200764
                            image.close();               // ?? causes an exception because !when the app starts! closed before can save                // TODO see https://stackoverflow.com/a/43564630/1200764
                        }
                        break;
                    }
                    int TARGET_FRAME_RATE = 10;
                    if ( skipRateReducedOnFrameNum < frameNum - 100 && skipRate > 2 && taskCompletionTimer.overlapWithLastInitiation() > (5000/TARGET_FRAME_RATE)) { // more than 1s overlap; going wrong
                        skipRate--; if(skipRate < 2){ skipRate = 2; }
                        skipRateReducedOnFrameNum = frameNum;
                        Log.i("Camera2BasicFragment","at frame "+frameNum+" reduced skipRate to "+skipRate+" on taskCompletionTimer.overlapWithLastInitiation() = "+taskCompletionTimer.overlapWithLastInitiation()+" > (5000/"+TARGET_FRAME_RATE+")");
                    }
                    try {
                        //  new ImageSaverAsyncTask(image, taskCompletionTimer).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);  // https://stackoverflow.com/questions/18266161/achieving-concurrency-using-asynctask  https://developer.android.com/reference/java/util/concurrent/Executor.html
                        new ImageSaverAsyncTask(image, taskCompletionTimer).executeOnExecutor(threadPoolExecutor);  // https://stackoverflow.com/questions/18266161/achieving-concurrency-using-asynctask  https://developer.android.com/reference/java/util/concurrent/Executor.html

                        if (image != null) {             // ?? causes an exception because !when the app starts! closed before can save                // TODO see https://stackoverflow.com/a/43564630/1200764
                            image.close();               // ?? causes an exception because !when the app starts! closed before can save                // TODO see https://stackoverflow.com/a/43564630/1200764
                        }
                    } catch (java.util.concurrent.RejectedExecutionException ree) {
                        Log.w("Camera2BasicFragment","onImageAvailable(ImageReader reader): hit the limit of available threads with a java.util.concurrent.RejectedExecutionException");
                        if(skipRate <= 10){ skipRate--; skipRateReducedOnFrameNum = frameNum; }
                        if(skipRate > 10){ skipRate = 10; }
                        if(skipRate < 2){ skipRate = 2; }
                        if (image != null) {             // ?? causes an exception because !when the app starts! closed before can save                // TODO see https://stackoverflow.com/a/43564630/1200764
                            image.close();               // ?? causes an exception because !when the app starts! closed before can save                // TODO see https://stackoverflow.com/a/43564630/1200764
                        }
                    }
                    break;
                }
                default:
            }
        }
    };

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * {@link CaptureRequest} generated by {@link #mPreviewRequestBuilder}
     */
    private CaptureRequest mPreviewRequest;

    /**
     * The current state of camera state for taking pictures.
     *
     * @see #mCaptureCallback
     */
    private int mState = STATE_SHOWING_CAMERA_PREVIEW;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * Whether the current camera device supports Flash or not.
     */
    private boolean mFlashSupported;

    /**
     * Orientation of the camera sensor
     */
    private int mSensorOrientation;

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_SHOWING_CAMERA_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_FOR_FOCUS_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE); // check the auto-focus state
                    if (afState == null) {                                        // no particular auto-focus state: capture a frame
                        captureStillPicture();
                                                                                    // ? missing  mState = STATE_PICTURE_TAKEN;  ?
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||      // autofocus is locked, may be able to capture a frame
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE); // check the auto-exposure state
                        if (aeState == null ||                                        // no particular auto-exposure state or auto-exposure state is converged/ready: capture a frame
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_FOR_EXPOSURE_TO_BE_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE); // check the auto-exposure state
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_FOR_EXPOSURE_TO_NOT_BE_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_FOR_EXPOSURE_TO_NOT_BE_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE); // check the auto-exposure state
                    if (aeState == null ||
                            aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };

    /**
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show
     */
    private void showToast(final String text) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSizeForImage(Size[] choices, int textureViewWidth,
                                                  int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                    option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    public static Camera2BasicFragment newInstance() {
        Log.i("Camera2BasicFragment","newInstance(): start");
        return new Camera2BasicFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera2_basic, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        view.findViewById(R.id.picture).setOnClickListener(this);
        view.findViewById(R.id.info).setOnClickListener(this);
        mTextureView = (AutoFitTextureView) view.findViewById(R.id.texture);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mFile = new File(getActivity().getExternalFilesDir(null), "pic.jpg");
    }

    @Override
    public void onResume() {
        super.onResume();

        Log.i("Camera2BasicFragment","onResume(): start");


        startBackgroundThread();
        threadPoolExecutor.allowCoreThreadTimeOut(true);


        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }


    }

    @Override
    public void onPause() {
        Log.i("Camera2BasicFragment","onPause(): start");
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    private void requestCameraPermission() {
        if (FragmentCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            FragmentCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                ErrorDialog.newInstance(getString(R.string.request_permission))
                        .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private void setUpCameraOutputs(int width, int height) {
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            logCameraCharacteristics(manager);
            for (String cameraId_ : manager.getCameraIdList()) {
                CameraCharacteristics cameraDetails = manager.getCameraCharacteristics(cameraId_);

                if(isFrontFacingCameraId(cameraDetails)) { continue; } // We don't use a front facing camera in this sample.

                StreamConfigurationMap map = cameraDetails.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) { continue; }

                // For still image captures, we use the largest available size.
                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888 /*ImageFormat.JPEG*/ )),         // TODO see https://stackoverflow.com/a/43564630/1200764
                        new CompareSizesByArea());



                // Find out if we need to swap dimension to get the preview size relative to sensor  coordinate.
                int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                //noinspection ConstantConditions
                mSensorOrientation = cameraDetails.get(CameraCharacteristics.SENSOR_ORIENTATION);

                Point displaySize = new Point();
                activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth = width;         int rotatedPreviewHeight = height;
                int maxPreviewWidth     = displaySize.x; int maxPreviewHeight     = displaySize.y;
                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0: case Surface.ROTATION_180:
                        if (mSensorOrientation == 90 || mSensorOrientation == 270) { swappedDimensions = true; }
                        break;
                    case Surface.ROTATION_90: case Surface.ROTATION_270:
                        if (mSensorOrientation == 0 || mSensorOrientation == 180) { swappedDimensions = true; }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                }
                if (swappedDimensions) {
                    rotatedPreviewWidth = height;         rotatedPreviewHeight = width;
                    maxPreviewWidth     = displaySize.y;  maxPreviewHeight     = displaySize.x;
                }
                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) { maxPreviewWidth = MAX_PREVIEW_WIDTH; }
                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) { maxPreviewHeight = MAX_PREVIEW_HEIGHT; }

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                imageSize = chooseOptimalSizeForImage(map.getOutputSizes(SurfaceTexture.class),  // TODO - match the mImageReader = ImageReader.newInstance() to this preview size
                        rotatedPreviewWidth, rotatedPreviewHeight, 
                        maxPreviewWidth, maxPreviewHeight, largest);
                    Log.i(TAG, "setUpCameraOutputs(int "+width+", int "+height+"): mPreviewSize: "+imageSize.getWidth()+"x"+imageSize.getHeight());
                    /* TODO - "the ImageReader class allows direct application access to image data rendered into a {@link android.view.Surface"*/
                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mTextureView.setAspectRatio(imageSize.getWidth(), imageSize.getHeight());           // Fit the aspect ratio of TextureView to the size of preview.
                    mImageReader = ImageReader.newInstance( imageSize.getWidth(), imageSize.getHeight(), ImageFormat.YUV_420_888, 2 /*maxImages*/ ); // TODO see https://stackoverflow.com/a/43564630/1200764 - this scaling is arbitrary; reduces the max width from 4160 to 240, so always get 320x240
                } else {
                    mTextureView.setAspectRatio(imageSize.getHeight(), imageSize.getWidth());
                    mImageReader = ImageReader.newInstance( imageSize.getHeight(), imageSize.getWidth(), ImageFormat.YUV_420_888, 2 /*maxImages*/ ); // TODO see https://stackoverflow.com/a/43564630/1200764 - this scaling is arbitrary; reduces the max width from 4160 to 240, so always get 320x240
                }

                /* TODO - this configures the image processing as a callback that is called whenever a frame is available
                    - onImageAvailableListener is a  ImageReader.OnImageAvailableListener, which is called whenever mImageReader has a frame available to process
                    - backgroundHandler is a Handler
                 */
                mImageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);
                boolean flashSupported = isFlashSupported(cameraDetails);
                cameraId = cameraId_;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
    }

    private boolean isFlashSupported(CameraCharacteristics cameraDetails) {
        // Check if the flash is supported.
        Boolean available = cameraDetails.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        return available == null ? false : available;
    }

    private boolean isFrontFacingCameraId(CameraCharacteristics characteristics) {
        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        boolean frontFacingCameraId = false;
        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
            frontFacingCameraId = true;
        }
        return frontFacingCameraId;
    }

    /**
     * Opens the camera specified by {@link Camera2BasicFragment#cameraId}.
     */
    private void openCamera(int width, int height) {
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
        }
        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(cameraId, mStateCallback, backgroundHandler);
            logCameraCharacteristics(manager);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    private void logCameraCharacteristics(CameraManager manager) throws CameraAccessException {
        String[] cameras = manager.getCameraIdList();
        for(String camera : cameras) {
            CameraCharacteristics cc = manager.getCameraCharacteristics(camera);
            CameraCharacteristics.Key<int[]> aa = cc.REQUEST_AVAILABLE_CAPABILITIES;
            for (int i = 0; i < cc.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES).length; i++) {
                Log.e(TAG, "Capability: " + cc.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)[i]);
            }
        }
        for(String camera : cameras) {
            CameraCharacteristics cc = manager.getCameraCharacteristics(camera);
            Range<Integer>[] fpsRange = cc.get(cc.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            Log.e(TAG, "fpsRange: [" + fpsRange[0] + "," + fpsRange[1] + "]");
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        backgroundHandler = new Handler(mBackgroundThread.getLooper());
        mBackgroundThread2 = new HandlerThread("CameraBackground2");
        mBackgroundThread2.start();
        mBackgroundHandler2 = new Handler(mBackgroundThread2.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mBackgroundThread2.quitSafely();
        try {
            mBackgroundThread2.join();
            mBackgroundThread2 = null;
            mBackgroundHandler2 = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * TODO see https://stackoverflow.com/a/43564630/1200764
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession() {
        Log.i("Camera2BasicFragment","createCameraPreviewSession(): start");
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(imageSize.getWidth(), imageSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            Surface mImageSurface = mImageReader.getSurface();          // TODO see https://stackoverflow.com/a/43564630/1200764
            mPreviewRequestBuilder.addTarget(mImageSurface);            // TODO see https://stackoverflow.com/a/43564630/1200764

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // Flash is automatically enabled when necessary.
                                setAutoFlash(mPreviewRequestBuilder);

                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, backgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            showToast("Failed");
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mTextureView || null == imageSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, imageSize.getHeight(), imageSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / imageSize.getHeight(),
                    (float) viewWidth / imageSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    /**
     * Initiate a still image capture.
     */
    private void takePicture() {
        Log.i("Camera2BasicFragment","takePicture()");
        lockFocus();
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        try {
            // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_FOR_FOCUS_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
     */
    private void runPrecaptureSequence() {
        Log.i("Camera2BasicFragment","runPrecaptureSequence()");
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_FOR_EXPOSURE_TO_BE_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCaptureCallback} from both {@link #lockFocus()}.
     */
    private void captureStillPicture() {
        try {
            final Activity activity = getActivity();
            if (null == activity || null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            setAutoFlash(captureBuilder);

            // Orientation
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    showToast("Saved: " + mFile);
                    Log.d(TAG, mFile.toString());
                    unlockFocus();
                }
            };

            mCaptureSession.stopRepeating();
            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            final Range<Integer> FIFTEEN_FPS = new Range<Integer>(15,15);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, FIFTEEN_FPS);
            setAutoFlash(mPreviewRequestBuilder);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    backgroundHandler);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_SHOWING_CAMERA_PREVIEW;
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
                    backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View view) {
        Log.i("Camera2BasicFragment","onClick(View view)");
        switch (view.getId()) {
            case R.id.picture: {
                takePicture();
                break;
            }
            case R.id.info: {
                Activity activity = getActivity();
                if (null != activity) {
                    new AlertDialog.Builder(activity)
                            .setMessage(R.string.intro_message)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
                break;
            }
        }
    }

    private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
        if (mFlashSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }

    /**
     * Saves a JPEG {@link Image} into the specified {@link File}.
     */
    private static class ImageSaver implements Runnable {
        byte[] luminanceBytes;
        int imageWidth;
        int imageHeight;

        public ImageSaver(Image image /*, File file*/) {
            Log.i("ImageSaver","ImageSaver(Image image)");
            long startTime = Calendar.getInstance().getTimeInMillis();
            Log.i("ImageSaver","ImageSaver(Image image): start = "+startTime);
            ByteBuffer luminanceBuffer = /*mImage*/image.getPlanes()[0].getBuffer();
            Log.i("ImageSaver","ImageSaver(Image image): after image.getPlanes()[0].getBuffer() in "+timeElapsed(startTime)+"ms");
            /*byte[]*/ luminanceBytes = new byte[luminanceBuffer.remaining()];  // buffer size: current position is zero, remaining() gives "the number of elements between the current position and the limit"
            Log.i("ImageSaver","ImageSaver(Image image): after luminanceBytes = new byte[luminanceBuffer.remaining()] in "+timeElapsed(startTime)+"ms");
            luminanceBuffer.get(luminanceBytes);                            // copy from buffer to bytes: get() "transfers bytes from this buffer into the given destination array"
            imageWidth = image.getWidth();
            imageHeight = image.getHeight();
            Log.i("ImageSaver","ImageSaver(Image image): end after "+timeElapsed(startTime)+"ms");
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
                long algorithmStepStartTime =0L;
                algorithmStepStartTime = Calendar.getInstance().getTimeInMillis();
                GrayF32 grayImage = new GrayF32(imageWidth,imageHeight);
                Log.i("ImageSaver","run() : after constructing grayImage in "+timeElapsed(algorithmStepStartTime)+"ms");
                // from NV21 to gray scale
                algorithmStepStartTime = Calendar.getInstance().getTimeInMillis();
                ConvertNV21.nv21ToGray(luminanceBytes,imageWidth,imageHeight, grayImage);
                Log.i("ImageSaver","run() : after converting nv21ToGray in "+timeElapsed(algorithmStepStartTime)+"ms");

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
                FiducialDetector<GrayF32> detector = FactoryFiducial.squareBinary(
                        new ConfigFiducialBinary(BOOFCV_TAG_WIDTH),
                        ConfigThreshold.local(ThresholdType.LOCAL_SQUARE, 10),          // TODO - evaluate parameter - ?'radius'?
                        GrayF32.class);  // tag size,  type,  ?'radius'?
                //        detector.setLensDistortion(lensDistortion);
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

                // TODO - timing here  c[camera_num]-f[frameprocessed]
                long timeNow = Calendar.getInstance().getTimeInMillis();
                Log.i("ImageSaver","run() : start detector.detect(grayImage);");
                Log.i("ImageSaver","run() : start detector.detect(grayImage) at "+Calendar.getInstance().getTimeInMillis());
                detector.detect(grayImage);
                Log.i("ImageSaver","run() : after detector.detect(grayImage) in "+timeElapsed(startTime)+"ms");
                Log.i("ImageSaver","run() : finished detector.detect(grayImage);");
                String logTag = "ImageSaver";
                for (int i = 0; i < detector.totalFound(); i++) {
                    timeNow = Calendar.getInstance().getTimeInMillis();
                    if( detector.hasUniqueID() ) {
                        long tag_id_long = detector.getId(i);
                        Log.i(logTag, "run() : tag detection "+i+" after detector.getId("+i+") = "+tag_id_long+" in " + timeElapsed(timeNow) + "ms");
                        Log.i(logTag, "run() : tag detection "+i+" after detector.getId("+i+") = "+tag_id_long+" in " + timeElapsed(startTime) + "ms");
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


    static private class TaskCompletionTimer {

        long threadCompletions = 1L;
        long previousInitiationTimeMs = -1;
        long previousExecutionStartTimeMs = -1;
        long previousExecutionEndTimeMs = -1;
        long overlapWithLastInitiation = -1;
        long overlapWithLastExecution = -1;
        long completionAfterPrevious = -1;
        long executionPeriod = -1;
        AtomicInteger concurrentThreadsExecuting = new AtomicInteger(0);

        private TaskCompletionTimer() {
        }

        static TaskCompletionTimer instance() {
            return new TaskCompletionTimer();
        }

        long overlapWithLastInitiation() {
            return overlapWithLastInitiation;
        }

        void incConcurrentThreadsExecuting() {
            concurrentThreadsExecuting.incrementAndGet();
        }

        void decConcurrentThreadsExecuting() {
            concurrentThreadsExecuting.decrementAndGet();
        }

        int concurrentThreadsExecuting() {
            return concurrentThreadsExecuting.intValue();
        }

        void completedTask(long executionThreadId, long initiationTimeMs, long executionStartTimeMs, long executionEndTimeMs) {
            decConcurrentThreadsExecuting();
            if (threadCompletions>1) {
                overlapWithLastInitiation = executionEndTimeMs-previousInitiationTimeMs;
                overlapWithLastExecution = executionEndTimeMs-previousInitiationTimeMs;
                completionAfterPrevious = executionEndTimeMs-previousExecutionEndTimeMs;
                executionPeriod = executionEndTimeMs-executionStartTimeMs;
                Log.i("completedTask","thread completion "+completionAfterPrevious+"ms after previous: "+overlapWithLastInitiation+"ms overlap with last started: thread "+executionThreadId+" was the "+threadCompletions+"th thread to complete at "+executionEndTimeMs+", started at "+executionStartTimeMs);
            }
            previousInitiationTimeMs = initiationTimeMs;
            previousExecutionStartTimeMs = executionStartTimeMs;
            previousExecutionEndTimeMs = executionEndTimeMs;
            threadCompletions++;
        }
    }


    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

//        public Thread newThread(Runnable r) {
//            return new Thread(r, "AsyncTask #" + mCount.getAndIncrement());
//        }

        @Override
        public Thread newThread(final Runnable runnable_) {
            Runnable wrapperRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);
                    } catch (Throwable t) {

                    }
                    runnable_.run();
                }
            };
            return new Thread(wrapperRunnable);
        }
    };

    private static final BlockingQueue<Runnable> sPoolWorkQueue =
            new LinkedBlockingQueue<Runnable>(128);  // allow 128 in the queue


    ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
            8 , 16 , 30 , TimeUnit.SECONDS,    // CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
            sPoolWorkQueue, sThreadFactory);



    /*
    TODO - see - https://stackoverflow.com/questions/25647881/android-asynctask-example-and-explanation
     */
    /**
     * Saves a JPEG {@link Image} into the specified {@link File}.
     */
    private class ImageSaverAsyncTask extends AsyncTask<Void, Void, Long> { //parameter array type, progress type, return type
        byte[] luminanceBytes;
        int imageWidth;
        int imageHeight;

        long instantiationTimeMs = -1L;
        long executionThreadId = -1L;
        long executionStartTimeMs = -1L;
        long executionEndTimeMs = -1L;

        public ImageSaverAsyncTask(Image image, TaskCompletionTimer taskCompletionTimer_) {
            super();
            Log.i("ImageSaverAsyncTask","ImageSaverAsyncTask(Image image)");
            long startTime = Calendar.getInstance().getTimeInMillis();
            instantiationTimeMs = startTime;
            Log.i("ImageSaverAsyncTask","ImageSaverAsyncTask(Image image): start = "+startTime);
            ByteBuffer luminanceBuffer = /*mImage*/image.getPlanes()[0].getBuffer();
            Log.i("ImageSaverAsyncTask","ImageSaverAsyncTask(Image image): after image.getPlanes()[0].getBuffer() in "+timeElapsed(startTime)+"ms");
            /*byte[]*/ luminanceBytes = new byte[luminanceBuffer.remaining()];  // buffer size: current position is zero, remaining() gives "the number of elements between the current position and the limit"
            Log.i("ImageSaverAsyncTask","ImageSaverAsyncTask(Image image): after luminanceBytes = new byte[luminanceBuffer.remaining()] in "+timeElapsed(startTime)+"ms");
            luminanceBuffer.get(luminanceBytes);                            // copy from buffer to bytes: get() "transfers bytes from this buffer into the given destination array"
            imageWidth = image.getWidth();
            imageHeight = image.getHeight();
            Log.i("ImageSaverAsyncTask","ImageSaverAsyncTask(Image image): image dimensions: "+imageWidth+" pixels wide, "+imageHeight+" pixels high");
            taskCompletionTimer = taskCompletionTimer_;
            Log.i("ImageSaverAsyncTask","ImageSaverAsyncTask(Image image): end after "+timeElapsed(startTime)+"ms");
        }

        @Override
        protected Long doInBackground(Void... params) { //(Image...  images) {
            executionThreadId = Thread.currentThread().getId();
            String logTag = "ImgeSv_p="+executionThreadId;

                long startTime = Calendar.getInstance().getTimeInMillis();
                Log.i(logTag, "doInBackground(): start = " + startTime);
                executionStartTimeMs = startTime;
                taskCompletionTimer.incConcurrentThreadsExecuting();
                try {
                    Log.i(logTag, "doInBackground() : doing some work in the Try block: concurrentThreadsExecuting = "+taskCompletionTimer.concurrentThreadsExecuting());
                    Log.i(logTag, "doInBackground() : doing some work in the Try block: Runtime.getRuntime().availableProcessors() = "+Runtime.getRuntime().availableProcessors());

                    Random random = new Random();
                    if (1 == random.nextInt()) {
                        Log.i(logTag, "doInBackground() : throwing an IOException at random while doing some work in the Try block");
                        throw new IOException("No particular reason: ImageSaver.doInBackground() : throwing an IOException at random while doing some work in the Try block");
                    }
                    // dummy image processing code - https://boofcv.org/index.php?title=Android_support
                    long algorithmStepStartTime = 0L;
                    algorithmStepStartTime = Calendar.getInstance().getTimeInMillis();
                    GrayF32 grayImage = new GrayF32(imageWidth, imageHeight);
                    Log.i(logTag, "doInBackground() : after constructing grayImage in " + timeElapsed(algorithmStepStartTime) + "ms");
                    // from NV21 to gray scale
                    algorithmStepStartTime = Calendar.getInstance().getTimeInMillis();
                    ConvertNV21.nv21ToGray(luminanceBytes, imageWidth, imageHeight, grayImage);
                    Log.i(logTag, "doInBackground() : after converting nv21ToGray in " + timeElapsed(algorithmStepStartTime) + "ms");

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
                    Log.i(logTag, "doInBackground() : start detector.detect(grayImage);");
                    Log.i(logTag, "doInBackground() : start detector.detect(grayImage) at " + timeNow);
                    detector.detect(grayImage);
                    Log.i(logTag, "doInBackground() : after detector.detect(grayImage) in " + timeElapsed(timeNow) + "ms");
                    Log.i(logTag, "doInBackground() : after detector.detect(grayImage) : time since start = " + timeElapsed(startTime) + "ms");
                    Log.i(logTag, "doInBackground() : finished detector.detect(grayImage);");
                    for (int i = 0; i < detector.totalFound(); i++) {
                        timeNow = Calendar.getInstance().getTimeInMillis();
                        if( detector.hasUniqueID() ) {
                            long tag_id_long = detector.getId(i);
                            Log.i(logTag, "doInBackground() : tag detection "+i+" after detector.getId("+i+") = "+tag_id_long+" in " + timeElapsed(timeNow) + "ms");
                            Log.i(logTag, "doInBackground() : tag detection "+i+" after detector.getId("+i+") = "+tag_id_long+" in " + timeElapsed(startTime) + "ms from start");
                        } else {
                            Log.i(logTag, "doInBackground() : tag detection "+i+" has no id; detector.hasUniqueID() == false ");
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                }
            executionEndTimeMs = Calendar.getInstance().getTimeInMillis();
            return new Long(0L);
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
            taskCompletionTimer.completedTask(executionThreadId, instantiationTimeMs, executionStartTimeMs, executionEndTimeMs);
        }

    }

    private static long timeElapsed(long startTime) {
        long timeNow;
        long timeElapsed;
        timeNow = Calendar.getInstance().getTimeInMillis();
        timeElapsed = timeNow - startTime;
        return timeElapsed;
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    /**
     * Shows an error message dialog.
     */
    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }

    }

    /**
     * Shows OK/Cancel confirmation dialog about camera permission.
     */
    public static class ConfirmationDialog extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.request_permission)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            FragmentCompat.requestPermissions(parent,
                                    new String[]{Manifest.permission.CAMERA},
                                    REQUEST_CAMERA_PERMISSION);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Activity activity = parent.getActivity();
                                    if (activity != null) {
                                        activity.finish();
                                    }
                                }
                            })
                    .create();
        }
    }

}
