package com.example.android.camera2basic;

import android.util.Log;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by will on 24/04/18.
 */
class TaskCompletionTimer {

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

    int incConcurrentThreadsExecuting() {
        return concurrentThreadsExecuting.incrementAndGet();
    }

    int decConcurrentThreadsExecuting() {
        return concurrentThreadsExecuting.decrementAndGet();
    }

    int concurrentThreadsExecuting() {
        return concurrentThreadsExecuting.intValue();
    }

    void completedTask(long executionThreadId, long initiationTimeMs, long executionStartTimeMs, long executionEndTimeMs) {
        decConcurrentThreadsExecuting();
        if (threadCompletions > 1) {
            overlapWithLastInitiation = executionEndTimeMs - previousInitiationTimeMs;
            overlapWithLastExecution = executionEndTimeMs - previousInitiationTimeMs;
            completionAfterPrevious = executionEndTimeMs - previousExecutionEndTimeMs;
            executionPeriod = executionEndTimeMs - executionStartTimeMs;
            Log.i("completedTask", "thread completion " + completionAfterPrevious + "ms after previous: " + overlapWithLastInitiation + "ms overlap with last started: thread " + executionThreadId + " was the " + threadCompletions + "th thread to complete at " + executionEndTimeMs + ", started at " + executionStartTimeMs);
        }
        previousInitiationTimeMs = initiationTimeMs;
        previousExecutionStartTimeMs = executionStartTimeMs;
        previousExecutionEndTimeMs = executionEndTimeMs;
        threadCompletions++;
    }
}
