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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import boofcv.struct.image.GrayF32;

import static com.example.android.camera2basic.OpenCVStub.demoOpenCV;


public class Camera2BasicFragment extends Fragment
        implements View.OnClickListener, FragmentCompat.OnRequestPermissionsResultCallback {

    // This snippet hides the system bars.
    private void hideSystemUI() {
        // Set the IMMERSIVE flag.
        // Set the content to appear under the system bars so that the content
        // doesn't resize when the system bars hide and show.
        getView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                        | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                        | View.SYSTEM_UI_FLAG_IMMERSIVE);
    }

    // This snippet shows the system bars. It does this by removing all the flags
// except for the ones that make the content appear under the system bars.
    private void showSystemUI() {
        getView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }


    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final String FRAGMENT_DIALOG = "dialog";
    static final int targetFPS = 6;
    static final int maxConcurrentThreads = 8;
    static final int numRecordsToUse = 10;
    static int fps = 5;
    static int fps10 = 10;
    static int fps20 = 20;
    static int fpsUpper = fps10;
    public static  Range<Integer> fpsRange = new Range<>(fps, fpsUpper);

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
    private Size imageReaderSize;


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
     * Do the image processing - this a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener onImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {
        final int RUN_ASYNC_BACKGROUNDHANDLER =  10;
        final int RUN_ASYNC_ASYNCTASK =         20;
        final int RUN_ASYNC_METHOD =            RUN_ASYNC_ASYNCTASK; //RUN_ASYNC_BACKGROUNDHANDLER; //RUN_ASYNC_ASYNCTASK;

        @Override
        public void onImageAvailable(ImageReader reader) {
            //Image image =  reader.acquireNextImage();       // TODO see https://stackoverflow.com/a/43564630/1200764
            Image image =  reader.acquireLatestImage();       // TODO see https://stackoverflow.com/a/43564630/1200764
            if (null == image) {
                Log.w("onImageAvailable","null == image : frameNum="+frameNum);
                return;
            }
            System.out.println("onImageAvailable: ImageReader image size= "+image.getWidth()+"x"+image.getHeight());

                System.out.println("onImageAvailable: before OpenCVStub.demoOpenCV");
                demoOpenCV();
                System.out.println("onImageAvailable: after OpenCVStub.demoOpenCV");

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
                    if (skipFrame(image)) break;
                    try {
                        //  new ImageSaverAsyncTask(image, taskCompletionTimer).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);  // https://stackoverflow.com/questions/18266161/achieving-concurrency-using-asynctask  https://developer.android.com/reference/java/util/concurrent/Executor.html
                        new ImageSaverAsyncTask(Camera2BasicFragment.this, image/*, taskCompletionTimer*/).executeOnExecutor(threadPoolExecutor);  // https://stackoverflow.com/questions/18266161/achieving-concurrency-using-asynctask  https://developer.android.com/reference/java/util/concurrent/Executor.html
                        if (image != null) {             // ?? causes an exception because !when the app starts! closed before can save                // TODO see https://stackoverflow.com/a/43564630/1200764
                            image.close();               // ?? causes an exception because !when the app starts! closed before can save                // TODO see https://stackoverflow.com/a/43564630/1200764
                        }
                    } catch (java.util.concurrent.RejectedExecutionException ree) {
                        Log.w("Camera2BasicFragment","onImageAvailable(ImageReader reader): hit the limit of available threads with a java.util.concurrent.RejectedExecutionException");
                        skipRateOnException();
                        if (image != null) {             // ?? causes an exception because !when the app starts! closed before can save                // TODO see https://stackoverflow.com/a/43564630/1200764
                            image.close();               // ?? causes an exception because !when the app starts! closed before can save                // TODO see https://stackoverflow.com/a/43564630/1200764
                        }
                    }
                    break;
                }
                default: {

                }
                try {
                    if (image != null) {             // ?? causes an exception because !when the app starts! closed before can save                // TODO see https://stackoverflow.com/a/43564630/1200764
                        image.close();               // ?? causes an exception because !when the app starts! closed before can save                // TODO see https://stackoverflow.com/a/43564630/1200764
                    } } catch(Exception e) {
                    Log.e("Camera2BasicFragment","error closing image in final try-catch block.");
                }
            }
        }
    };

    private void skipRateOnException() {
        if(skipRate <= 10){ skipRate--; skipRateReducedOnFrameNum = frameNum; }
        if(skipRate > 10){ skipRate = 10; }
        if(skipRate < 2){ skipRate = 2; }
    }

    private boolean skipFrame(Image image) {
        if (skipRate <= 10 && skipRate>0 && frameNum%skipRate == 0 ) { // skip this image - e.g. if skipRate == 8 and frameNum == 16
            Log.i("Camera2BasicFragment","skipping frame "+frameNum+" on skipRate "+skipRate);
            if (image != null) {             // ?? causes an exception because !when the app starts! closed before can save                // TODO see https://stackoverflow.com/a/43564630/1200764
                image.close();               // ?? causes an exception because !when the app starts! closed before can save                // TODO see https://stackoverflow.com/a/43564630/1200764
            }
            return true;
        }
        int TARGET_FRAME_RATE = 10;
        if ( skipRateReducedOnFrameNum < frameNum - 100 && skipRate > 2 && taskCompletionTimer.overlapWithLastInitiation() > (5000/TARGET_FRAME_RATE)) { // more than 1s overlap; going wrong
            skipRate--; if(skipRate < 2){ skipRate = 2; }
            skipRateReducedOnFrameNum = frameNum;
            Log.i("Camera2BasicFragment","at frame "+frameNum+" reduced skipRate to "+skipRate+" on taskCompletionTimer.overlapWithLastInitiation() = "+taskCompletionTimer.overlapWithLastInitiation()+" > (5000/"+TARGET_FRAME_RATE+")");
        }
        return false;
    }

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
                            runPrecaptureSequenceAndCaptureImage();
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
     * @param softMinWidth  The width of the texture view relative to sensor coordinate
     * @param softMinHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSizeForImage(Size[] choices, int softMinWidth,
                                                  int softMinHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            System.out.println("chooseOptimalSizeForImage: image size option = "+option.getWidth()+"x"+option.getHeight());
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= softMinWidth &&
                    option.getHeight() >= softMinHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            // return Collections.min(bigEnough, new CompareSizesByArea());
            Size size = Collections.min(bigEnough, new CompareSizesByArea());
            System.out.println("chooseOptimalSizeForImage: returning size="+size);
            return size;
        } else if (notBigEnough.size() > 0) {
            // return Collections.max(notBigEnough, new CompareSizesByArea());
            Size size = Collections.max(notBigEnough, new CompareSizesByArea());
            System.out.println("chooseOptimalSizeForImage: returning size="+size);
            return size;
        } else {
            Log.e(TAG, "chooseOptimalSizeForImage: Couldn't find any suitable preview size: returning size="+choices[0].toString());
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
        hookUpGuiButtons(view);
        mTextureView = (AutoFitTextureView) view.findViewById(R.id.texture);
        System.out.println("onViewCreated: mTextureView size = "+mTextureView.getWidth()+"x"+mTextureView.getHeight());
    }

    private void hookUpGuiButtons(View view) {
        view.findViewById(R.id.picture).setOnClickListener(this);
        view.findViewById(R.id.fps).setOnClickListener(this);
        view.findViewById(R.id.info).setOnClickListener(this);
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
            System.out.println("onResume(): mTextureView size = "+mTextureView.getWidth()+"x"+mTextureView.getHeight());
//            hideSystemUI();
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
        if (FragmentCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {        // be fancy to explain why the user should give the permission if it's not obvious from the application type "whether you should show UI with rationale for requesting a permission"
            new ConfirmationDialogFragment().show(getChildFragmentManager(), FRAGMENT_DIALOG);              // show the dialog - ConfirmationDialogFragment.onCreateDialog gets called somewhere along the way
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
                Size largest_camera_image_for_format = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888 /*ImageFormat.JPEG*/ )),         // TODO see https://stackoverflow.com/a/43564630/1200764
                        new CompareSizesByArea());



                // Find out if we need to swap dimension to get the preview size relative to sensor  coordinate.
                int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                //noinspection ConstantConditions
                mSensorOrientation = cameraDetails.get(CameraCharacteristics.SENSOR_ORIENTATION);

                Point displaySize = new Point();
                CalcPreviewSize calcPreviewSize = new CalcPreviewSize(width, height, activity, displayRotation, displaySize).invoke();
                int rotatedPreviewWidth = calcPreviewSize.getRotatedPreviewWidth();
                int rotatedPreviewHeight = calcPreviewSize.getRotatedPreviewHeight();
                int maxPreviewWidth = calcPreviewSize.getMaxPreviewWidth();
                int maxPreviewHeight = calcPreviewSize.getMaxPreviewHeight();

//                Size[] imageReaderSizes = new Size[]{
//                        new Size(4160,3120),  // Nexus5 landscape 4:3
//                        new Size(1920,1440)}; // Nexus5 portrait  4:3
                /*
                Landscape
                01-10 16:54:03.214 8297-8297/com.example.android.camera2basic I/System.out: chooseOptimalSizeForImage: image size option = 2592x1728
                01-10 16:54:03.214 8297-8297/com.example.android.camera2basic I/System.out: chooseOptimalSizeForImage: image size option = 2048x1536
                01-10 16:54:03.214 8297-8297/com.example.android.camera2basic I/System.out: chooseOptimalSizeForImage: image size option = 1920x1440
                01-10 16:54:03.214 8297-8297/com.example.android.camera2basic I/System.out: chooseOptimalSizeForImage: image size option = 1920x1080
                01-10 16:54:03.214 8297-8297/com.example.android.camera2basic I/System.out: chooseOptimalSizeForImage: image size option = 1280x960
                01-10 16:54:03.214 8297-8297/com.example.android.camera2basic I/System.out: chooseOptimalSizeForImage: image size option = 1280x768
                01-10 16:54:03.214 8297-8297/com.example.android.camera2basic I/System.out: chooseOptimalSizeForImage: image size option = 1280x720
                01-10 16:54:03.214 8297-8297/com.example.android.camera2basic I/System.out: chooseOptimalSizeForImage: image size option = 1024x768
                01-10 16:54:03.214 8297-8297/com.example.android.camera2basic I/System.out: chooseOptimalSizeForImage: image size option = 800x600
                01-10 16:54:03.214 8297-8297/com.example.android.camera2basic I/System.out: chooseOptimalSizeForImage: image size option = 800x480
                01-10 16:54:03.214 8297-8297/com.example.android.camera2basic I/System.out: chooseOptimalSizeForImage: image size option = 720x480
                01-10 16:54:03.214 8297-8297/com.example.android.camera2basic I/System.out: chooseOptimalSizeForImage: image size option = 640x480
                01-10 16:54:03.214 8297-8297/com.example.android.camera2basic I/System.out: chooseOptimalSizeForImage: image size option = 352x288
                01-10 16:54:03.214 8297-8297/com.example.android.camera2basic I/System.out: chooseOptimalSizeForImage: image size option = 320x240
                01-10 16:54:03.214 8297-8297/com.example.android.camera2basic I/System.out: chooseOptimalSizeForImage: image size option = 176x144

                Portrait
                01-10 16:55:05.461 8501-8501/? I/System.out: chooseOptimalSizeForImage: image size option = 4160x3120
                01-10 16:55:05.461 8501-8501/? I/System.out: chooseOptimalSizeForImage: image size option = 4160x2774
                01-10 16:55:05.461 8501-8501/? I/System.out: chooseOptimalSizeForImage: image size option = 4160x2340
                01-10 16:55:05.461 8501-8501/? I/System.out: chooseOptimalSizeForImage: image size option = 4000x3000
                01-10 16:55:05.461 8501-8501/? I/System.out: chooseOptimalSizeForImage: image size option = 3840x2160
                01-10 16:55:05.461 8501-8501/? I/System.out: chooseOptimalSizeForImage: image size option = 3264x2176
                01-10 16:55:05.461 8501-8501/? I/System.out: chooseOptimalSizeForImage: image size option = 3200x2400
                01-10 16:55:05.461 8501-8501/? I/System.out: chooseOptimalSizeForImage: image size option = 3200x1800
                01-10 16:55:05.461 8501-8501/? I/System.out: chooseOptimalSizeForImage: image size option = 2592x1944
                01-10 16:55:05.461 8501-8501/? I/System.out: chooseOptimalSizeForImage: image size option = 2592x1728
                01-10 16:55:05.461 8501-8501/? I/System.out: chooseOptimalSizeForImage: image size option = 2048x1536
                01-10 16:55:05.461 8501-8501/? I/System.out: chooseOptimalSizeForImage: image size option = 1920x1440
                01-10 16:55:05.461 8501-8501/? I/System.out: chooseOptimalSizeForImage: image size option = 1920x1080
                01-10 16:55:05.461 8501-8501/? I/System.out: chooseOptimalSizeForImage: image size option = 1280x960
                01-10 16:55:05.461 8501-8501/? I/System.out: chooseOptimalSizeForImage: image size option = 1280x768
                01-10 16:55:05.462 8501-8501/? I/System.out: chooseOptimalSizeForImage: image size option = 1280x720
                01-10 16:55:05.462 8501-8501/? I/System.out: chooseOptimalSizeForImage: image size option = 1024x768
                01-10 16:55:05.462 8501-8501/? I/System.out: chooseOptimalSizeForImage: image size option = 800x600
                01-10 16:55:05.462 8501-8501/? I/System.out: chooseOptimalSizeForImage: image size option = 800x480
                01-10 16:55:05.462 8501-8501/? I/System.out: chooseOptimalSizeForImage: image size option = 720x480
                01-10 16:55:05.462 8501-8501/? I/System.out: chooseOptimalSizeForImage: image size option = 640x480
                01-10 16:55:05.462 8501-8501/? I/System.out: chooseOptimalSizeForImage: image size option = 352x288
                01-10 16:55:05.462 8501-8501/? I/System.out: chooseOptimalSizeForImage: image size option = 320x240
                01-10 16:55:05.462 8501-8501/? I/System.out: chooseOptimalSizeForImage: image size option = 176x144
                */

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                imageSize = chooseOptimalSizeForImage(map.getOutputSizes(SurfaceTexture.class),  // TODO - match the mImageReader = ImageReader.newInstance() to this preview size
                        rotatedPreviewWidth, rotatedPreviewHeight, 
                        maxPreviewWidth, maxPreviewHeight, largest_camera_image_for_format);

                Size[] imageReaderSizes = new Size[]{
                        //  new Size(4160,3120),  // Nexus5 landscape 4:3 maximum resolution
                        //  new Size(2592,1944), //Nexus5 landscape 4:3
                        new Size(1920,1440), //Nexus5 landscape 4:3
                        new Size(1280, 960), //Nexus5 landscape 4:3
                        new Size(1024, 768), //Nexus5 landscape 4:3
                        new Size( 320, 240)  //Nexus5 landscape 4:3  -  bottom-end failsafe
                }; // Nexus5 portrait  4:3
//                int maxSizeWidth = 1920;  int maxSizeHeight = 1440;
//                int maxSizeWidth = 1280;  int maxSizeHeight = 960;
                int maxSizeWidth = 1024;  int maxSizeHeight = 768;
                imageReaderSize = chooseOptimalSizeForImage( imageReaderSizes,
                        rotatedPreviewWidth, rotatedPreviewHeight,
                        maxSizeWidth, maxSizeHeight,
                        largest_camera_image_for_format);
                    Log.i(TAG, "setUpCameraOutputs(int "+width+", int "+height+"): imageSize= "+imageSize.getWidth()+"x"+imageSize.getHeight());
                    /* TODO - "the ImageReader class allows direct application access to image data rendered into a {@link android.view.Surface"*/
                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mTextureView.setAspectRatio(imageSize.getWidth(), imageSize.getHeight());           // Fit the aspect ratio of TextureView to the size of preview.
       //             mImageReader = ImageReader.newInstance( imageSize.getWidth(), imageSize.getHeight(), ImageFormat.YUV_420_888, 2 /*maxImages*/ ); // TODO see https://stackoverflow.com/a/43564630/1200764 - this scaling is arbitrary; reduces the max width from 4160 to 240, so always get 320x240
                    // mImageReader = ImageReader.newInstance( largest_camera_image_for_format.getWidth(), largest_camera_image_for_format.getHeight(), ImageFormat.YUV_420_888, 2 /*maxImages*/ ); // TODO see https://stackoverflow.com/a/43564630/1200764 - this scaling is arbitrary; reduces the max width from 4160 to 240, so always get 320x240
                    mImageReader = ImageReader.newInstance( imageReaderSize.getWidth(), imageReaderSize.getHeight() , ImageFormat.YUV_420_888, 2 /*maxImages*/ ); // TODO see https://stackoverflow.com/a/43564630/1200764 - this scaling is arbitrary; reduces the max width from 4160 to 240, so always get 320x240
                } else {
                    mTextureView.setAspectRatio(imageSize.getHeight(), imageSize.getWidth());
       //             mImageReader = ImageReader.newInstance( imageSize.getHeight(), imageSize.getWidth(), ImageFormat.YUV_420_888, 2 /*maxImages*/ ); // TODO see https://stackoverflow.com/a/43564630/1200764 - this scaling is arbitrary; reduces the max width from 4160 to 240, so always get 320x240
                    //mImageReader = ImageReader.newInstance( largest_camera_image_for_format.getHeight(), largest_camera_image_for_format.getWidth(), ImageFormat.YUV_420_888, 2 /*maxImages*/ ); // TODO see https://stackoverflow.com/a/43564630/1200764 - this scaling is arbitrary; reduces the max width from 4160 to 240, so always get 320x240
                    mImageReader = ImageReader.newInstance( imageReaderSize.getHeight(), imageReaderSize.getWidth(), ImageFormat.YUV_420_888, 2 /*maxImages*/ ); // TODO see https://stackoverflow.com/a/43564630/1200764 - this scaling is arbitrary; reduces the max width from 4160 to 240, so always get 320x240
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
            Log.i(TAG,"logCameraCharacteristics: start");
        String[] cameras = manager.getCameraIdList();
        for(String camera : cameras) {
            CameraCharacteristics cc = manager.getCameraCharacteristics(camera);
            CameraCharacteristics.Key<int[]> aa = cc.REQUEST_AVAILABLE_CAPABILITIES;
            for (int i = 0; i < cc.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES).length; i++) {
                Log.i(TAG, "logCameraCharacteristics: camera="+camera+" available capability id= " + cc.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)[i]);
            }
        }
        for(String camera : cameras) {
            CameraCharacteristics cc = manager.getCameraCharacteristics(camera);
            Range<Integer>[] fpsRange = cc.get(cc.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                Log.i(TAG, "logCameraCharacteristics: camera="+camera+" CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES fpsRange= [" + fpsRange[0] + "," + fpsRange[1] + "]");
            StreamConfigurationMap map = manager.getCameraCharacteristics(camera).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if(null != map) {
                fpsRange = map.getHighSpeedVideoFpsRanges(); // this range intends available fps range of device's camera.
                Log.i(TAG, "logCameraCharacteristics: camera=" + camera + " StreamConfigurationMap getHighSpeedVideoFpsRanges fpsRange=" + arrayOfRangesToString(fpsRange) );
                try {   Log.i(TAG, "logCameraCharacteristics: camera="+camera+" StreamConfigurationMap getHighSpeedVideoFpsRanges fpsRange= [" + fpsRange[0] + "," + fpsRange[1] + "]"); }
                catch(Exception e) {Log.i(TAG, "logCameraCharacteristics: camera="+camera+" StreamConfigurationMap getHighSpeedVideoFpsRanges : exception getting fpsRange="+e);}
            }
        }
            Log.i(TAG,"logCameraCharacteristics: end");
    }

    String arrayOfRangesToString(Range[] arrayOfRanges_) {
        String returnString = null;
        for (Range range : arrayOfRanges_) {
            returnString = (null == returnString ? range.toString() : returnString+range.toString() );
        }
        return (null == returnString ? "[No ranges in array]" : returnString );
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
            SurfaceTexture previewTexture = mTextureView.getSurfaceTexture();
            assert previewTexture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            previewTexture.setDefaultBufferSize(imageSize.getWidth(), imageSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface previewDisplaySurface = new Surface(previewTexture);

            // We set up a CaptureRequest.Builder with the output Surface.
            Log.i("Camera2BasicFragment","createCameraPreviewSession(): for mCameraDevice.getId()="+mCameraDevice.getId());
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(previewDisplaySurface);
            mPreviewRequestBuilder.addTarget(mImageReader.getSurface());            // TODO see https://stackoverflow.com/a/43564630/1200764

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(previewDisplaySurface, mImageReader.getSurface()),
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
                                mPreviewRequestBuilder.set( // Auto focus should be continuous for camera preview.
                                        CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                                setAutoexposureToTargetRangeOfFPS(fpsRange);
                                setAutoFlash(mPreviewRequestBuilder); // Flash is automatically enabled when necessary.
                                mPreviewRequest = mPreviewRequestBuilder.build(); // Finally, we start displaying the camera preview.
                                mCaptureSession.setRepeatingRequest(
                                        mPreviewRequest,
                                        mCaptureCallback,
                                        backgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            showToast("Cofiguration process failed");   // output to screen
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
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);  // Set the matrix to the scale and translate values that map the source rectangle to the destination rectangle
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


    private void adjustFps() {
        if (dontHaveEnoughDataYet()) {
            Log.i("Camera2BasicFragment","adjustFps(): numRecordsToUse < countOfFpsAndThreads.size(): "+numRecordsToUse+" < "+countOfFpsAndThreads.size());
            return;
        }
        FPS.Change change = calculateFPSChangeAndClear();
        switch (change) {
            case DECREASE:
                fps--;
                fpsRange = new Range<Integer>(fps,fpsUpper);
                Log.i("Camera2BasicFragment","adjustFps(): DECREASE: now "+fpsRange);
                unlockFocusAndReturnToPreview();
                Log.i("Camera2BasicFragment","adjustFps(): DECREASE: after unlockFocusAndReturnToPreview.");
                break;
            case INCREASE:
                fps++;
                fpsRange = new Range<Integer>(fps,fpsUpper);
                Log.i("Camera2BasicFragment","adjustFps(): INCREASE: now "+fpsRange);
                unlockFocusAndReturnToPreview();
                Log.i("Camera2BasicFragment","adjustFps(): INCREASE: after unlockFocusAndReturnToPreview.");
                break;
            case NO_CHANGE:
                Log.i("Camera2BasicFragment","adjustFps(): NO_CHANGE: still "+fpsRange);
                break;
            default:
                Log.i("Camera2BasicFragment","adjustFps(): don't know what this value is : "+change.name());
                break;
        }
    }

    private FPS.Change calculateFPSChangeAndClear() {
        int[] fps_ = new int[countOfFpsAndThreads.size()];
        int[] concurrentThreads = new int[countOfFpsAndThreads.size()];
        int i_ = 0;
        for ( PerformanceMetric perf : countOfFpsAndThreads.values()) {
            fps_[i_] = perf.fps();
            concurrentThreads[i_] = perf.concurrentThreads();
            i_++;
        }
        countOfFpsAndThreads.clear();
        return FPS.calc(fps_,concurrentThreads,numRecordsToUse,targetFPS,maxConcurrentThreads);
    }

    private boolean dontHaveEnoughDataYet() {
        return numRecordsToUse > countOfFpsAndThreads.size();
    }

    /**
     * Initiate a still image capture.
     */
    private void takePicture() {
        Log.i("Camera2BasicFragment","takePicture()");
        lockFocusAndCaptureImage();
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocusAndCaptureImage() {
        try {
            // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder.set(
                    CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_FOR_FOCUS_LOCK;
            mCaptureSession.capture(
                    mPreviewRequestBuilder.build(),
                    mCaptureCallback,
                    backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #lockFocusAndCaptureImage()}.
     */
    private void runPrecaptureSequenceAndCaptureImage() {
        Log.i("Camera2BasicFragment","runPrecaptureSequenceAndCaptureImage()");
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder.set(
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_FOR_EXPOSURE_TO_BE_PRECAPTURE;
            mCaptureSession.capture(
                    mPreviewRequestBuilder.build(),
                    mCaptureCallback,
                    backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCaptureCallback} from both {@link #lockFocusAndCaptureImage()}.
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
            captureBuilder.set(
                    CaptureRequest.CONTROL_AF_MODE,
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
                    unlockFocusAndReturnToPreview();
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
    private void unlockFocusAndReturnToPreview() {
        try {
            // Reset the auto-focus trigger
            cancelAnyCurrentlyActiveAutofocusTrigger();
            setAutoexposureToTargetRangeOfFPS(fpsRange);

            setAutoFlash(mPreviewRequestBuilder);
            mCaptureSession.capture(
                    mPreviewRequestBuilder.build(),
                    mCaptureCallback,
                    backgroundHandler);

            // Return the camera to the 'normal' state of preview.
            mState = STATE_SHOWING_CAMERA_PREVIEW;
            mCaptureSession.setRepeatingRequest(
                    mPreviewRequest,
                    mCaptureCallback,
                    backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setAutoexposureToTargetRangeOfFPS(Range<Integer> rangeOfFPS) {
        Log.i("Camera2BasicFragment","setAutoexposureToTargetRangeOfFPS("+rangeOfFPS+")");
        mPreviewRequestBuilder.set( // Auto focus should be continuous for camera preview.
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                rangeOfFPS);
    }

    private void cancelAnyCurrentlyActiveAutofocusTrigger() {
        mPreviewRequestBuilder.set(
                CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
    }

    @Override
    public void onClick(View view) {
        Log.i("Camera2BasicFragment","onClick(View view)");
        switch (view.getId()) {
            case R.id.picture: {
                takePicture();
                break;
            }
            case R.id.fps: {
                adjustFps();
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


    Map<String, PerformanceMetric>countOfFpsAndThreads = Collections.synchronizedMap(new LinkedHashMap<String, PerformanceMetric>()  // convenient for the fixed-size
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, PerformanceMetric> eldest) {
            return this.size() > 50;
        }
    });


    Map<String, PerformanceMetric>logOfFpsAndThreads = Collections.synchronizedMap(new LinkedHashMap<String, PerformanceMetric>()  // convenient for the fixed-size
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, PerformanceMetric> eldest) {
            return this.size() > 200;
        }
    });

    ConcurrentLinkedQueue<GrayF32> unusedGrayImageQueue = new ConcurrentLinkedQueue<GrayF32>();   //  todo - persist in the application across onSleep/onResume for re-orientations

    void finishedUsingGrayImage(GrayF32 image_) {
        Log.i("finishedUsingGrayImage","finishedUsingGrayImage");
        if(null!=unusedGrayImageQueue && null!=image_) {
            try {
                unusedGrayImageQueue.add(image_);
            } catch (Exception e) {
                Log.e(TAG, "Exception in unusedGrayImageQueue.add(image_): CONTINUING after this exception: ",e);
                e.printStackTrace();
            }
        }
    }
    GrayF32 fetchAGrayImageToUse(int imageWidth_, int imageHeight_) {
        GrayF32 unusedImage;
        while(true) {
            try {
                unusedImage = unusedGrayImageQueue.remove();
            } catch (NoSuchElementException e) {                    // queue is empty, so make a new image
                Log.i("fetchAGrayImageToUse","queue is empty, so make a new image");
                return new GrayF32(imageWidth_, imageHeight_);
            }
            if(unusedImage.getWidth() == imageWidth_ && unusedImage.getHeight() == imageHeight_) {
                Log.i("fetchAGrayImageToUse","image is the right size");
                return unusedImage;
            } else if(unusedImage.getWidth() < imageWidth_ || unusedImage.getHeight() < imageHeight_) {     // too small: discard and continue
                Log.i("fetchAGrayImageToUse","image is too small: discard and continue");
                unusedImage = null;                                 // discard: setting to null to make intention plain.
            } else if(unusedImage.getWidth() >= imageWidth_ || unusedImage.getHeight() >= imageHeight_) {   // too large: can resize
                Log.i("fetchAGrayImageToUse","image is too large: can resize");
                unusedImage.reshape(imageWidth_,imageHeight_);      // resize "without declaring new memory."
                return unusedImage;
            }
        }
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

    /***********************************************************************************************/
    /**
     * Shows OK/Cancel confirmation dialog about camera permission.
     */
    public static class ConfirmationDialogFragment extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();        // ... get the parent fragment of this fragment ...
            return new AlertDialog.Builder(getActivity())       // ... go to Activity, create an AlertDialog builder ...
                    .setMessage(R.string.request_permission)    // ... set message to display in the alert ...
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() { // ... set the 'OK'/'Agree'/'Yes' button callback method
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            FragmentCompat.requestPermissions(parent,                               // ... to request permissions ... ????
                                    new String[]{Manifest.permission.CAMERA},
                                    REQUEST_CAMERA_PERMISSION);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,                                     // ... set the 'Cancel'/'No' button callback method ...
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Activity activity = parent.getActivity();
                                    if (activity != null) {
                                        activity.finish();                                          // ... to quit
                                    }
                                }
                            })
                    .create();                                                                      // ... creates but does not show
        }
    }


    private class CalcPreviewSize {
        private int width;
        private int height;
        private Activity activity;
        private int displayRotation;
        private Point displaySize;
        private int rotatedPreviewWidth;
        private int rotatedPreviewHeight;
        private int maxPreviewWidth;
        private int maxPreviewHeight;

        public CalcPreviewSize(int width, int height, Activity activity, int displayRotation, Point displaySize) {
            this.width = width;
            this.height = height;
            this.activity = activity;
            this.displayRotation = displayRotation;
            this.displaySize = displaySize;
        }

        public int getRotatedPreviewWidth() {
            return rotatedPreviewWidth;
        }

        public int getRotatedPreviewHeight() {
            return rotatedPreviewHeight;
        }

        public int getMaxPreviewWidth() {
            return maxPreviewWidth;
        }

        public int getMaxPreviewHeight() {
            return maxPreviewHeight;
        }

        public CalcPreviewSize invoke() {
            activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
            rotatedPreviewWidth = width;
            rotatedPreviewHeight = height;
            maxPreviewWidth = displaySize.x;
            maxPreviewHeight = displaySize.y;
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
            return this;
        }
    }
}
