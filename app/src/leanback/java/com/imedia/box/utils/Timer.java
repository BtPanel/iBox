package com.imedia.box.utils;

import android.content.Intent;
import android.os.CountDownTimer;

import com.imedia.box.App;
import com.imedia.box.event.ActionEvent;
import com.imedia.box.service.PlaybackService;

import java.util.concurrent.TimeUnit;

public class Timer {

    private CountDownTimer timer;
    private Callback callback;
    private long tick;

    private static class Loader {
        static final Timer INSTANCE = new Timer();
    }

    public static Timer get() {
        return Loader.INSTANCE;
    }

    public boolean isRunning() {
        return timer != null;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public long getTick() {
        return tick;
    }

    public void set(long duration) {
        cancel();
        timer = new CountDownTimer(duration, 1000) {
            @Override
            public void onTick(long remaining) {
                tick = remaining;
                if (callback != null) callback.onTick(remaining);
            }

            @Override
            public void onFinish() {
                if (callback != null) callback.onFinish();
                if (PlaybackService.isRunning()) sendPause();
                reset();
            }
        }.start();
    }

    private void sendPause() {
        Intent intent = new Intent(App.get(), PlaybackService.class);
        intent.setAction(ActionEvent.PAUSE);
        App.get().startService(intent);
    }

    public void delay() {
        long remaining = tick;
        cancel();
        set(TimeUnit.MINUTES.toMillis(5) + remaining);
    }

    public void reset() {
        tick = 0;
        cancel();
    }

    private void cancel() {
        if (timer != null) timer.cancel();
        timer = null;
    }

    public interface Callback {
        void onTick(long tick);
        void onFinish();
    }
}
