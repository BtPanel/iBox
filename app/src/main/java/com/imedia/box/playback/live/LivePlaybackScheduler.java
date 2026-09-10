package com.imedia.box.playback.live;

import com.imedia.box.App;

public interface LivePlaybackScheduler {
    void post(Runnable runnable, long delayMs);
    void remove(Runnable runnable);

    static LivePlaybackScheduler app() {
        return new LivePlaybackScheduler() {
            @Override public void post(Runnable runnable, long delayMs) { App.post(runnable, delayMs); }
            @Override public void remove(Runnable runnable) { App.removeCallbacks(runnable); }
        };
    }
}
