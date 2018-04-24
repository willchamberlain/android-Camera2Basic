package com.example.android.camera2basic;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.CvType;
import org.opencv.core.Scalar;

/**
 * Created by will on 24/04/18.
 */


public class OpenCVStub {

//    static{ System.loadLibrary(Core.NATIVE_LIBRARY_NAME); }

    static{ OpenCVLoader.initDebug(); }

    public static void demoOpenCV() {
        System.out.println("OpenCVStub: Welcome to OpenCV " + Core.VERSION);
        Mat m = new Mat(5, 10, CvType.CV_8UC1, new Scalar(0));
        System.out.println("OpenCVStub: OpenCV Mat: " + m);
        Mat mr1 = m.row(1);
        mr1.setTo(new Scalar(1));
        Mat mc5 = m.col(5);
        mc5.setTo(new Scalar(5));
        System.out.println("OpenCVStub: OpenCV Mat data:\n" + m.dump());
    }

}
