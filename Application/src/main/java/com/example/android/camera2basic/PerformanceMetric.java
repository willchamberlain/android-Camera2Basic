package com.example.android.camera2basic;

/**
 * Created by will on 24/04/18.
 */
class PerformanceMetric {
    private int fps, concurrentThreads;

    PerformanceMetric(int fps_, int concurrentThreads_) {
        fps = fps_;
        concurrentThreads = concurrentThreads_;
    }

    public int fps() {
        return fps;
    }

    public int concurrentThreads() {
        return concurrentThreads;
    }
}