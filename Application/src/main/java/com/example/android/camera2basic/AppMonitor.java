package com.example.android.camera2basic;

import java.util.Calendar;

/**
 * Created by will on 24/04/18.
 */

public class AppMonitor {
    static long timeElapsed(long startTime) {
        long timeNow;
        long timeElapsed;
        timeNow = Calendar.getInstance().getTimeInMillis();
        timeElapsed = timeNow - startTime;
        return timeElapsed;
    }
}

