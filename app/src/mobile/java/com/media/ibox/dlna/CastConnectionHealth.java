package com.media.ibox.dlna;

public final class CastConnectionHealth {

    public enum Status {
        CONNECTED, LOST, RETRYING
    }

    private static final int FAILURE_THRESHOLD = 3;

    private Status status = Status.CONNECTED;
    private int failures;

    public synchronized Status status() {
        return status;
    }

    public synchronized void pollFailed() {
        if (status == Status.RETRYING) return;
        if (++failures >= FAILURE_THRESHOLD) status = Status.LOST;
    }

    public synchronized void pollSucceeded() {
        failures = 0;
        if (status != Status.RETRYING) status = Status.CONNECTED;
    }

    public synchronized boolean beginRetry() {
        if (status != Status.LOST) return false;
        status = Status.RETRYING;
        return true;
    }

    public synchronized void retrySucceeded() {
        reset();
    }

    public synchronized void retryFailed() {
        status = Status.LOST;
        failures = FAILURE_THRESHOLD;
    }

    public synchronized void reset() {
        status = Status.CONNECTED;
        failures = 0;
    }
}
