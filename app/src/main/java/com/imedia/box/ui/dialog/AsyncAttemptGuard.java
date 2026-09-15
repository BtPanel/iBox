package com.imedia.box.ui.dialog;

final class AsyncAttemptGuard {

    private long generation;
    private boolean attached;

    synchronized void attach() {
        attached = true;
        generation++;
    }

    synchronized long next() {
        return ++generation;
    }

    synchronized void detach() {
        attached = false;
        generation++;
    }

    synchronized boolean isCurrent(long attempt) {
        return attached && generation == attempt;
    }
}
